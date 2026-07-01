# IS-089 Runs resource + test-control — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** A unified `/runs` REST resource for automation — list/get runs, poll a run's readiness/state, stop a run, and start a run (replay/synthetic/scenario) with a non-anonymous automation initiator.

**Architecture:** New `RunService` (domain) over the existing `RunRepository` (+ new `findByProjectPaged`), reusing the three run services for `POST /runs` routing (with `trigger`/`initiator` threaded through Replay/Synthetic) and `RuntimeController.health` for `/state`. New `RunController` (api). Enrichment (labels) mirrors IS-122's `ActiveRunService` (which is left untouched).

**Tech Stack:** Java 21, Spring Boot, jOOQ, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ + Mockito, Testcontainers (Postgres 17).

## Global Constraints

- Jackson 3 (`tools.jackson.*`, never `com.fasterxml.jackson.*`); `@Service`/`@Repository`/`@RestController`, constructor injection.
- Run kinds `REPLAY|SYNTHETIC|SCENARIO|RECORDING`; states `QUEUED→RUNNING→STOPPED|FAILED|COMPLETED`; trigger `MANUAL|AUTOMATION`.
- `POST /runs`: `trigger="AUTOMATION"`, `initiator` required + non-blank (never anonymous); routes by `kind` to Replay/Synthetic/Scenario.
- `POST /runs/{id}/stop`: `runtime.stop` each participating source; if run non-terminal → `runs.end(id,"STOPPED",now())`; terminal → sources stopped, state left.
- Cursor pagination (IS-074) via `Page`/`PageCursor`; `@PreAuthorize` (IS-077): GET → `OBSERVE`, `POST /runs` → `REPLAY_START`, `/stop` → `REPLAY_STOP`.
- Leave IS-122 `ActiveRunService`/`/active-runs` untouched.
- After all tasks: `./gradlew build` green; IS-089 catalog `[x]` via `/open-pr`.

---

## File Structure

```
persistence/.../run/RunRepository.java, JooqRunRepository.java   (modify) — findByProjectPaged
persistence/src/test/.../run/RunRepositoryIT.java                (modify) — paged test
domain/.../replay/ReplayService.java                            (modify) — trigger/initiator overload
domain/.../synthetic/SyntheticRunService.java                   (modify) — trigger/initiator overload
domain/.../run/RunView.java, RunState.java, SourceState.java, RunService.java  (create)
domain/src/test/.../run/RunServiceTest.java                     (create)
domain/src/test/.../replay/ReplayServiceTest.java, synthetic/SyntheticRunServiceTest.java (modify) — threading tests
api/.../run/RunController.java                                  (create) + nested DTOs
api/src/test/.../run/RunControllerTest.java                     (create)
```

---

## Task 1: `RunRepository.findByProjectPaged`

**Files:**
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/run/RunRepository.java`, `JooqRunRepository.java`
- Test: `persistence/src/test/java/com/ainclusive/iotsim/persistence/run/RunRepositoryIT.java`

**Interfaces:**
- Produces: `List<RunRow> RunRepository.findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit)` — sort `created_at DESC, id DESC`.

- [ ] **Step 1: Add the interface method**

In `RunRepository.java`:
```java
    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<RunRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);
