# IS-119 — Continuous Live Synthetic Feed (Model B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "run a synthetic source" a continuous, real-time-paced live feed (runs until stopped, optional `maxDurationMs` cap) while keeping the existing bounded one-shot batch as the scenario-step / dataset primitive.

**Architecture:** A new pure-logic `SyntheticLiveRunService` (domain) opens a `SYNTHETIC` run in `RUNNING`, holds per-variable generators in an in-memory registry, and on each `tickAll()` emits only the samples due since start (gated by an injected wall `Clock`), pushing them through `runtime.applyValues(...)` (which already fans out to SSE). A thin `SyntheticLivePacer` bean (app module) owns a daemon `ScheduledExecutorService` that calls `tickAll()` every 250 ms. The standalone REST endpoint and the `POST /runs {kind:"SYNTHETIC"}` dispatcher re-route to the live service; scenarios keep calling the untouched `SyntheticRunService.run(...)`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5 + AssertJ, Jackson 3 (`tools.jackson.*`), Gradle multi-module.

## Global Constraints

- Jackson 3: import `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException` — NOT `com.fasterxml.jackson.*`.
- Multi-constructor `@Component`/`@Service`: annotate the production constructor with `@Autowired` (test-seam constructor stays package-private) or the context won't boot.
- Task ID convention: this is `IS-119` (backend). Commits reference `IS-119`; PR body uses `Implements: IS-119`.
- Do NOT bump the API version — stay on `/api/v1`.
- Run the FULL `./gradlew build` before opening the PR (Spotless/Checkstyle only run in the full build, not `test --tests`).
- No new dependencies. No changes to the runtime-supervisor module.
- Governance: the SPEC.md wording change (Task 7) requires explicit user approval at execution time before committing — do not edit SPEC.md silently.

## File Structure

- **Create** `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunSummary.java` — start-time summary record.
- **Create** `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java` — registry + `start` / `tickAll` / `stopIfLive` + finalize (COMPLETED/FAILED). No threads.
- **Create** `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java` — unit tests using a controllable wall clock and manual `tickAll()`.
- **Create** `app/src/main/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacer.java` — daemon scheduler bean calling `tickAll()`.
- **Create** `app/src/test/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacerTest.java` — verifies the scheduler invokes `tickAll()` and shuts down.
- **Modify** `api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java` — route to live service; new request/response shape; updated Swagger text.
- **Modify** `app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java` — assert `RUNNING` response.
- **Modify** `domain/src/main/java/com/ainclusive/iotsim/domain/run/RunService.java` — SYNTHETIC branch → live; stop path cancels the live feed; relax `durationMs` to an optional cap.
- **Modify** `domain/src/test/java/com/ainclusive/iotsim/domain/run/RunServiceTest.java` — update SYNTHETIC start/stop expectations.
- **Modify** `backend-specs/TASKS.md` — re-scope the IS-119 line description.
- **Modify** `SPEC.md` — clarifying note (user-gated).

---

### Task 1: `SyntheticLiveRunService.start()` + `SyntheticLiveRunSummary`

