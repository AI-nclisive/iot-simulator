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
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Run-synthetic (IS-119, Model B): a continuous, real-time-paced live feed. Opens a
 * {@code SYNTHETIC} run in {@code RUNNING} and emits each variable's due samples on
 * every {@link #tickAll()} (paced by the injected wall {@link Clock}) until stopped
 * ({@link #stopIfLive}) or an optional {@code maxDurationMs} cap is reached. The value
 * <em>sequence</em> stays seed-deterministic; only how many samples are emitted before
 * a stop varies with wall-clock time. The bounded one-shot twin is {@code SyntheticRunService}.
 */
@Service
public class SyntheticLiveRunService {

    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;
    private final Clock wallClock;

    private final ConcurrentMap<String, LiveRun> registry = new ConcurrentHashMap<>();

    @Autowired
    public SyntheticLiveRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence, ObjectMapper json) {
        this(dataSources, schemas, runtime, runs, evidence, json, Clock.systemUTC());
    }

    /** Test seam: inject a controllable wall clock so paced emission is reproducible without sleeps. */
    SyntheticLiveRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence,
            ObjectMapper json, Clock wallClock) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.runtime = runtime;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
        this.wallClock = wallClock;
    }

    /**
     * Starts a continuous live synthetic run. {@code maxDurationMs} is an optional
     * wall-clock safety cap (null = unbounded, stopped only via {@code /runs/{id}/stop}).
     */
    public SyntheticLiveRunSummary start(String projectId, String dataSourceId, Long maxDurationMs,
            String trigger, String initiator) {
        if (maxDurationMs != null && maxDurationMs <= 0) {
            throw new IllegalArgumentException("maxDurationMs must be > 0 when set: " + maxDurationMs);
        }
        DataSourceRow source = requireSource(projectId, dataSourceId);
        if (!"SYNTHETIC".equals(source.basis())) {
            throw new IllegalArgumentException("data source is not synthetic: " + dataSourceId);
        }
        SyntheticConfig config = parseConfig(source.runtimeConfig());
        List<SyntheticVariable> variables = SyntheticConfigMapper.toVariables(config);

        DeterministicSettings settings = config.seed() == null
                ? DeterministicSettings.withRandomSeed(wallClock.instant())
                : new DeterministicSettings(config.seed(), wallClock.instant());
        DeterminismContext context = settings.newContext();

        RunRow run = runs.create(projectId, "SYNTHETIC", trigger, initiator, List.of(dataSourceId), null, null);
        try {
            runs.start(run.id(), now());
            EvidenceRow evidenceRow = evidence.create(projectId, run.id(), initiator);
            runs.linkEvidence(run.id(), evidenceRow.id());
            evidence.updateManifest(evidenceRow.id(), manifest(settings.seed(), 0));

            runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source, json));

            List<VariableFeed> feeds = new ArrayList<>();
            for (SyntheticVariable variable : variables) {
                feeds.add(new VariableFeed(variable.generator(context), variable.updateRateMs()));
            }
            registry.put(run.id(), new LiveRun(run.id(), evidenceRow.id(), dataSourceId,
                    settings.seed(), wallClock.instant(), maxDurationMs, feeds));

            return new SyntheticLiveRunSummary(dataSourceId, settings.seed(), run.id(), evidenceRow.id(), "RUNNING");
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /** Placeholder — implemented in Task 2. */
    public void tickAll() {
        // Task 2
    }

    /** Placeholder — implemented in Task 3. */
    public boolean stopIfLive(String runId) {
        return false; // Task 3
    }

    private String manifest(long seed, long valueCount) {
        return json.writeValueAsString(Map.of("synthetic", true, "live", true, "seed", seed, "valueCount", valueCount));
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

    static final Comparator<NeutralValue> ORDER =
            Comparator.comparing(NeutralValue::sourceTime).thenComparing(NeutralValue::nodeId);

    /** One variable's live cursor: its generator and how many samples have been emitted so far. */
    static final class VariableFeed {
        final SyntheticGenerator generator;
        final long updateRateMs;
        long emitted;

        VariableFeed(SyntheticGenerator generator, long updateRateMs) {
            this.generator = generator;
            this.updateRateMs = updateRateMs;
        }
    }

    /** In-memory handle for one running live feed. */
    record LiveRun(String runId, String evidenceId, String dataSourceId, long seed,
            Instant startWall, Long maxDurationMs, List<VariableFeed> feeds) {

        long elapsedMs(Clock wallClock) {
            return Duration.between(startWall, wallClock.instant()).toMillis();
        }
    }
}