```

- [ ] **Step 2: Write the failing IT**

Add to `RunRepositoryIT.java` (reuse its wired `runs` + `projectId`):
```java
    @Test
    void findByProjectPagedReturnsNewestFirstWithCursor() {
        RunRow a = runs.create(projectId, "REPLAY", "MANUAL", "local", List.of(), null, null);
        RunRow b = runs.create(projectId, "SYNTHETIC", "MANUAL", "local", List.of(), null, null);
        RunRow c = runs.create(projectId, "SCENARIO", "MANUAL", "local", List.of(), "scn-x", null);

        List<RunRow> page1 = runs.findByProjectPaged(projectId, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        RunRow last = page1.get(1);
        List<RunRow> page2 = runs.findByProjectPaged(projectId, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(RunRow::id).contains(a.id());
    }
```

- [ ] **Step 3: Run to verify it fails**

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*RunRepositoryIT' --rerun-tasks
```
Expected: FAIL to compile (method missing).

- [ ] **Step 4: Implement in `JooqRunRepository`** (mirror `findByProject` + the keyset predicate from `JooqSampleRepository.findByProjectPaged`)

```java
    @Override
    public List<RunRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(RUNS).where(RUNS.PROJECT_ID.eq(projectId));
        if (afterAt != null) {
            q = q.and(RUNS.CREATED_AT.lt(afterAt)
                    .or(RUNS.CREATED_AT.eq(afterAt).and(RUNS.ID.lt(afterId))));
        }
        Result<RunsRecord> records = q.orderBy(RUNS.CREATED_AT.desc(), RUNS.ID.desc()).limit(limit).fetch();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> sourcesByRun = dsl.select(RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID)
                .from(RUN_SOURCES)
                .where(RUN_SOURCES.RUN_ID.in(records.map(RunsRecord::getId)))
                .orderBy(RUN_SOURCES.DATA_SOURCE_ID.asc())
                .fetchGroups(RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID);
        return records.map(r -> map(r, sourcesByRun.getOrDefault(r.getId(), List.of())));
    }
```
> `map(RunsRecord, List<String>)` already exists (used by `findByProject`); reuse it. Confirm imports `org.jooq.Result` + `RunsRecord` are present (they are, from `findByProject`).

- [ ] **Step 5: Run to verify it passes**

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*RunRepositoryIT' --rerun-tasks
```
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add persistence/src/main/java/com/ainclusive/iotsim/persistence/run \
        persistence/src/test/java/com/ainclusive/iotsim/persistence/run/RunRepositoryIT.java
git commit -m "feat(IS-089): RunRepository.findByProjectPaged (cursor pagination)"
```

---

## Task 2: Thread `trigger`/`initiator` into Replay & Synthetic

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java`, `.../synthetic/SyntheticRunService.java`
- Test: `domain/src/test/.../replay/ReplayServiceTest.java`, `.../synthetic/SyntheticRunServiceTest.java`

**Interfaces:**
- Produces:
  - `ReplayService.replay(String projectId, String dataSourceId, String recordingId, DeterministicSettings settings, boolean compatibilityAck, String trigger, String initiator, String parentRunId) -> ReplaySummary` — the full overload; existing 5-arg and 6-arg (`…, parentRunId`) delegate with `trigger="MANUAL"`, `initiator="local"`.
  - `SyntheticRunService.run(String projectId, String dataSourceId, long durationMs, String trigger, String initiator, String parentRunId) -> SyntheticRunSummary` — full overload; existing 3-arg and 4-arg delegate with `"MANUAL"`/`"local"`.

- [ ] **Step 1: Write failing threading tests**

`ReplayServiceTest.java` — the `FakeRuns` records the created run's trigger/initiator (add `triggerOf(runId)`/`initiatorOf(runId)` accessors reading its stored `RunRow`):
```java
    @Test
    void replayWithAutomationTriggerAndInitiator() {
        ReplayService service = replayService(List.of(sampleValue()));
        ReplaySummary s = service.replay(PROJECT, SOURCE, RECORDING, null, true, "AUTOMATION", "ci-bot", null);
        assertThat(runs.triggerOf(s.runId())).isEqualTo("AUTOMATION");
        assertThat(runs.initiatorOf(s.runId())).isEqualTo("ci-bot");
    }
```
`SyntheticRunServiceTest.java` (its `FakeRuns` similarly exposes trigger/initiator):
```java
    @Test
    void runWithAutomationTriggerAndInitiator() {
        // same happy-path stubs as the existing success test, then:
        service.run(PROJECT, SOURCE, 1000L, "AUTOMATION", "ci-bot", null);
        // assert the created run row carries trigger=AUTOMATION, initiator=ci-bot
        // (via the fake's stored RunRow, mirroring the existing parentRunId test).
    }
```
(Add the `triggerOf`/`initiatorOf` accessors — or an assertion on the stored `RunRow` — to each test's `FakeRuns`, matching how the IS-086 `parentRunId` test inspects the stored run.)

- [ ] **Step 2: Run to verify they fail**

`./gradlew :domain:test --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest'` → FAIL to compile (overload arity).

- [ ] **Step 3: Add the overloads**

`ReplayService.java` — replace the current 5-arg/6-arg with delegating stubs and put the body in the 8-arg:
```java
    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck) {
        return replay(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck, null);
    }

    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck, String parentRunId) {
        return replay(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck,
                "MANUAL", "local", parentRunId);
    }

    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck,
            String trigger, String initiator, String parentRunId) {
        // ... existing body, EXCEPT the run creation line:
        RunRow run = runs.create(projectId, "REPLAY", trigger, initiator, List.of(dataSourceId), null, parentRunId);
        // ... rest unchanged
    }
```
`SyntheticRunService.java` — same shape:
```java
    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs) {
        return run(projectId, dataSourceId, durationMs, null);
    }

    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs, String parentRunId) {
        return run(projectId, dataSourceId, durationMs, "MANUAL", "local", parentRunId);
    }

    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs,
            String trigger, String initiator, String parentRunId) {
        // ... existing body, EXCEPT the run creation line:
        RunRow run = runs.create(projectId, "SYNTHETIC", trigger, initiator, List.of(dataSourceId), null, parentRunId);
        // ... rest unchanged
    }
```

- [ ] **Step 4: Run to verify they pass**

`./gradlew :domain:test --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest'` → PASS (existing tests still green).

- [ ] **Step 5: Commit**
```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/replay/ReplayServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java
git commit -m "feat(IS-089): thread trigger/initiator through Replay/Synthetic run services"
```

---

## Task 3: `RunService` (RunView / RunState / listPaged / get / stateOf / stop / start)

**Files:**
- Create: `domain/.../run/RunView.java`, `RunState.java`, `SourceState.java`, `RunService.java`
- Test: `domain/src/test/.../run/RunServiceTest.java`

**Interfaces:**
- Consumes: `RunRepository` (findById/findByProjectPaged/end); `RuntimeController.health(sourceId)` → `SourceHealth(state, SourceError(origin,reason,at))` + `.stop(sourceId)`; `DataSourceRepository.findByProject`; `ScenarioRepository.findById`; `ReplayService`/`SyntheticRunService`/`ScenarioRunService`; `Page`/`PageCursor`; `ResourceNotFoundException`.
- Produces:
  - `record RunView(String id, String projectId, String kind, String trigger, String initiator, String state, String scenarioId, String evidenceId, String parentRunId, List<String> sourceIds, Instant startedAt, Instant endedAt, Instant createdAt, String label, String relatedLabel)`
  - `record SourceState(String sourceId, String state, String lastError)`
  - `record RunState(String runState, List<SourceState> sources)`
  - `record StartRunCommand(String kind, String initiator, String dataSourceId, String recordingId, Long durationMs, String scenarioId, Long seed, String startTime, Boolean compatibilityAck)`
  - `RunService`: `Page<RunView> listPaged(projectId, cursor, Integer limit)`, `RunView get(projectId, id)`, `RunState stateOf(projectId, id)`, `RunView stop(projectId, id)`, `RunView start(projectId, StartRunCommand cmd)`.

- [ ] **Step 1: Write the records**

`RunView.java`, `SourceState.java`, `RunState.java`, `StartRunCommand.java` — exactly the record signatures listed above (package `com.ainclusive.iotsim.domain.run`; `RunView`/`RunState`/`SourceState` import `java.time.Instant`/`java.util.List`).

- [ ] **Step 2: Write the failing service test** (`RunServiceTest.java`, Mockito)

```java
package com.ainclusive.iotsim.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunServiceTest {

    private static final String PROJECT = "p1";

    private RunRepository runs;
    private DataSourceRepository dataSources;
    private ScenarioRepository scenarios;
    private RuntimeController runtime;
    private ReplayService replay;
    private SyntheticRunService synthetic;
    private ScenarioRunService scenarioRun;
    private RunService service;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        dataSources = mock(DataSourceRepository.class);
        scenarios = mock(ScenarioRepository.class);
        runtime = mock(RuntimeController.class);
        replay = mock(ReplayService.class);
        synthetic = mock(SyntheticRunService.class);
        scenarioRun = mock(ScenarioRunService.class);
        when(dataSources.findByProject(PROJECT)).thenReturn(List.of());
        service = new RunService(runs, dataSources, scenarios, runtime, replay, synthetic, scenarioRun);
    }

    private RunRow row(String id, String kind, String state, List<String> sources) {
        return new RunRow(id, PROJECT, kind, "MANUAL", "local", state, null, null,
                OffsetDateTime.now(), null, OffsetDateTime.now(), sources, null);
    }

    @Test
    void getMissingOrWrongProjectThrows404() {
        when(runs.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(PROJECT, "nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void stateOfAggregatesRunStateAndPerSourceHealth() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "RUNNING", List.of("ds1"))));
        when(runtime.health("ds1")).thenReturn(new SourceHealth("RUNNING", null));
        RunState st = service.stateOf(PROJECT, "r1");
        assertThat(st.runState()).isEqualTo("RUNNING");
        assertThat(st.sources()).singleElement().satisfies(s -> {
            assertThat(s.sourceId()).isEqualTo("ds1");
            assertThat(s.state()).isEqualTo("RUNNING");
        });
    }

    @Test
    void stopStopsSourcesAndEndsRunWhenNonTerminal() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "RUNNING", List.of("ds1", "ds2"))));
        when(runs.end(eq("r1"), eq("STOPPED"), any())).thenReturn(row("r1", "REPLAY", "STOPPED", List.of("ds1", "ds2")));
        RunView v = service.stop(PROJECT, "r1");
        verify(runtime).stop("ds1");
        verify(runtime).stop("ds2");
        verify(runs).end(eq("r1"), eq("STOPPED"), any());
        assertThat(v.state()).isEqualTo("STOPPED");
    }

    @Test
    void stopTerminalRunStopsSourcesButDoesNotReEnd() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "COMPLETED", List.of("ds1"))));
        service.stop(PROJECT, "r1");
        verify(runtime).stop("ds1");
        org.mockito.Mockito.verify(runs, org.mockito.Mockito.never()).end(any(), any(), any());
    }

    @Test
    void startReplayRoutesWithAutomationTriggerAndInitiator() {
        when(replay.replay(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot"), eq((String) null)))
                .thenReturn(new ReplaySummary("rec1", "ds1", 5, "run-r", "ev", null));
        when(runs.findById("run-r")).thenReturn(Optional.of(row("run-r", "REPLAY", "COMPLETED", List.of("ds1"))));
        RunView v = service.start(PROJECT, new StartRunCommand("REPLAY", "ci-bot", "ds1", "rec1", null, null, null, null, false));
        assertThat(v.id()).isEqualTo("run-r");
        verify(replay).replay(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot"), eq((String) null));
    }

    @Test
    void startRejectsBlankInitiator() {
        assertThatThrownBy(() -> service.start(PROJECT, new StartRunCommand("REPLAY", "  ", "ds1", "rec1", null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startRejectsUnknownKind() {
        assertThatThrownBy(() -> service.start(PROJECT, new StartRunCommand("NOPE", "ci-bot", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```
> Confirm `ReplaySummary` constructor arity (`recordingId, dataSourceId, valueCount, runId, evidenceId, settings`) and adjust the stub literal. `SyntheticRunSummary`/`ScenarioRunSummary` likewise if you add their routing tests.

- [ ] **Step 3: Run to verify it fails**

`./gradlew :domain:test --tests '*RunServiceTest'` → FAIL to compile (`RunService` missing).

- [ ] **Step 4: Write `RunService`**

```java
package com.ainclusive.iotsim.domain.run;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Unified runs resource + test-control (IS-089). Read/list/state/stop, and a single
 *  automation-facing start that routes to the replay/synthetic/scenario services. */
@Service
public class RunService {

    private static final Set<String> TERMINAL = Set.of("STOPPED", "FAILED", "COMPLETED");

    private final RunRepository runs;
    private final DataSourceRepository dataSources;
    private final ScenarioRepository scenarios;
    private final RuntimeController runtime;
    private final ReplayService replay;
    private final SyntheticRunService synthetic;
    private final ScenarioRunService scenarioRun;

    public RunService(RunRepository runs, DataSourceRepository dataSources, ScenarioRepository scenarios,
            RuntimeController runtime, ReplayService replay, SyntheticRunService synthetic,
            ScenarioRunService scenarioRun) {
        this.runs = runs;
        this.dataSources = dataSources;
        this.scenarios = scenarios;
        this.runtime = runtime;
        this.replay = replay;
        this.synthetic = synthetic;
        this.scenarioRun = scenarioRun;
    }

    public Page<RunView> listPaged(String projectId, String cursor, Integer limit) {
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<RunRow> rows = runs.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            RunRow lastRow = rows.get(rows.size() - 1);
            next = PageCursor.encode(lastRow.createdAt(), lastRow.id());
        }
        Map<String, String> sourceNames = sourceNames(projectId);
        return new Page<>(rows.stream().map(r -> view(r, sourceNames)).toList(), next, size);
    }

    public RunView get(String projectId, String id) {
        return view(require(projectId, id), sourceNames(projectId));
    }

    public RunState stateOf(String projectId, String id) {
        RunRow run = require(projectId, id);
        List<SourceState> sources = run.sourceIds().stream().map(sid -> {
            SourceHealth h = runtime.health(sid);
            String lastError = h != null && h.lastError() != null ? h.lastError().reason() : null;
            return new SourceState(sid, h != null ? h.state() : "STOPPED", lastError);
        }).toList();
        return new RunState(run.state(), sources);
    }

    public RunView stop(String projectId, String id) {
        RunRow run = require(projectId, id);
        run.sourceIds().forEach(runtime::stop);
        RunRow after = TERMINAL.contains(run.state())
                ? run
                : runs.end(id, "STOPPED", OffsetDateTime.now(ZoneOffset.UTC));
        return view(after, sourceNames(projectId));
    }

    public RunView start(String projectId, StartRunCommand cmd) {
        String initiator = cmd.initiator();
        if (initiator == null || initiator.isBlank()) {
            throw new IllegalArgumentException("initiator is required for an automation run");
        }
        String runId = switch (cmd.kind() == null ? "" : cmd.kind()) {
            case "REPLAY" -> {
                require(cmd.dataSourceId(), "dataSourceId");
                require(cmd.recordingId(), "recordingId");
                DeterministicSettings settings = cmd.seed() != null
                        ? new DeterministicSettings(cmd.seed(),
                                cmd.startTime() != null ? Instant.parse(cmd.startTime()) : Instant.now())
                        : null;
                yield replay.replay(projectId, cmd.dataSourceId(), cmd.recordingId(), settings,
                        Boolean.TRUE.equals(cmd.compatibilityAck()), "AUTOMATION", initiator, null).runId();
            }
            case "SYNTHETIC" -> {
                require(cmd.dataSourceId(), "dataSourceId");
                if (cmd.durationMs() == null || cmd.durationMs() <= 0) {
                    throw new IllegalArgumentException("durationMs must be > 0 for a SYNTHETIC run");
                }
                yield synthetic.run(projectId, cmd.dataSourceId(), cmd.durationMs(), "AUTOMATION", initiator, null).runId();
            }
            case "SCENARIO" -> {
                require(cmd.scenarioId(), "scenarioId");
                yield scenarioRun.run(projectId, cmd.scenarioId(), "AUTOMATION", initiator).runId();
            }
            default -> throw new IllegalArgumentException("unknown run kind: " + cmd.kind());
        };
        return view(require(projectId, runId), sourceNames(projectId));
    }

    // ---- helpers ----

    private RunRow require(String projectId, String id) {
        return runs.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Run", id));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private Map<String, String> sourceNames(String projectId) {
        return dataSources.findByProject(projectId).stream()
                .collect(Collectors.toMap(DataSourceRow::id, DataSourceRow::name));
    }

    private RunView view(RunRow r, Map<String, String> sourceNames) {
        String relatedLabel = null;
        if ("SCENARIO".equals(r.kind()) && r.scenarioId() != null) {
            relatedLabel = scenarios.findById(r.scenarioId()).map(s -> s.name()).orElse(null);
        } else if (!r.sourceIds().isEmpty()) {
            relatedLabel = sourceNames.get(r.sourceIds().get(0));
        }
        String label = relatedLabel != null ? relatedLabel + " " + r.kind().toLowerCase() : r.kind();
        return new RunView(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(), r.state(),
                r.scenarioId(), r.evidenceId(), r.parentRunId(), r.sourceIds(),
                instant(r.startedAt()), instant(r.endedAt()), instant(r.createdAt()), label, relatedLabel);
    }

    private static Instant instant(OffsetDateTime t) {
        return t != null ? t.toInstant() : null;
    }
}
```
> `view(...)` mirrors `ActiveRunService`'s labeling (kept separate; a future refactor could extract a shared labeler). `stateOf` per-source `runtime.health` is O(n); fine. Confirm `ScenarioRow` name accessor is `name()`.

- [ ] **Step 5: Run to verify it passes**

`./gradlew :domain:test --tests '*RunServiceTest'` → PASS.

- [ ] **Step 6: Commit**
```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/run \
        domain/src/test/java/com/ainclusive/iotsim/domain/run/RunServiceTest.java
git commit -m "feat(IS-089): RunService (list/get/state/stop/start routing)"
```

---

## Task 4: `RunController` + DTOs

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/run/RunController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/run/RunControllerTest.java`

**Interfaces:**
- Consumes: `RunService` (Task 3); `Page`; `Permission` (OBSERVE/REPLAY_START/REPLAY_STOP).
- Produces: `/api/v1/projects/{projectId}/runs` (GET, POST), `/runs/{id}` (GET), `/runs/{id}/state` (GET), `/runs/{id}/stop` (POST).

- [ ] **Step 1: Write the controller** (mirror `ScenarioController`'s `@PreAuthorize` constant + Page mapping style)

```java
package com.ainclusive.iotsim.api.run;

import com.ainclusive.iotsim.domain.run.RunService;
import com.ainclusive.iotsim.domain.run.RunState;
import com.ainclusive.iotsim.domain.run.RunView;
import com.ainclusive.iotsim.domain.run.SourceState;
import com.ainclusive.iotsim.domain.run.StartRunCommand;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified runs resource + test-control for automation (backend-specs/05, IS-089). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/runs")
public class RunController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";
    private static final String REPLAY_STOP =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_STOP)";

    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<RunResponse> list(@PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return runs.listPaged(projectId, cursor, limit).map(RunResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public RunResponse get(@PathVariable String projectId, @PathVariable String id) {
        return RunResponse.from(runs.get(projectId, id));
    }

    @GetMapping("/{id}/state")
    @PreAuthorize(OBSERVE)
    public RunStateResponse state(@PathVariable String projectId, @PathVariable String id) {
        return RunStateResponse.from(runs.stateOf(projectId, id));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize(REPLAY_STOP)
    public RunResponse stop(@PathVariable String projectId, @PathVariable String id) {
        return RunResponse.from(runs.stop(projectId, id));
    }

    @PostMapping
    @PreAuthorize(REPLAY_START)
    public ResponseEntity<RunResponse> start(@PathVariable String projectId, @RequestBody StartRunRequest req) {
        if (req == null || req.kind() == null || req.kind().isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        RunView v = runs.start(projectId, new StartRunCommand(req.kind(), req.initiator(),
                req.dataSourceId(), req.recordingId(), req.durationMs(), req.scenarioId(),
                req.seed(), req.startTime(), req.compatibilityAck()));
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/runs/" + v.id()))
                .body(RunResponse.from(v));
    }

    public record StartRunRequest(String kind, String initiator, String dataSourceId, String recordingId,
            Long durationMs, String scenarioId, Long seed, String startTime, Boolean compatibilityAck) {}

    public record RunResponse(String id, String projectId, String kind, String trigger, String initiator,
            String state, String scenarioId, String evidenceId, String parentRunId, List<String> sourceIds,
            Instant startedAt, Instant endedAt, Instant createdAt, String label, String relatedLabel) {
        static RunResponse from(RunView v) {
            return new RunResponse(v.id(), v.projectId(), v.kind(), v.trigger(), v.initiator(), v.state(),
                    v.scenarioId(), v.evidenceId(), v.parentRunId(), v.sourceIds(),
                    v.startedAt(), v.endedAt(), v.createdAt(), v.label(), v.relatedLabel());
        }
    }

    public record SourceStateResponse(String sourceId, String state, String lastError) {
        static SourceStateResponse from(SourceState s) {
            return new SourceStateResponse(s.sourceId(), s.state(), s.lastError());
        }
    }

    public record RunStateResponse(String runState, List<SourceStateResponse> sources) {
        static RunStateResponse from(RunState st) {
            return new RunStateResponse(st.runState(), st.sources().stream().map(SourceStateResponse::from).toList());
        }
    }
}
```

- [ ] **Step 2: Write the POJO controller test** (`RunControllerTest.java`, capturing `FakeService extends RunService`)

```java
package com.ainclusive.iotsim.api.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.api.run.RunController.RunResponse;
import com.ainclusive.iotsim.api.run.RunController.RunStateResponse;
import com.ainclusive.iotsim.api.run.RunController.StartRunRequest;
import com.ainclusive.iotsim.domain.run.RunService;
import com.ainclusive.iotsim.domain.run.RunState;
import com.ainclusive.iotsim.domain.run.RunView;
import com.ainclusive.iotsim.domain.run.SourceState;
import com.ainclusive.iotsim.domain.run.StartRunCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RunControllerTest {

    private static RunView view(String id, String state) {
        return new RunView(id, "p1", "REPLAY", "AUTOMATION", "ci-bot", state, null, "ev", null,
                List.of("ds1"), Instant.EPOCH, null, Instant.EPOCH, "src replay", "src");
    }

    private static final class FakeService extends RunService {
        String startedKind;
        FakeService() { super(null, null, null, null, null, null, null); }
        @Override public RunView get(String p, String id) { return view(id, "RUNNING"); }
        @Override public RunState stateOf(String p, String id) {
            return new RunState("RUNNING", List.of(new SourceState("ds1", "RUNNING", null)));
        }
        @Override public RunView stop(String p, String id) { return view(id, "STOPPED"); }
        @Override public RunView start(String p, StartRunCommand cmd) { this.startedKind = cmd.kind(); return view("run-new", "COMPLETED"); }
    }

    @Test
    void getMapsRun() {
        RunResponse r = new RunController(new FakeService()).get("p1", "r1");
        assertThat(r.id()).isEqualTo("r1");
        assertThat(r.trigger()).isEqualTo("AUTOMATION");
    }

    @Test
    void stateMapsRunAndSources() {
        RunStateResponse r = new RunController(new FakeService()).state("p1", "r1");
        assertThat(r.runState()).isEqualTo("RUNNING");
        assertThat(r.sources()).singleElement().satisfies(s -> assertThat(s.sourceId()).isEqualTo("ds1"));
    }

    @Test
    void stopMapsStoppedRun() {
        assertThat(new RunController(new FakeService()).stop("p1", "r1").state()).isEqualTo("STOPPED");
    }

    @Test
    void startReturns201AndRoutesKind() {
        FakeService svc = new FakeService();
        ResponseEntity<RunResponse> resp = new RunController(svc).start("p1",
                new StartRunRequest("REPLAY", "ci-bot", "ds1", "rec1", null, null, null, null, false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/runs/run-new");
        assertThat(svc.startedKind).isEqualTo("REPLAY");
    }

    @Test
    void startWithBlankKindThrows() {
        assertThatThrownBy(() -> new RunController(new FakeService()).start("p1",
                new StartRunRequest("  ", "ci-bot", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```
> `FakeService extends RunService` requires `RunService` and its methods non-`final` (they are). The `super(null×7)` ctor call needs the persistence types on the api test classpath — `api` already added `testImplementation(project(":persistence"))` in IS-085 for exactly this pattern; if a compile error says a persistence/platform type isn't resolvable, add `testImplementation(project(":platform"))` too.

- [ ] **Step 3: Run the controller test**

`./gradlew :api:test --tests '*RunControllerTest'` → PASS (5 tests). Also `./gradlew :api:checkstyleTest -q` (no unused imports).

- [ ] **Step 4: Commit**
```bash
git add api/src/main/java/com/ainclusive/iotsim/api/run \
        api/src/test/java/com/ainclusive/iotsim/api/run/RunControllerTest.java
git commit -m "feat(IS-089): RunController (/runs list/get/state/stop/start)"
```

---

## Task 5: Full verification

- [ ] **Step 1: Full build**
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build --rerun-tasks
```
Expected: BUILD SUCCESSFUL — `RunRepositoryIT`, `RunServiceTest`, `RunControllerTest`, the Replay/Synthetic threading tests, and the app context-boot smoke (RunController/RunService beans wire) all pass. If a checkstyle `UnusedImports` fails, remove the import and re-run (subagent `test --tests` runs don't run checkstyle).

- [ ] **Step 2: Open the PR** — `/open-pr` (flip `IS-089` catalog `[x]`; `Implements: IS-089`; arm auto-merge; board → In review).

---

## Self-Review

**Spec coverage:** GET /runs (paged) → T1+T3+T4; GET /runs/{id} → T3+T4; GET /state → T3+T4; POST /stop (sources+STOPPED) → T3+T4; POST /runs (3 kinds + AUTOMATION/initiator) → T2(threading)+T3(routing)+T4(endpoint); pagination → T1; permissions → T4; enrich/label → T3. ✅

**Placeholder scan:** No TBD/TODO. The "confirm ReplaySummary/ScenarioRow accessor" and "add testImplementation(platform) if needed" notes are explicit verify-or-adjust instructions.

**Type consistency:** `RunView`/`RunState`/`SourceState`/`StartRunCommand` records (T3) are used identically by `RunService` (T3) and `RunController` (T4). `RunRepository.findByProjectPaged` (T1) is consumed by `RunService.listPaged` (T3). The Replay/Synthetic 8-/6-arg overloads (T2: `replay(...,trigger,initiator,parentRunId)`, `run(...,trigger,initiator,parentRunId)`) match `RunService.start`'s calls (T3) and the `RunServiceTest` stubs. `RunRow` 13-field constructor (incl. trailing `parentRunId`) used consistently in test rows. `SourceHealth(state, SourceError(origin,reason,at))` → `SourceState(sourceId, state, lastError=reason)` in `stateOf`. ✅