Opens a `SYNTHETIC` run in `RUNNING`, creates + links evidence with a `{synthetic,live,seed,valueCount:0}` manifest, starts the runtime, registers the run's per-variable generators, and returns immediately. No ticking yet.

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunSummary.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java`

**Interfaces:**
- Consumes (all existing): `DataSourceRepository`, `SchemaRepository`, `RuntimeController`, `RunRepository`, `EvidenceRepository`, `tools.jackson.databind.ObjectMapper`, `RuntimeStartSpecs.of(schemas, source, json)`, `SyntheticConfig`, `SyntheticConfigMapper.toVariables(config)`, `SyntheticVariable.generator(context)`, `DeterministicSettings`.
- Produces: `SyntheticLiveRunSummary(String dataSourceId, long seed, String runId, String evidenceId, String state)`; `SyntheticLiveRunService.start(String projectId, String dataSourceId, Long maxDurationMs, String trigger, String initiator)` → `SyntheticLiveRunSummary`; plus (used by later tasks) `tickAll()`, `boolean stopIfLive(String runId)`, and a package-private test constructor taking a `java.time.Clock wallClock`.

- [ ] **Step 1: Write the failing test**

Create `SyntheticLiveRunServiceTest.java`. Copy the fakes block verbatim from `SyntheticRunServiceTest.java` (the inner classes `CapturingRuntime`, `FakeRuns`, `FakeEvidence`, `FakeDataSources`, `EmptySchemas`, and the `config(Long seed)` helper) — they implement exactly the interfaces this service needs. Then add the wall-clock seam and this first test:

```java
package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.protocolmodel.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SyntheticLiveRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private CapturingRuntime runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;
    private MutableClock wall; // injected wall clock we advance by hand

    @BeforeEach
    void setUp() {
        runtime = new CapturingRuntime();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        wall = MutableClock.at(T0, ZoneOffset.UTC);
    }

    private SyntheticLiveRunService service(String basis, SyntheticConfig config) {
        String rc = config == null ? "{}" : json.writeValueAsString(config);
        return new SyntheticLiveRunService(new FakeDataSources(basis, rc), new EmptySchemas(),
                runtime, runs, evidence, json, wall);
    }

    @Test
    void startOpensRunningRunAndEvidenceAndReturnsImmediately() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));

        SyntheticLiveRunSummary summary = service.start(PROJECT, SOURCE, null, "MANUAL", "local");

        assertThat(summary.seed()).isEqualTo(5L);
        assertThat(summary.state()).isEqualTo("RUNNING");
        assertThat(summary.dataSourceId()).isEqualTo(SOURCE);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.applied).isEmpty(); // nothing emitted until a tick

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("SYNTHETIC");
        assertThat(run.state()).isEqualTo("RUNNING");
        assertThat(run.trigger()).isEqualTo("MANUAL");
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson())
                .contains("\"seed\":5").contains("\"live\":true");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: FAIL — `SyntheticLiveRunService` / `SyntheticLiveRunSummary` do not exist (compilation error).

- [ ] **Step 3: Write the summary record**

Create `SyntheticLiveRunSummary.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

/**
 * Start-time result of a continuous live synthetic run (Model B, IS-119).
 * The value count is unknown at start (the feed runs until stopped), so it is not
 * part of this summary — read progress via {@code GET /runs/{id}/state} and the
 * final count from the run's evidence once it ends.
 */
public record SyntheticLiveRunSummary(
        String dataSourceId, long seed, String runId, String evidenceId, String state) {}
```

- [ ] **Step 4: Write the minimal service (start + registry scaffolding only)**

Create `SyntheticLiveRunService.java`:

```java
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunSummary.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java
git commit -m "feat(IS-119): SyntheticLiveRunService.start opens a RUNNING live synthetic run"
```

---

### Task 2: `tickAll()` — real-time-paced emission

