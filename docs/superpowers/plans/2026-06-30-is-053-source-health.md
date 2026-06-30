# IS-053 Source Health & Error Surfacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface per-source health transitions (STALE / RECOVERED / ERROR) live on the runtime SSE stream and in `runtime_events`, attribute each error to an `origin`, and expose a point-in-time `GET /api/v1/data-sources/{id}/health`.

**Architecture:** The supervisor already computes per-source state but emits nothing on its own health transitions. We make `Supervisor.ManagedWorker` track a `lastError` and emit a `RuntimeActivityEvent` on each real transition; that rides the existing rail — `LiveEventHub` streams it and `PersistingRuntimeActivityListener` persists it (carrying a new `origin` in the existing `payload` jsonb, no migration). A new `SourceHealth` read model + a `RuntimeController.health()` default method back the connect snapshot and the new REST endpoint.

**Tech Stack:** Java 21 records, Spring Boot (Web MVC), JUnit 5 + AssertJ + Mockito, Jackson 3 (`tools.jackson.*`), Gradle multi-module.

## Global Constraints

- Jackson is 3.x: import `tools.jackson.*` (never `com.fasterxml.jackson.*`); catch unchecked `JacksonException`.
- API stays at `/api/v1`; do not introduce `/api/v2`.
- Module boundaries are enforced by ArchUnit: `domain`/`api` may depend on `platform`; `platform` depends on neither. New platform types (`HealthOrigin`, `SourceError`, `SourceHealth`) live in `com.ainclusive.iotsim.platform.runtime`.
- After every task, `./gradlew build` must be green, including Spotless, Checkstyle and ArchUnit. (These unit/`@WebMvcTest` tests need no Docker.)
- `runtime_events.payload` is jsonb; `origin` goes into that payload — **no Flyway migration**.
- Add/update tests for every change. TDD: failing test first.

---

### Task 1: `HealthOrigin` enum + `RuntimeActivityEvent.origin`

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/HealthOrigin.java`
- Modify: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEvent.java`
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEventTest.java`

**Interfaces:**
- Produces: `enum HealthOrigin { SIMULATOR, PROTOCOL, UNKNOWN }`; `RuntimeActivityEvent(String dataSourceId, String type, Instant at, String detail, HealthOrigin origin)` (canonical) plus a back-compat `RuntimeActivityEvent(String, String, Instant, String)` that defaults `origin` to `null`. Accessor `origin()`.

- [ ] **Step 1: Write the failing test** — append to `RuntimeActivityEventTest`:

```java
@Test
void fourArgConstructorDefaultsOriginToNull() {
    RuntimeActivityEvent e =
            new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null);
    assertThat(e.origin()).isNull();
}

@Test
void carriesExplicitOrigin() {
    RuntimeActivityEvent e = new RuntimeActivityEvent(
            "ds1", "SOURCE_STALE", Instant.ofEpochSecond(5), "no health response",
            HealthOrigin.SIMULATOR);
    assertThat(e.origin()).isEqualTo(HealthOrigin.SIMULATOR);
}
```

Add the import `import static org.assertj.core.api.Assertions.assertThat;` if not present (it is used by the file already).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.RuntimeActivityEventTest"`
Expected: COMPILE FAILURE — `HealthOrigin` does not exist / no 5-arg constructor.

- [ ] **Step 3: Create `HealthOrigin`**

```java
package com.ainclusive.iotsim.platform.runtime;

/**
 * Where a data-source health problem originates, as far as the supervisor can
 * reliably tell. {@code SIMULATOR} = the supervisor's own monitoring detected it
 * (unresponsive worker, process exit, restart budget exhausted); {@code PROTOCOL}
 * = the worker reported it over the runtime-events stream; {@code UNKNOWN} =
 * fallback. Finer attribution (source configuration vs Edge Device) is carried in
 * the human-readable reason, not as a distinct value.
 *
 * <p>See SPEC.md → Observe Data Source Health And Errors.
 */
public enum HealthOrigin {
    SIMULATOR,
    PROTOCOL,
    UNKNOWN
}
```

