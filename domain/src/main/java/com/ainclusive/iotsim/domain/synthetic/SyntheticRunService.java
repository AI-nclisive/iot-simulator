package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.protocolmodel.DeterminismContext;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Run-synthetic (IS-065, Model A): generates a bounded, deterministic batch from a
 * source's stored {@link SyntheticConfig} and applies it via the runtime in one shot,
 * opening a {@code SYNTHETIC} run + evidence. The generated twin of
 * {@code ReplayService.replay}; consistent with the system's no client-timing
 * guarantee (continuous live pacing is the deferred IS-119 / IS-069).
 */
@Service
public class SyntheticRunService {

    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public SyntheticRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence, ObjectMapper json) {
        this(dataSources, schemas, runtime, runs, evidence, json, Clock.systemUTC());
    }

    /** Test seam: pin the run-start clock so the produced series is fully reproducible. */
    SyntheticRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence,
            ObjectMapper json, Clock clock) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.runtime = runtime;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
        this.clock = clock;
    }

    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0: " + durationMs);
        }
        DataSourceRow source = requireSource(projectId, dataSourceId);
        if (!"SYNTHETIC".equals(source.basis())) {
            throw new IllegalArgumentException("data source is not synthetic: " + dataSourceId);
        }
        SyntheticConfig config = parseConfig(source.runtimeConfig());
        List<SyntheticVariable> variables = SyntheticConfigMapper.toVariables(config);

        DeterministicSettings settings = config.seed() == null
                ? DeterministicSettings.withRandomSeed(clock.instant())
                : new DeterministicSettings(config.seed(), clock.instant());

        RunRow run = runs.create(projectId, "SYNTHETIC", "MANUAL", "local", List.of(dataSourceId), null);
        try {
            runs.start(run.id(), now());
            EvidenceRow evidenceRow = evidence.create(projectId, run.id(), "local");
            runs.linkEvidence(run.id(), evidenceRow.id());

            List<NeutralValue> values = generate(variables, settings, durationMs);
            evidence.updateManifest(evidenceRow.id(), manifest(settings.seed(), values.size()));

            runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source));
            long applied = runtime.applyValues(dataSourceId, values);
            runs.end(run.id(), "COMPLETED", now());
            return new SyntheticRunSummary(dataSourceId, applied, settings.seed(), run.id(), evidenceRow.id());
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /**
     * Generates {@code durationMs / updateRateMs} samples per variable from one shared
     * run context (per-node RNG streams keep variables independent yet reproducible),
     * merged into one timeline ordered by sourceTime then nodeId.
     */
    private static List<NeutralValue> generate(
            List<SyntheticVariable> variables, DeterministicSettings settings, long durationMs) {
        DeterminismContext context = settings.newContext();
        List<NeutralValue> values = new ArrayList<>();
        for (SyntheticVariable variable : variables) {
            SyntheticGenerator generator = variable.generator(context);
            long ticks = durationMs / variable.updateRateMs();
            for (long i = 0; i < ticks; i++) {
                values.add(generator.next());
            }
        }
        values.sort(Comparator.comparing(NeutralValue::sourceTime).thenComparing(NeutralValue::nodeId));
        return values;
    }

    /** Inspectable evidence seed: the effective seed + value count (no recordingId for synthetic). */
    private String manifest(long seed, int valueCount) {
        return json.writeValueAsString(Map.of("synthetic", true, "seed", seed, "valueCount", valueCount));
    }

    private SyntheticConfig parseConfig(String runtimeConfig) {
        try {
            return json.readValue(runtimeConfig, SyntheticConfig.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("invalid synthetic runtimeConfig: " + e.getMessage(), e);
        }
    }

    private DataSourceRow requireSource(String projectId, String dataSourceId) {
        return dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