On each tick, for every registered run, compute how many samples each variable is due (`elapsedMs / updateRateMs`), pull only the newly-due samples from its generator, order them, and push via `applyValues`. The value sequence must match a Model-A batch of the same elapsed duration (seed-determinism preserved).

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java`

**Interfaces:**
- Consumes: `LiveRun`, `VariableFeed`, `ORDER` from Task 1.
- Produces: working `tickAll()`.

- [ ] **Step 1: Write the failing tests**

Add to `SyntheticLiveRunServiceTest`:

```java
    @Test
    void tickEmitsOnlySamplesDueSinceStart() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        service.start(PROJECT, SOURCE, null, "MANUAL", "local");

        // config: temp @100ms, rnd @250ms. Advance 500ms of wall time.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();

        // temp: 500/100 = 5 ; rnd: 500/250 = 2  => 7 values on first tick.
        assertThat(runtime.applied).hasSize(7);

        // Advance to 1000ms total and tick again.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();

        // temp now 10 total (=> +5), rnd now 4 total (=> +2) => +7, cumulative 14.
        assertThat(runtime.applied).hasSize(14);
    }

    @Test
    void livePacedSequenceMatchesBoundedBatchForSameElapsed() {
        // Model A over 1000ms produces 14 values (see SyntheticRunServiceTest); the paced
        // feed must produce the identical ordered sequence once 1000ms has elapsed.
        SyntheticRunService batch = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), new CapturingRuntime(), new FakeRuns(), new FakeEvidence(),
                json, java.time.Clock.fixed(T0, ZoneOffset.UTC));
        CapturingRuntime batchRuntime = new CapturingRuntime();
        SyntheticRunService batch2 = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), batchRuntime, new FakeRuns(), new FakeEvidence(),
                json, java.time.Clock.fixed(T0, ZoneOffset.UTC));
        batch2.run(PROJECT, SOURCE, 1000);

        SyntheticLiveRunService live = service("SYNTHETIC", config(5L));
        live.start(PROJECT, SOURCE, null, "MANUAL", "local");
        wall.advance(Duration.ofMillis(1000));
        live.tickAll();

        assertThat(runtime.applied).isEqualTo(batchRuntime.applied);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: FAIL — `tickEmitsOnlySamplesDueSinceStart` expects 7 but the placeholder emits 0.

- [ ] **Step 3: Implement `tickAll()`**

Replace the `tickAll()` placeholder in `SyntheticLiveRunService`:

```java
    /** Advances every registered live run by one pacing step (called by the pacer, ~every 250 ms). */
    public void tickAll() {
        for (LiveRun live : registry.values()) {
            tickOne(live);
        }
    }

    private void tickOne(LiveRun live) {
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
        // Cap handling (COMPLETED) is added in Task 3.
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: PASS (all four tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java
git commit -m "feat(IS-119): tickAll emits real-time-paced due samples, seed-deterministic"
```

---

### Task 3: `stopIfLive` + finalize on cap (COMPLETED) and error (FAILED)

Manual stop cancels the feed and stamps the final `valueCount` on evidence, leaving the run-ending to `RunService`. The cap ends the run `COMPLETED`; a throwing tick ends it `FAILED` without affecting other runs. `registry.remove(...)` is the single atomic gate that prevents double-finalize.

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java`

**Interfaces:**
- Produces: working `boolean stopIfLive(String runId)`; cap→COMPLETED and error→FAILED finalize inside `tickOne`.

- [ ] **Step 1: Write the failing tests**

Add to `SyntheticLiveRunServiceTest` (add imports `import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;` and `import java.util.List;` if not already present via the copied fakes):

```java
    @Test
    void capReachedFinalizesRunCompletedWithTotalValueCount() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        SyntheticLiveRunSummary s = service.start(PROJECT, SOURCE, 1000L, "MANUAL", "local");

        wall.advance(Duration.ofMillis(5000)); // well past the 1000ms cap
        service.tickAll();

        RunRow run = runs.byId.get(s.runId());
        assertThat(run.state()).isEqualTo("COMPLETED");
        // capped at 1000ms => 14 values (temp 10 + rnd 4); count reflected in evidence.
        assertThat(runtime.applied).hasSize(14);
        assertThat(evidence.byId.get(s.evidenceId()).manifestJson()).contains("\"valueCount\":14");
        // A subsequent tick is a no-op (removed from registry).
        service.tickAll();
        assertThat(runtime.applied).hasSize(14);
    }

    @Test
    void stopIfLiveCancelsFeedAndStampsEvidenceButLeavesRunEndingToCaller() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        SyntheticLiveRunSummary s = service.start(PROJECT, SOURCE, null, "MANUAL", "local");
        wall.advance(Duration.ofMillis(500));
        service.tickAll(); // 7 emitted

        boolean wasLive = service.stopIfLive(s.runId());

        assertThat(wasLive).isTrue();
        assertThat(evidence.byId.get(s.evidenceId()).manifestJson()).contains("\"valueCount\":7");
        // Run is NOT ended here (RunService owns that on manual stop): still RUNNING.
        assertThat(runs.byId.get(s.runId()).state()).isEqualTo("RUNNING");
        // Ticking after stop does nothing.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();
        assertThat(runtime.applied).hasSize(7);
        // Idempotent second stop.
        assertThat(service.stopIfLive(s.runId())).isFalse();
    }

    @Test
    void tickErrorFinalizesThatRunFailedWithoutAffectingOthers() {
        // Runtime that throws only for the failing source.
        CapturingRuntime ok = new CapturingRuntime();
        SyntheticLiveRunService service = new SyntheticLiveRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), ok, runs, evidence, json, wall) {
        };
        // Two runs on the same fake source id is not possible; instead assert a throwing
        // applyValues marks the run FAILED and is removed.
        CapturingRuntime throwing = new CapturingRuntime() {
            @Override public long applyValues(String id, List<com.ainclusive.iotsim.protocolmodel.NeutralValue> v) {
                throw new IllegalStateException("apply failed");
            }
        };
        SyntheticLiveRunService failing = new SyntheticLiveRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), throwing, runs, evidence, json, wall);
        SyntheticLiveRunSummary s = failing.start(PROJECT, SOURCE, null, "MANUAL", "local");

        wall.advance(Duration.ofMillis(500));
        failing.tickAll(); // applyValues throws -> run FAILED

        assertThat(runs.byId.get(s.runId()).state()).isEqualTo("FAILED");
        assertThat(failing.stopIfLive(s.runId())).isFalse(); // already removed
    }