- [ ] **Step 4: Extend `RuntimeActivityEvent`** — add the `origin` component and a back-compat constructor. Update the class javadoc's last paragraph to mention origin:

```java
public record RuntimeActivityEvent(
        String dataSourceId, String type, Instant at, String detail, HealthOrigin origin) {

    public RuntimeActivityEvent {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }

    /**
     * A non-health/error event with no origin attribution (origin {@code null}).
     * Worker lifecycle events (SOURCE_START/STOP) use this form.
     */
    public RuntimeActivityEvent(String dataSourceId, String type, Instant at, String detail) {
        this(dataSourceId, type, at, detail, null);
    }
}
```

In the class javadoc, extend the `type`/`detail` paragraph with: `{@code origin} attributes health/error events ({@code SIMULATOR} for supervisor-detected, {@code PROTOCOL} for worker-reported); it is {@code null} for non-health events.`

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.RuntimeActivityEventTest"`
Expected: PASS. All existing 4-arg call sites still compile via the back-compat constructor.

- [ ] **Step 6: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/HealthOrigin.java \
        platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEvent.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEventTest.java
git commit -m "feat(observ): IS-053 add HealthOrigin + RuntimeActivityEvent.origin

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `SourceError`, `SourceHealth`, `RuntimeController.health()`

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/SourceError.java`
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/SourceHealth.java`
- Modify: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeController.java`
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/InMemoryRuntimeControllerTest.java`

**Interfaces:**
- Consumes: `HealthOrigin` (Task 1).
- Produces: `record SourceError(HealthOrigin origin, String reason, Instant at)`; `record SourceHealth(String state, SourceError lastError)`; `RuntimeController.health(String dataSourceId)` returning `SourceHealth`, with a default that returns `new SourceHealth(state(dataSourceId), null)`.

- [ ] **Step 1: Write the failing test** — create `InMemoryRuntimeControllerTest`:

```java
package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryRuntimeControllerTest {

    private static RuntimeStartSpec spec() {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(), 0);
    }

    @Test
    void healthReportsRunningStateWithNoError() {
        InMemoryRuntimeController rc = new InMemoryRuntimeController();
        rc.start("d1", spec());

        SourceHealth h = rc.health("d1");

        assertThat(h.state()).isEqualTo("RUNNING");
        assertThat(h.lastError()).isNull();
    }

    @Test
    void healthForUnknownSourceIsStoppedWithNoError() {
        InMemoryRuntimeController rc = new InMemoryRuntimeController();

        SourceHealth h = rc.health("unknown");

        assertThat(h.state()).isEqualTo("STOPPED");
        assertThat(h.lastError()).isNull();
    }
}
```

