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
import java.util.LinkedHashMap;
import java.util.List;
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
            Instant startedAt = wallClock.instant();
            evidence.updateManifest(evidenceRow.id(),
                    manifest(run.id(), trigger, initiator, dataSourceId, startedAt, null, settings.seed(), 0));

            runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source));

            List<VariableFeed> feeds = new ArrayList<>();
            for (SyntheticVariable variable : variables) {
                feeds.add(new VariableFeed(variable.generator(context), variable.updateRateMs()));
            }
            registry.put(run.id(), new LiveRun(run.id(), evidenceRow.id(), dataSourceId,
                    trigger, initiator, settings.seed(), startedAt, maxDurationMs, feeds));

            return new SyntheticLiveRunSummary(dataSourceId, settings.seed(), run.id(), evidenceRow.id(), "RUNNING");
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /** Advances every registered live run by one pacing step (called by the pacer, ~every 250 ms). */
    public void tickAll() {
        for (LiveRun live : new ArrayList<>(registry.values())) {
            tickOne(live);
        }
    }

    private void tickOne(LiveRun live) {
        try {
            long elapsedMs = live.elapsedMs(wallClock);
            boolean capped = live.maxDurationMs() != null && elapsedMs >= live.maxDurationMs();
            long effectiveMs = capped ? live.maxDurationMs() : elapsedMs;

            List<NeutralValue> due = new ArrayList<>();
            for (VariableFeed feed : live.feeds()) {
                long dueTicks = effectiveMs / feed.updateRateMs;
                for (long i = feed.emitted; i < dueTicks; i++) {
                    due.add(feed.generator.next());
                }
                feed.emitted = dueTicks;
            }
            due.sort(ORDER);
            if (!due.isEmpty()) {
                runtime.applyValues(live.dataSourceId(), due);
            }
            if (capped) {
                finalizeRun(live, "COMPLETED");
            }
        } catch (RuntimeException e) {
            finalizeRun(live, "FAILED");
        }
    }

    /**
     * Cancels a live feed if present, stamping the final value count on its evidence.
     * Does NOT end the run — the caller ({@code RunService.stop}) owns the STOPPED
     * transition. Returns whether a live feed was actually cancelled (idempotent).
     */
    public boolean stopIfLive(String runId) {
        LiveRun live = registry.remove(runId);
        if (live == null) {
            return false;
        }
        stampEvidence(live, null);
        return true;
    }

    /** Ends the run in a terminal state and stamps the final value count. Atomic via registry.remove. */
    private void finalizeRun(LiveRun live, String terminalState) {
        if (registry.remove(live.runId()) == null) {
            return; // already finalized/stopped
        }
        stampEvidence(live, wallClock.instant());
        runs.end(live.runId(), terminalState, now());
    }

    /**
     * Best-effort stamp of the final value count onto evidence. Failures are swallowed on
     * purpose: the manifest count is advisory, and a transient stamp error must not block
     * the terminal-state write in {@link #finalizeRun} nor surface out of {@link #stopIfLive}
     * into {@code RunService.stop} (which would strand the runtime source and leave the run
     * RUNNING). The run's terminal state stays the source of truth. (The domain module keeps
     * no logging facade by design — same swallow-and-continue stance as the {@code tickOne} catch.)
     */
    private void stampEvidence(LiveRun live, Instant endedAt) {
        try {
            evidence.updateManifest(live.evidenceId(),
                    manifest(live.runId(), live.trigger(), live.initiator(), live.dataSourceId(),
                            live.startedAt(), endedAt, live.seed(), totalEmitted(live)));
        } catch (RuntimeException ignored) {
            // advisory count only; the terminal-state transition must still proceed
        }
    }

    private static long totalEmitted(LiveRun live) {
        long total = 0;
        for (VariableFeed feed : live.feeds()) {
            total += feed.emitted;
        }
        return total;
    }

    private String manifest(String runId, String trigger, String initiator, String dataSourceId,
            Instant startedAt, Instant endedAt, long seed, long valueCount) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "SYNTHETIC");
        m.put("runId", runId);
        m.put("trigger", trigger);
        m.put("initiator", initiator);
        m.put("startedAt", startedAt.toString());
        m.put("endedAt", endedAt != null ? endedAt.toString() : null);
        m.put("sourceIds", List.of(dataSourceId));
        m.put("scenarioId", null);
        m.put("recordingId", null);
        m.put("seed", seed);
        m.put("valueCount", valueCount);
        return json.writeValueAsString(m);
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

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(wallClock.instant(), ZoneOffset.UTC);
    }

    static final Comparator<NeutralValue> ORDER =
            Comparator.comparing(NeutralValue::sourceTime).thenComparing(NeutralValue::nodeId);

    /** One variable's live cursor: its generator and how many samples have been emitted so far. */
    static final class VariableFeed {
        final SyntheticGenerator generator;
        final long updateRateMs;
        // Written only by the pacer thread in tickOne; read by a request thread via
        // totalEmitted() on stop. volatile gives that single-writer/reader hand-off a
        // happens-before edge (no torn/stale count). A stop racing an in-flight tick may
        // still let that tick push one final batch before runtime.stop — benign and expected.
        volatile long emitted;

        VariableFeed(SyntheticGenerator generator, long updateRateMs) {
            this.generator = generator;
            this.updateRateMs = updateRateMs;
        }
    }

    /** In-memory handle for one running live feed. */
    record LiveRun(String runId, String evidenceId, String dataSourceId,
            String trigger, String initiator, long seed,
            Instant startedAt, Long maxDurationMs, List<VariableFeed> feeds) {

        long elapsedMs(Clock wallClock) {
            return Duration.between(startedAt, wallClock.instant()).toMillis();
        }
    }
}