```

Note: `CapturingRuntime` and the other fakes were copied from `SyntheticRunServiceTest` in Task 1; the anonymous `throwing` subclass overrides `applyValues`. Ensure `CapturingRuntime`'s methods are non-`final` and its class is `static` (it already is).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: FAIL — cap does not finalize; `stopIfLive` returns false.

- [ ] **Step 3: Implement finalize + stopIfLive**

In `SyntheticLiveRunService`, replace the `stopIfLive` placeholder and extend `tickOne`:

```java
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
        evidence.updateManifest(live.evidenceId(), manifest(live.seed(), totalEmitted(live)));
        return true;
    }
```

Extend `tickOne` — wrap the body in try/catch and handle the cap:

```java
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
                finalize(live, "COMPLETED");
            }
        } catch (RuntimeException e) {
            finalize(live, "FAILED");
        }
    }

    /** Ends the run in a terminal state and stamps the final value count. Atomic via registry.remove. */
    private void finalize(LiveRun live, String terminalState) {
        if (registry.remove(live.runId()) == null) {
            return; // already finalized/stopped
        }
        evidence.updateManifest(live.evidenceId(), manifest(live.seed(), totalEmitted(live)));
        runs.end(live.runId(), terminalState, now());
    }

    private static long totalEmitted(LiveRun live) {
        long total = 0;
        for (VariableFeed feed : live.feeds()) {
            total += feed.emitted;
        }
        return total;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*SyntheticLiveRunServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticLiveRunServiceTest.java
git commit -m "feat(IS-119): stopIfLive + cap(COMPLETED)/error(FAILED) finalize with valueCount"
```

---

### Task 4: `SyntheticLivePacer` scheduler bean (app module)

A thin daemon-scheduled bean that calls `SyntheticLiveRunService.tickAll()` at a fixed rate (default 250 ms, property-overridable). Mirrors `LiveValuesHub`'s single-thread daemon scheduler. Cancels + shuts down on context close.

**Files:**
- Create: `app/src/main/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacer.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacerTest.java`

**Interfaces:**
- Consumes: `SyntheticLiveRunService.tickAll()`.
- Produces: `SyntheticLivePacer` bean (auto-starts via `@PostConstruct`, stops via `@PreDestroy`); package-visible `start()` / `stop()` for testing.

- [ ] **Step 1: Write the failing test**

Create `SyntheticLivePacerTest.java`:

```java
package com.ainclusive.iotsim.app.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SyntheticLivePacerTest {

    @Test
    void scheduledPacerInvokesTickAllRepeatedlyThenStops() {
        AtomicInteger ticks = new AtomicInteger();
        SyntheticLiveRunService service = new CountingService(ticks);
        SyntheticLivePacer pacer = new SyntheticLivePacer(service, 20L); // 20ms interval

        pacer.start();
        await().atMost(Duration.ofSeconds(2)).until(() -> ticks.get() >= 3);
        pacer.stop();

        int afterStop = ticks.get();
        // No further ticks after stop().
        await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofMillis(200))
                .until(() -> ticks.get() == afterStop);
        assertThat(ticks.get()).isEqualTo(afterStop);
    }

    /** Minimal stand-in that counts tickAll() calls (no repositories needed). */
    private static final class CountingService extends SyntheticLiveRunService {
        private final AtomicInteger ticks;

        CountingService(AtomicInteger ticks) {
            super(null, null, null, null, null, null, java.time.Clock.systemUTC());
            this.ticks = ticks;
        }

        @Override
        public void tickAll() {
            ticks.incrementAndGet();
        }
    }
}
```

If `awaitility` is not already a test dependency in the `app` module, use a `CountDownLatch` in `CountingService.tickAll()` instead and `latch.await(2, SECONDS)` — check `app/build.gradle` first; the repo already uses Awaitility in supervisor tests, but verify per-module.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests '*SyntheticLivePacerTest'`
Expected: FAIL — `SyntheticLivePacer` does not exist.