(The unused `NeutralValue` import is removed; keep only what compiles — `List`, the records, AssertJ. If Checkstyle flags unused imports, drop `NeutralValue`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeControllerTest"`
Expected: COMPILE FAILURE — `SourceHealth` / `health` do not exist.

- [ ] **Step 3: Create `SourceError`**

```java
package com.ainclusive.iotsim.platform.runtime;

import java.time.Instant;

/**
 * The most recent error observed for a data source: where it came from
 * ({@link HealthOrigin}), a human-readable {@code reason}, and the {@code at}
 * instant it was observed.
 */
public record SourceError(HealthOrigin origin, String reason, Instant at) {}
```

- [ ] **Step 4: Create `SourceHealth`**

```java
package com.ainclusive.iotsim.platform.runtime;

/**
 * Point-in-time health of a data source: its current runtime {@code state}
 * (RUNNING/STARTING/STALE/ERROR/STOPPED, as {@link RuntimeController#state}) plus
 * the most recent {@link SourceError}. {@code lastError} is retained even after
 * recovery (so callers can show "last error was …") and is {@code null} when no
 * error has occurred.
 */
public record SourceHealth(String state, SourceError lastError) {}
```

- [ ] **Step 5: Add the default method to `RuntimeController`** (after `state(...)`):

```java
    /**
     * Current health: the runtime {@link #state} plus the most recent error, if
     * any. The default reports {@code state} with no error detail; the runtime
     * supervisor overrides it to surface staleness/exit reasons.
     */
    default SourceHealth health(String dataSourceId) {
        return new SourceHealth(state(dataSourceId), null);
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeControllerTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/SourceError.java \
        platform/src/main/java/com/ainclusive/iotsim/platform/runtime/SourceHealth.java \
        platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeController.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/InMemoryRuntimeControllerTest.java
git commit -m "feat(observ): IS-053 SourceHealth read model + RuntimeController.health()

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Supervisor emits health transitions + tracks lastError

**Files:**
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorHealthTest.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorRestartTest.java`

**Interfaces:**
- Consumes: `HealthOrigin`, `SourceError`, `SourceHealth`, `RuntimeActivityEvent` (Tasks 1–2); existing `runtimeActivityListener` field; existing `WorkerState`, `ManagedWorker`.
- Produces: `Supervisor.health(String)` override; emitted `RuntimeActivityEvent`s of type `SOURCE_STALE` / `SOURCE_RECOVERED` / `SOURCE_ERROR` (origin `SIMULATOR`); worker-reported `ERROR` mapped to origin `PROTOCOL`.

- [ ] **Step 1: Write the failing tests (stale/recover)** — append to `SupervisorHealthTest`. Add imports at top:

```java
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.util.concurrent.CopyOnWriteArrayList;
```

Test methods:

```java
@Test
void staleTransitionEmitsHealthEventWithSimulatorOrigin() {
    List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
    supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
            ClientActivityListener.NONE, events::add);
    supervisor.start("ds1", spec());

    launcher.last().service().setHealthLive(false);

    await(() -> events.stream().anyMatch(e -> e.type().equals("SOURCE_STALE")));
    RuntimeActivityEvent stale = events.stream()
            .filter(e -> e.type().equals("SOURCE_STALE")).findFirst().orElseThrow();
    assertThat(stale.origin()).isEqualTo(HealthOrigin.SIMULATOR);
    assertThat(stale.dataSourceId()).isEqualTo("ds1");

    SourceHealth h = supervisor.health("ds1");
    assertThat(h.state()).isEqualTo("STALE");
    assertThat(h.lastError()).isNotNull();
    assertThat(h.lastError().origin()).isEqualTo(HealthOrigin.SIMULATOR);
}

@Test
void recoveryEmitsRecoveredEventAndRetainsLastError() {
    List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
    supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
            ClientActivityListener.NONE, events::add);
    supervisor.start("ds1", spec());

    launcher.last().service().setHealthLive(false);
    await(() -> "STALE".equals(supervisor.state("ds1")));
    launcher.last().service().setHealthLive(true);

    await(() -> events.stream().anyMatch(e -> e.type().equals("SOURCE_RECOVERED")));
    SourceHealth h = supervisor.health("ds1");
    assertThat(h.state()).isEqualTo("RUNNING");
    assertThat(h.lastError()).isNotNull(); // retained after recovery
}

@Test
void staleEventIsEmittedOnlyOncePerTransition() {
    List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
    supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
            ClientActivityListener.NONE, events::add);
    supervisor.start("ds1", spec());

    launcher.last().service().setHealthLive(false);
    await(() -> "STALE".equals(supervisor.state("ds1")));
    sleep(300); // several more poll cycles while still unhealthy

    long staleCount = events.stream().filter(e -> e.type().equals("SOURCE_STALE")).count();
    assertThat(staleCount).isEqualTo(1);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorHealthTest"`
Expected: COMPILE FAILURE (no `health()` / `origin()` semantics) then, once compiling, FAIL — no `SOURCE_STALE` event is ever emitted.

- [ ] **Step 3: Add platform imports to `Supervisor.java`** (with the other `platform.runtime` imports):

```java
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
```

- [ ] **Step 4: Add `lastError` field + `health()` override + emit helper.** In `ManagedWorker`, add beside the other volatile fields:

```java
        private volatile SourceError lastError;
```

Add the emit helper inside `ManagedWorker` (it reads the enclosing `runtimeActivityListener`):

```java
        /** Emits a supervisor-detected health transition; call OUTSIDE the monitor. */
        private void emitHealthEvent(String type, String reason) {
            runtimeActivityListener.onRuntimeActivity(new RuntimeActivityEvent(
                    dataSourceId, type, Instant.now(), reason, HealthOrigin.SIMULATOR));
        }
```

Add the `RuntimeController.health` override on `Supervisor` (next to `state(...)`):

```java
    @Override
    public SourceHealth health(String dataSourceId) {
        ManagedWorker worker = running.get(dataSourceId);
        return worker == null
                ? new SourceHealth(STOPPED, null)
                : new SourceHealth(worker.stateName(), worker.lastError);
    }
```

- [ ] **Step 5: Emit on stale/recover in `probeHealth()`.** Replace the body of `probeHealth()` with:

```java
        void probeHealth() {
            WorkerClient current;
            synchronized (this) {
                if (stopping || state != WorkerState.RUNNING || client == null) {
                    return;
                }
                current = client;
            }
            boolean healthy = current.isHealthy(healthPolicy.probeTimeout());
            boolean becameStale = false;
            boolean recovered = false;
            String reason = null;
            synchronized (this) {
                if (stopping || state != WorkerState.RUNNING || client != current) {
                    return;
                }
                if (healthy) {
                    healthFailures = 0;
                    if (stale) {
                        stale = false;
                        recovered = true;
                    }
                } else if (++healthFailures >= healthPolicy.staleThreshold() && !stale) {
                    stale = true;
                    becameStale = true;
                    reason = "no health response in " + healthFailures + " consecutive probes";
                    lastError = new SourceError(HealthOrigin.SIMULATOR, reason, Instant.now());
                }
            }
            if (becameStale) {
                emitHealthEvent("SOURCE_STALE", reason);
            } else if (recovered) {
                emitHealthEvent("SOURCE_RECOVERED", "health restored");
            }
        }
```

- [ ] **Step 6: Run the stale/recover tests**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorHealthTest"`
Expected: PASS (all five existing + three new).

- [ ] **Step 7: Write the failing test (error on cap)** — append to `SupervisorRestartTest`. Add imports:

```java
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import java.util.concurrent.CopyOnWriteArrayList;
```

Test:

```java
@Test
void reachingRestartCapEmitsErrorHealthEventWithSimulatorOrigin() {
    List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
    supervisor = new Supervisor(launcher, fastPolicy, HealthPolicy.DEFAULT,
            ClientActivityListener.NONE, events::add);
    supervisor.start("ds1", spec());

    launcher.crashLast();
    awaitRunning(2);
    launcher.crashLast();
    awaitRunning(3);
    launcher.crashLast();

    await(() -> "ERROR".equals(supervisor.state("ds1")));
    await(() -> events.stream().anyMatch(e -> e.type().equals("SOURCE_ERROR")));
    RuntimeActivityEvent err = events.stream()
            .filter(e -> e.type().equals("SOURCE_ERROR")).findFirst().orElseThrow();
    assertThat(err.origin()).isEqualTo(HealthOrigin.SIMULATOR);
    assertThat(supervisor.health("ds1").lastError().origin()).isEqualTo(HealthOrigin.SIMULATOR);
}
```

- [ ] **Step 8: Run it to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorRestartTest"`
Expected: FAIL — no `SOURCE_ERROR` emitted.

- [ ] **Step 9: Make `scheduleRestart()` terminal-aware and emit from the two callers.** Change `scheduleRestart` to return whether it became terminal, and set `lastError`:

```java
        /**
         * Caller holds the monitor. Backs off and schedules a restart, or gives up
         * at the cap. Returns {@code true} if the worker reached a terminal
         * {@code EXITED} (ERROR) state with no further restart.
         */
        private boolean scheduleRestart() {
            if (restarts >= restartPolicy.maxRestarts()) {
                state = WorkerState.EXITED; // exhausted -> reported as ERROR
                lastError = new SourceError(HealthOrigin.SIMULATOR,
                        "restart budget exhausted after " + restarts + " attempts", Instant.now());
                return true;
            }
            restarts++;
            state = WorkerState.SPAWNED; // recovering -> reported as STARTING
            Duration delay = restartPolicy.backoffFor(restarts);
            try {
                pendingRestart = scheduler.schedule(
                        this::restart, delay.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                state = WorkerState.EXITED; // scheduler shutting down; nothing more to try
                lastError = new SourceError(HealthOrigin.SIMULATOR,
                        "supervisor shutting down", Instant.now());
                return true;
            }
            return false;
        }
```

Update `onWorkerExit` to compute the terminal flag under the lock and emit after:

```java
        /** Fires when a worker process exits — schedules a restart unless it was intentional. */
        private void onWorkerExit(LaunchedWorker exited) {
            boolean terminal;
            synchronized (this) {
                if (stopping || launched != exited) {
                    return;
                }
                // The process is gone; release its streams and channel before relaunching.
                cancelStreams();
                closeQuietly(client);
                client = null;
                launched = null;
                terminal = scheduleRestart();
            }
            if (terminal) {
                emitHealthEvent("SOURCE_ERROR",
                        lastError != null ? lastError.reason() : "worker exited");
            }
        }
```

Update the `restart()` catch block (the relaunch-failure path) to do the same:

```java
            } catch (RuntimeException e) {
                // Relaunch itself failed (or was interrupted by stop()); back off again
                // unless we are tearing down.
                boolean terminal = false;
                synchronized (this) {
                    if (!stopping) {
                        terminal = scheduleRestart();
                    }
                }
                if (terminal) {
                    emitHealthEvent("SOURCE_ERROR",
                            lastError != null ? lastError.reason() : "worker exited");
                }
                return;
            }
```

Note: `onWorkerExit` is no longer `synchronized` at the method level (the inner block holds the monitor). Confirm the method signature reads `private void onWorkerExit(LaunchedWorker exited)` with no `synchronized` keyword.

- [ ] **Step 10: Map worker-reported `ERROR` to PROTOCOL.** In `toRuntimeActivity`, set the origin:

```java
    private static RuntimeActivityEvent toRuntimeActivity(String dataSourceId, RuntimeEvent event) {
        String type = event.getType();
        if (type == null || type.isBlank()) {
            return null;
        }
        long micros = event.getAtMicros();
        Instant at = Instant.ofEpochSecond(
                Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
        String detail = event.getDetail().isEmpty() ? null : event.getDetail();
        HealthOrigin origin = "ERROR".equals(type) ? HealthOrigin.PROTOCOL : null;
        return new RuntimeActivityEvent(dataSourceId, type, at, detail, origin);
    }
```

- [ ] **Step 11: Run the supervisor tests**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.Supervisor*"`
Expected: PASS — health, restart, runtime-events and the rest stay green (the intentional-stop test must not see a spurious `SOURCE_ERROR`).

- [ ] **Step 12: Commit**

```bash
git add runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorHealthTest.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorRestartTest.java
git commit -m "feat(observ): IS-053 supervisor emits health transitions + tracks lastError

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Persist `origin` into the runtime_events payload

**Files:**
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListener.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListenerTest.java`

**Interfaces:**
- Consumes: `RuntimeActivityEvent.origin()` (Task 1).
- Produces: the persisted `payload` jsonb now includes `"origin"` when present, preserving `{}` / `{"detail":…}` when absent.

- [ ] **Step 1: Write the failing test** — append to `PersistingRuntimeActivityListenerTest`. Add imports:

```java
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
```

Test:

```java
@Test
void includesOriginInPayloadWhenPresent() {
    when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

    listener.onRuntimeActivity(new RuntimeActivityEvent(
            "ds1", "SOURCE_STALE", Instant.ofEpochSecond(8), "no health response",
            HealthOrigin.SIMULATOR));

    verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("SOURCE_STALE"),
            eq(Instant.ofEpochSecond(8).atOffset(ZoneOffset.UTC)),
            eq("{\"detail\":\"no health response\",\"origin\":\"SIMULATOR\"}"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.runtime.PersistingRuntimeActivityListenerTest"`
Expected: FAIL — payload is `{"detail":"no health response"}` (no origin).

- [ ] **Step 3: Build the payload from the whole event.** Replace `persist(...)`'s append call and the `payload(String detail)` method:

In `persist(...)`, change the append line:

```java
            runtimeEvents.append(projectId.get(), event.dataSourceId(), null, event.type(),
                    event.at().atOffset(ZoneOffset.UTC), payload(event));
```

Replace the `payload` method (and add the `LinkedHashMap` import):

```java
    private String payload(RuntimeActivityEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (event.detail() != null && !event.detail().isBlank()) {
            map.put("detail", event.detail());
        }
        if (event.origin() != null) {
            map.put("origin", event.origin().name());
        }
        if (map.isEmpty()) {
            return EMPTY_PAYLOAD;
        }
        try {
            return json.writeValueAsString(map);
        } catch (JacksonException e) {
            return EMPTY_PAYLOAD;
        }
    }
```

Add `import java.util.LinkedHashMap;` (keep `import java.util.Map;`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.runtime.PersistingRuntimeActivityListenerTest"`
Expected: PASS — the two existing tests (`{}` and `{"detail":"boom"}`) still hold; the new origin test passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListener.java \
        app/src/test/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListenerTest.java
git commit -m "feat(observ): IS-053 persist health-event origin in runtime_events payload

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Stream `origin` on the SSE runtime channel

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventHub.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveEventHubTest.java`

**Interfaces:**
- Consumes: `RuntimeActivityEvent.origin()` (Task 1).
- Produces: the runtime SSE data map gains an `"origin"` entry (the enum name) when the event carries one; absent otherwise.

- [ ] **Step 1: Write the failing test** — append to `LiveEventHubTest`. Add imports:

```java
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
```

Tests:

```java
@Test
void healthEventCarriesOriginInData() {
    RecordingPublisher pub = new RecordingPublisher();
    LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("p"), INLINE);

    hub.onRuntimeActivity(new RuntimeActivityEvent(
            "d1", "SOURCE_STALE", Instant.EPOCH, "stale", HealthOrigin.SIMULATOR));

    assertThat(pub.events).singleElement().satisfies(p -> {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) p.data();
        assertThat(data).containsEntry("origin", "SIMULATOR");
    });
}