- [ ] **Step 3: Implement the pacer**

Create `SyntheticLivePacer.java`:

```java
package com.ainclusive.iotsim.app.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Drives {@link SyntheticLiveRunService#tickAll()} on a fixed wall-clock cadence so
 * continuous synthetic runs (IS-119, Model B) emit paced values. One daemon thread,
 * mirroring the SSE flush scheduler pattern. Auto-starts on context refresh and shuts
 * down on close.
 */
@Component
public class SyntheticLivePacer {

    private final SyntheticLiveRunService service;
    private final long tickIntervalMs;
    private ScheduledExecutorService scheduler;

    public SyntheticLivePacer(SyntheticLiveRunService service,
            @Value("${iotsim.synthetic.live.tick-interval-ms:250}") long tickIntervalMs) {
        this.service = service;
        this.tickIntervalMs = tickIntervalMs;
    }

    @PostConstruct
    void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory());
        scheduler.scheduleAtFixedRate(this::safeTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void safeTick() {
        try {
            service.tickAll();
        } catch (RuntimeException e) {
            // tickAll already isolates per-run failures; never let the scheduler thread die.
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "synthetic-live-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests '*SyntheticLivePacerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacer.java \
        app/src/test/java/com/ainclusive/iotsim/app/synthetic/SyntheticLivePacerTest.java
git commit -m "feat(IS-119): SyntheticLivePacer daemon scheduler drives tickAll every 250ms"
```

---

### Task 5: Re-route `SyntheticRunController` to the live feed

The standalone `POST .../run-synthetic` now starts a live run: it depends on `SyntheticLiveRunService`, accepts an optional `maxDurationMs` (body optional → unbounded), and returns `state:"RUNNING"` with `valueCount:0`. Swagger text updated.

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java`

**Interfaces:**
- Consumes: `SyntheticLiveRunService.start(projectId, dataSourceId, maxDurationMs, "MANUAL", "local")` → `SyntheticLiveRunSummary`.
- Produces: `SyntheticRunResponse(String dataSourceId, long valueCount, long seed, String runId, String evidenceId, String state)`; `SyntheticRunRequest(Long maxDurationMs)`.

- [ ] **Step 1: Update the failing test**

Open `app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java`. Replace its mocking/assertions so it mocks `SyntheticLiveRunService.start(...)` returning `new SyntheticLiveRunSummary("ds1", 5L, "run-1", "ev-1", "RUNNING")` and asserts the JSON response contains `"state":"RUNNING"`, `"runId":"run-1"`, `"valueCount":0`. (Follow the existing MockMvc harness style already in this file; only the mocked service type, the stubbed method, and the assertions change.)

Key assertions:

```java
// given
org.mockito.Mockito.when(liveRuns.start(
        org.mockito.ArgumentMatchers.eq("p1"),
        org.mockito.ArgumentMatchers.eq("ds1"),
        org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq("MANUAL"),
        org.mockito.ArgumentMatchers.eq("local")))
    .thenReturn(new com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary(
            "ds1", 5L, "run-1", "ev-1", "RUNNING"));