@Test
void nonHealthEventOmitsOrigin() {
    RecordingPublisher pub = new RecordingPublisher();
    LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("p"), INLINE);

    hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "SOURCE_START", Instant.EPOCH, "ok"));

    assertThat(pub.events).singleElement().satisfies(p -> {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) p.data();
        assertThat(data).doesNotContainKey("origin");
    });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveEventHubTest"`
Expected: FAIL — `data` has no `origin` entry.

- [ ] **Step 3: Add origin to the data map** in `onRuntimeActivity`, after the `detail` put:

```java
            data.put("detail", event.detail() == null ? "" : event.detail());
            if (event.origin() != null) {
                data.put("origin", event.origin().name());
            }
            publisher.publish(StreamKey.runtime(projectId), event.type(), data, event.at());
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveEventHubTest"`
Expected: PASS — existing routing/caching/dispatch tests stay green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventHub.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveEventHubTest.java
git commit -m "feat(observ): IS-053 stream health-event origin on the runtime SSE channel

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Enrich the runtime-state connect snapshot with lastError

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshot.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshotTest.java`

**Interfaces:**
- Consumes: `RuntimeController.health(id)` → `SourceHealth` (Task 2).
- Produces: `SourceRuntimeState(String dataSourceId, String state, SourceError lastError)`; the snapshot is built from `health(id)`.

- [ ] **Step 1: Write the failing test** — append to `RuntimeStateSnapshotTest`. Add imports:

```java
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.Instant;
import com.ainclusive.iotsim.api.stream.RuntimeStateSnapshot.SourceRuntimeState;
```

Test:

```java
@Test
void includesLastErrorFromHealth() {
    RuntimeController rc = new RuntimeController() {
        public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
        public String stop(String id) { return "STOPPED"; }
        public String state(String id) { return "STALE"; }
        public long applyValues(String id, List<NeutralValue> v) { return 0; }
        public SourceHealth health(String id) {
            return new SourceHealth("STALE",
                    new SourceError(HealthOrigin.SIMULATOR, "stale", Instant.EPOCH));
        }
    };
    ProjectSources sources = pid -> List.of("d1");

    List<LiveEvent> initial = new RuntimeStateSnapshot(rc, sources).initialFor("p1");

    List<?> data = (List<?>) initial.get(0).data();
    SourceRuntimeState s = (SourceRuntimeState) data.get(0);
    assertThat(s.state()).isEqualTo("STALE");
    assertThat(s.lastError().origin()).isEqualTo(HealthOrigin.SIMULATOR);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.RuntimeStateSnapshotTest"`
Expected: COMPILE FAILURE — `SourceRuntimeState` has no `lastError()` accessor.

- [ ] **Step 3: Enrich the record + builder.** In `RuntimeStateSnapshot`, change the record and the mapping; add imports `import com.ainclusive.iotsim.platform.runtime.SourceError;` and `import com.ainclusive.iotsim.platform.runtime.SourceHealth;`:

```java
    /** One source's current runtime state plus its most recent error (null when none). */
    public record SourceRuntimeState(String dataSourceId, String state, SourceError lastError) {}
```

```java
    List<LiveEvent> initialFor(String projectId) {
        List<SourceRuntimeState> states = sources.idsOf(projectId).stream()
                .map(id -> {
                    SourceHealth h = runtimeController.health(id);
                    return new SourceRuntimeState(id, h.state(), h.lastError());
                })
                .toList();
        return List.of(new LiveEvent(LiveEvent.NO_SEQ, "runtime-state", states, Instant.now()));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.RuntimeStateSnapshotTest"`
Expected: PASS — the existing `buildsStatePerSourceInProject` test still passes (it extracts `dataSourceId`/`state`; the default `health()` returns `lastError == null`).

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshot.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshotTest.java
git commit -m "feat(observ): IS-053 include lastError in runtime-state connect snapshot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: `GET /api/v1/data-sources/{id}/health` endpoint

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/health/HealthController.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/stream/SourceHealthEndToEndTest.java`

**Interfaces:**
- Consumes: `RuntimeController.health(id)` → `SourceHealth`/`SourceError` (Task 2).
- Produces: `GET /api/v1/data-sources/{id}/health` → `{ "state": String, "lastError": { "origin", "reason", "at" } | null }`.

- [ ] **Step 1: Write the failing test** — create `SourceHealthEndToEndTest`:

```java
package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.health.HealthController;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = HealthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(SourceHealthEndToEndTest.TestBeans.class)
class SourceHealthEndToEndTest {

    static class TestBeans {
        @Bean
        RuntimeController runtimeController() {
            return new RuntimeController() {
                public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
                public String stop(String id) { return "STOPPED"; }
                public String state(String id) { return "STALE"; }
                public long applyValues(String id, List<NeutralValue> v) { return 0; }
                public SourceHealth health(String id) {
                    if (id.equals("d1")) {
                        return new SourceHealth("STALE", new SourceError(
                                HealthOrigin.SIMULATOR, "no health response in 3 probes",
                                Instant.parse("2026-06-30T12:00:00Z")));
                    }
                    return new SourceHealth("STOPPED", null);
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void healthEndpointReturnsStateAndError() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/data-sources/d1/health"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"state\":\"STALE\"");
        assertThat(body).contains("\"origin\":\"SIMULATOR\"");
        assertThat(body).contains("no health response in 3 probes");
    }

    @Test
    void unknownSourceReturnsStoppedWithNoError() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/data-sources/unknown/health"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"state\":\"STOPPED\"");
        assertThat(body).contains("\"lastError\":null");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.stream.SourceHealthEndToEndTest"`
Expected: COMPILE FAILURE — `HealthController` does not exist.

- [ ] **Step 3: Create `HealthController`**

```java
package com.ainclusive.iotsim.api.health;

import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-source health & error surfacing (IS-053): the current runtime state plus
 * the most recent error (origin + reason + time), retained after recovery. Live
 * transitions are on the SSE runtime stream
 * ({@code GET /api/v1/projects/{pid}/stream/runtime}); this is the point-in-time
 * query. An unknown {@code id} returns 200 with {@code STOPPED} + no error,
 * mirroring the sibling observability endpoints. See
 * backend-specs/05_API_CONTRACT.md and SPEC.md → Observe Data Source Health And
 * Errors.
 */
@RestController
public class HealthController {

    private final RuntimeController runtime;

    public HealthController(RuntimeController runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/api/v1/data-sources/{id}/health")
    public SourceHealthResponse health(@PathVariable String id) {
        return SourceHealthResponse.from(runtime.health(id));
    }

    /** Current runtime state plus the most recent error (null when none). */
    public record SourceHealthResponse(String state, SourceErrorDto lastError) {
        static SourceHealthResponse from(SourceHealth h) {
            return new SourceHealthResponse(h.state(), SourceErrorDto.from(h.lastError()));
        }
    }

    /** Where a problem came from, why, and when. */
    public record SourceErrorDto(HealthOrigin origin, String reason, Instant at) {
        static SourceErrorDto from(SourceError e) {
            return e == null ? null : new SourceErrorDto(e.origin(), e.reason(), e.at());
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.stream.SourceHealthEndToEndTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/health/HealthController.java \
        app/src/test/java/com/ainclusive/iotsim/app/stream/SourceHealthEndToEndTest.java
git commit -m "feat(api): IS-053 GET /data-sources/{id}/health endpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Full build + green gate

**Files:** none (verification task).

- [ ] **Step 1: Run the whole build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all modules compile; Spotless, Checkstyle, ArchUnit, and every test pass. (ITs that need Docker SKIP silently under a plain `./gradlew build`, which is expected here.)

- [ ] **Step 2: If anything is red, fix it before proceeding.** Re-run the specific failing module's tests, fix, and re-run `./gradlew build`. Do not move on with a red build.

- [ ] **Step 3: Confirm no stray API-version or dependency drift**

Run: `git diff --stat origin/master...HEAD`
Expected: only the files listed across Tasks 1–7; no `build.gradle` dependency additions, no `/api/v2`.

> The catalog checkbox flip for IS-053 in `backend-specs/TASKS.md` and the PR itself are handled by the `/open-pr` skill (the CI catalog-sync gate requires the flip to land in the same PR), not by this plan.

---

## Self-Review

**Spec coverage** (against `docs/superpowers/specs/2026-06-30-is-053-source-health-design.md`):
- Supervisor emits transitions on the existing rail → Task 3 (emit) + Tasks 4–5 (persist + stream). ✓
- `origin` (3 values) + `reason` on events/reads → Task 1 (`HealthOrigin`, event field), Task 3 (SIMULATOR emits, PROTOCOL mapping). ✓
- `GET /data-sources/{id}/health` with unknown-source 200 default → Task 7. ✓
- Connect snapshot enriched with `lastError` → Task 6. ✓
- Persist origin without migration (jsonb) → Task 4. ✓
- `lastError` retained after recovery → Task 3 (probeHealth recovery path does not clear it) + asserted in `recoveryEmitsRecoveredEventAndRetainsLastError`. ✓
- Flapping guarded (emit once per transition) → Task 3 (`&& !stale`) + `staleEventIsEmittedOnlyOncePerTransition`. ✓
- Duplicate-ERROR distinguishable by origin → Task 3 (SIMULATOR vs PROTOCOL). ✓
- In-memory / unknown → STOPPED + null → Task 2 default + Task 7 test. ✓

**Placeholder scan:** none — every code step shows full code; every run step shows the command + expected outcome.

**Type consistency:** `HealthOrigin`, `SourceError(origin,reason,at)`, `SourceHealth(state,lastError)`, `RuntimeController.health(String)`, `RuntimeActivityEvent(..,origin)`, `SourceRuntimeState(dataSourceId,state,lastError)`, `HealthController.SourceHealthResponse(state,lastError)` / `SourceErrorDto(origin,reason,at)` are used identically across tasks. `emitHealthEvent(type,reason)` and `scheduleRestart():boolean` are referenced consistently in Task 3.