// when / then (empty body -> unbounded)
mockMvc.perform(post("/api/v1/projects/p1/data-sources/ds1/run-synthetic")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.state").value("RUNNING"))
    .andExpect(jsonPath("$.runId").value("run-1"))
    .andExpect(jsonPath("$.valueCount").value(0));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests '*SyntheticRunControllerTest'`
Expected: FAIL — controller still uses `SyntheticRunService`; no `state` field.

- [ ] **Step 3: Rewrite the controller**

Replace `SyntheticRunController.java` body:

```java
package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a continuous, real-time-paced live synthetic run on a data source (Model B,
 * IS-119): values flow over wall-clock time until stopped via {@code POST /runs/{id}/stop}
 * (optional {@code maxDurationMs} safety cap). The bounded one-shot batch remains the
 * primitive used by scenario steps.
 *
 * <p>Authorization (IS-077): running synthetic data is a runtime-operate action —
 * {@link Permission#REPLAY_START} (user + admin).
 */
@RestController
@Tag(name = "Synthetic Runs", description = "Start a continuous live synthetic (generated) feed on a data source.")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic")
public class SyntheticRunController {

    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";

    private final SyntheticLiveRunService liveRuns;

    public SyntheticRunController(SyntheticLiveRunService liveRuns) {
        this.liveRuns = liveRuns;
    }

    @Operation(summary = "Start a live synthetic run",
            description = "Starts a continuous, real-time-paced synthetic feed on the data source. The run stays"
                    + " RUNNING until stopped via POST /runs/{id}/stop; an optional maxDurationMs caps it."
                    + " Returns immediately with the created run.")
    @PostMapping
    @PreAuthorize(REPLAY_START)
    public SyntheticRunResponse run(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody(required = false) SyntheticRunRequest req) {
        Long maxDurationMs = req != null ? req.maxDurationMs() : null;
        SyntheticLiveRunSummary summary = liveRuns.start(projectId, dataSourceId, maxDurationMs, "MANUAL", "local");
        return new SyntheticRunResponse(summary.dataSourceId(), 0L,
                summary.seed(), summary.runId(), summary.evidenceId(), summary.state());
    }

    /** Optional wall-clock safety cap in milliseconds; null/omitted = unbounded (stop via /runs/{id}/stop). */
    public record SyntheticRunRequest(Long maxDurationMs) {}

    public record SyntheticRunResponse(
            String dataSourceId, long valueCount, long seed, String runId, String evidenceId, String state) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests '*SyntheticRunControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java \
        app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java
git commit -m "feat(IS-119): run-synthetic endpoint starts a live feed (RUNNING), optional cap"
```

---

### Task 6: Re-route `RunService` SYNTHETIC branch + stop wiring

The `POST /runs {kind:"SYNTHETIC"}` dispatcher now starts a live run (with `durationMs` reinterpreted as the optional cap, no longer required). `RunService.stop` cancels the live feed before the existing per-source stop + STOPPED transition. Scenario dispatch is untouched.

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/run/RunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/run/RunServiceTest.java`

**Interfaces:**
- Consumes: `SyntheticLiveRunService.start(projectId, dataSourceId, cap, "AUTOMATION", initiator)`, `SyntheticLiveRunService.stopIfLive(runId)`.
- Produces: unchanged public `RunService` API; SYNTHETIC starts now yield a `RUNNING` run.

- [ ] **Step 1: Update the failing tests**

In `RunServiceTest.java`: the SYNTHETIC start test should now expect the returned run `state` to be `RUNNING` (not `COMPLETED`), and should no longer require `durationMs`. Add a test that `stop(...)` on a synthetic-live run calls `syntheticLive.stopIfLive(runId)` and ends the run `STOPPED`. Follow the file's existing construction of `RunService` (add the new `SyntheticLiveRunService` dependency to the constructor call / test fixture). Example new assertions:

```java
// SYNTHETIC start now returns a RUNNING run via the live service
when(syntheticLive.start("p1", "ds1", null, "AUTOMATION", "ci-bot"))
        .thenReturn(new SyntheticLiveRunSummary("ds1", 7L, "run-9", "ev-9", "RUNNING"));
RunView v = service.start("p1", new StartRunCommand("SYNTHETIC", "ci-bot", "ds1",
        null, null, null, null, null, null));
assertThat(v.state()).isEqualTo("RUNNING");

// stop cancels the live feed then ends STOPPED
service.stop("p1", "run-9");
verify(syntheticLive).stopIfLive("run-9");
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests '*RunServiceTest'`
Expected: FAIL — constructor arity mismatch / SYNTHETIC still routes to the batch service.

- [ ] **Step 3: Modify `RunService`**

1. Add the dependency. Change the field/constructor:

```java
    private final SyntheticRunService synthetic;          // batch primitive (scenarios)
    private final SyntheticLiveRunService syntheticLive;  // live standalone feed (IS-119)
```

Add `SyntheticLiveRunService syntheticLive` as a constructor parameter and assign it (add the import `import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;` and `import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary;`).

2. Replace the `SYNTHETIC` switch branch (was lines 110-116):

```java
            case "SYNTHETIC" -> {
                requireField(cmd.dataSourceId(), "dataSourceId");
                if (cmd.durationMs() != null && cmd.durationMs() <= 0) {
                    throw new IllegalArgumentException("durationMs (cap) must be > 0 when set");
                }
                yield syntheticLive.start(projectId, cmd.dataSourceId(), cmd.durationMs(),
                        "AUTOMATION", initiator).runId();
            }
```

3. In `stop(...)`, cancel the live feed before the existing per-source stop (new first line inside the method):

```java
    public RunView stop(String projectId, String id) {
        RunRow run = require(projectId, id);
        syntheticLive.stopIfLive(id);                 // no-op unless it is a live synthetic run
        run.sourceIds().forEach(runtime::stop);
        RunRow after = TERMINAL.contains(run.state())
                ? run
                : runs.end(id, "STOPPED", OffsetDateTime.now(ZoneOffset.UTC));
        return view(after, sourceNames(projectId));
    }
```

Update the `StartRunCommand` javadoc note for SYNTHETIC to read `dataSourceId`, `durationMs (optional cap)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*RunServiceTest'`
Expected: PASS. Also run `./gradlew :domain:test --tests '*ScenarioRunServiceTest'` — expect PASS (batch path untouched).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/run/RunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/run/RunServiceTest.java
git commit -m "feat(IS-119): /runs SYNTHETIC starts a live feed; stop cancels it (scenarios unchanged)"
```

---

### Task 7: Governance — SPEC.md note (user-gated) + TASKS.md re-scope

**Files:**
- Modify: `SPEC.md`
- Modify: `backend-specs/TASKS.md`

- [ ] **Step 1: Propose the SPEC.md wording and get explicit user approval**

Per `AGENTS.md`, do not edit `SPEC.md` silently. Present this proposed addition under the *Generate Synthetic Data* / *Run Deterministic Scenarios* area and wait for the user's OK:

> Running a synthetic source emits values as a **continuous, real-time-paced live feed** that runs until stopped (with an optional duration cap); the bounded one-shot generation remains available as a scenario step and for deterministic dataset generation.

- [ ] **Step 2: Apply the approved SPEC.md edit**

Insert the approved sentence in the relevant capability paragraph. (Exact line depends on the approved wording.)

- [ ] **Step 3: Re-scope the TASKS.md IS-119 line description**

In `backend-specs/TASKS.md`, replace the IS-119 line text (keep the checkbox state for `/open-pr` to flip) so it reads:

```
- [ ] IS-119 [BE] ⬜ [runtime] Run synthetic source — continuous live feed (Model B / real-time pacing): standalone run-synthetic + /runs kind=SYNTHETIC now start a live paced feed (until stop + optional cap); bounded one-shot retained as the scenario-step primitive — 02
```

- [ ] **Step 4: Commit**

```bash
git add SPEC.md backend-specs/TASKS.md
git commit -m "docs(IS-119): SPEC note on live synthetic feed; re-scope TASKS line"
```

---

### Task 8: Full build green

**Files:** none (verification).

- [ ] **Step 1: Run the full build (Testcontainers env exported)**

Run:
```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew build
```
Expected: `BUILD SUCCESSFUL`. This is the only place Spotless/Checkstyle run — fix any unused-import or formatting violations here.

- [ ] **Step 2: Sanity-check the smoke IT still boots the context**

The new `@Service` (`SyntheticLiveRunService`) and `@Component` (`SyntheticLivePacer`) must not break bean wiring. Confirm `ApplicationSmokeIT` / supervisor-mode context boot pass in the full build output (re-run with `--rerun-tasks` if cache-masked locally).

- [ ] **Step 3: Commit any formatting fixes**

```bash
git add -A
git commit -m "chore(IS-119): satisfy spotless/checkstyle in full build"
```

---

## Self-Review

**Spec coverage:**
- Live paced feed until stop + optional cap → Tasks 1–3 (`start`, `tickAll`, cap/finalize). ✓
- Standalone surfaces re-routed to live; scenarios keep batch → Tasks 5 (controller) & 6 (RunService), with `ScenarioRunService`/`SyntheticRunService` untouched. ✓
- Pacing via shared daemon `ScheduledExecutorService`, 250 ms → Task 4. ✓
- Stop wiring on existing `/runs/{id}/stop` → Task 6. ✓
- Determinism (sequence seed-deterministic; matches batch for same elapsed) → Task 2 test `livePacedSequenceMatchesBoundedBatchForSameElapsed`. ✓
- Evidence manifest valueCount at finalize → Task 3. ✓
- Contract change (`state:"RUNNING"`, valueCount 0 at start, `maxDurationMs`) → Task 5. ✓
- Error handling (invalid/missing/not-synthetic at start; per-run tick failure → FAILED; scheduler survives) → Tasks 1, 3, 4. ✓
- Governance (SPEC note gated, TASKS re-scope) → Task 7. ✓
- Full build / checkstyle → Task 8. ✓

**Placeholder scan:** The `tickAll()`/`stopIfLive` placeholders in Task 1 are intentional TDD scaffolding, each fully implemented in Tasks 2–3 with complete code. No "TODO/handle edge cases/similar to Task N" left as real work.

**Type consistency:** `SyntheticLiveRunSummary(dataSourceId, seed, runId, evidenceId, state)` used identically in Tasks 1, 5, 6. `start(projectId, dataSourceId, Long maxDurationMs, trigger, initiator)` and `stopIfLive(String)`/`tickAll()` signatures match across service, pacer, controller, and RunService. `SyntheticRunResponse` 6-arg shape consistent between Task 5 controller and test. `LiveRun`/`VariableFeed`/`ORDER` defined in Task 1, used in Tasks 2–3.

**Out of scope (unchanged):** `SyntheticRunService`, `ScenarioRunService`, `ReplayService`, runtime-supervisor, API version.
