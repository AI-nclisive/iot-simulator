# IS-061 Resource Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the supervisor a global cap on concurrent long-running source workers, refusing a new start once the cap is reached (admission control), surfaced as HTTP 503.

**Architecture:** A `ResourceGovernancePolicy` (cap value, `≤0` = unlimited) is wired from config into the `Supervisor`, which guards new launches with a `Semaphore`. A permit is acquired only for a genuinely new worker, held across restart-with-backoff, and released exactly once on terminal transition or launch failure. Refusal throws `RuntimeCapacityException`, mapped to 503 by `GlobalExceptionHandler`.

**Tech Stack:** Java 21 (records, `java.util.concurrent.Semaphore`, `AtomicBoolean`), Spring Boot `@ConfigurationProperties`, JUnit 5 + AssertJ, Gradle.

## Global Constraints

- Build tool: `./gradlew` (this repo's wrapper). Must be green before PR ([[always-compile-and-test]]).
- Jackson is 3.x (`tools.jackson.*`) — not relevant here, no JSON touched.
- Governance lives **only** in the supervisor (backend-specs/02 §7). No API/domain signature changes.
- Cap is **global** over long-running source workers (the `running` map / `start()` path). Transient `scan()`/`capture()` workers are NOT counted.
- Default cap = **50**; `maxConcurrentWorkers ≤ 0` = unlimited (preserves today's unbounded behavior).
- API version stays `/api/v1` (no endpoint changes here anyway).
- Module Gradle paths: `:runtime-supervisor`, `:platform`, `:api`, `:app`.
- Java package roots: supervisor `com.ainclusive.iotsim.supervisor`; platform port `com.ainclusive.iotsim.platform.runtime`; api error `com.ainclusive.iotsim.api.error`; app config `com.ainclusive.iotsim.app.config`.

---

### Task 1: `ResourceGovernancePolicy` value object

**Files:**
- Create: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicy.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicyTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `record ResourceGovernancePolicy(int maxConcurrentWorkers)`
  - `static final ResourceGovernancePolicy DEFAULT` (cap 50)
  - `boolean isLimited()` — true when `maxConcurrentWorkers > 0`

- [ ] **Step 1: Write the failing test**

Create `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicyTest.java`:

```java
package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceGovernancePolicyTest {

    @Test
    void defaultCapIs50AndLimited() {
        assertThat(ResourceGovernancePolicy.DEFAULT.maxConcurrentWorkers()).isEqualTo(50);
        assertThat(ResourceGovernancePolicy.DEFAULT.isLimited()).isTrue();
    }

    @Test
    void positiveCapIsLimited() {
        assertThat(new ResourceGovernancePolicy(3).isLimited()).isTrue();
    }

    @Test
    void zeroOrNegativeCapMeansUnlimited() {
        assertThat(new ResourceGovernancePolicy(0).isLimited()).isFalse();
        assertThat(new ResourceGovernancePolicy(-5).isLimited()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.ResourceGovernancePolicyTest"`
Expected: FAIL — compilation error, `ResourceGovernancePolicy` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicy.java`:

```java
package com.ainclusive.iotsim.supervisor;

/**
 * Resource-governance policy: a global cap on the number of concurrent
 * long-running source workers the supervisor will run at once. The supervisor
 * refuses a new start once the cap is reached (admission control), per
 * backend-specs/02_WORKER_CONTRACT_AND_IPC.md §5.
 *
 * <p>A non-positive {@code maxConcurrentWorkers} means <em>unlimited</em> — no
 * cap is enforced.
 */
public record ResourceGovernancePolicy(int maxConcurrentWorkers) {

    /** Sensible default: 50 concurrent source workers (each worker is a JVM process). */
    public static final ResourceGovernancePolicy DEFAULT = new ResourceGovernancePolicy(50);

    /** True when a finite cap is enforced (i.e. {@code maxConcurrentWorkers > 0}). */
    public boolean isLimited() {
        return maxConcurrentWorkers > 0;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.ResourceGovernancePolicyTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicy.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/ResourceGovernancePolicyTest.java
git commit -m "feat(gen): IS-061 add ResourceGovernancePolicy (concurrent-worker cap)"
```

---

### Task 2: Supervisor admission control + `RuntimeCapacityException`

This is the core enforcement. It creates the exception type (thrown here, mapped in Task 4), threads the policy into the `Supervisor` via a new canonical constructor, and guards `start()` with a `Semaphore`.

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeCapacityException.java`
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java`
  - imports (top of file, with the other `java.util.concurrent` imports)
  - field + constructors (around lines 94–168)
  - `start()` compute lambda (lines 199–208)
  - `ManagedWorker` field + `releaseSlot()` (around lines 631–651)
  - `scheduleRestart()` two terminal returns (lines 794–814)
  - `stop()` (lines 850–882)
- Modify (test helper): `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/TestWorkerLauncher.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorGovernanceTest.java`

**Interfaces:**
- Consumes: `ResourceGovernancePolicy` (Task 1); existing `WorkerLauncher`, `RestartPolicy`, `HealthPolicy`, `RuntimeStartSpec`, `TestWorkerLauncher`.
- Produces:
  - `class RuntimeCapacityException extends RuntimeException` in `com.ainclusive.iotsim.platform.runtime` with a `RuntimeCapacityException(String message)` constructor.
  - New canonical `Supervisor` constructor:
    `Supervisor(WorkerLauncher, RestartPolicy, HealthPolicy, ClientActivityListener, RuntimeActivityListener, LiveValueListener, ResourceGovernancePolicy)`
  - Convenience constructor for tests:
    `Supervisor(WorkerLauncher, RestartPolicy, ResourceGovernancePolicy)`
  - `TestWorkerLauncher.setLaunchFailing(boolean)`.

- [ ] **Step 1: Create the exception type**

Create `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeCapacityException.java`:

```java
package com.ainclusive.iotsim.platform.runtime;

/**
 * A data source could not be started because the supervisor is at its
 * concurrent-worker cap (resource governance, backend-specs/02 §5). The request
 * is valid — the system is simply at capacity — so the API maps this to
 * 503 Service Unavailable; retry after stopping a running source or raising the
 * cap.
 */
public class RuntimeCapacityException extends RuntimeException {

    public RuntimeCapacityException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Add the launch-failure hook to the test launcher**

In `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/TestWorkerLauncher.java`, add a field next to `handshakeBroken`:

```java
    private volatile boolean launchFailing;
```

Add a setter (place it next to `setHandshakeBroken`):

```java
    /**
     * From now on, {@code launch(...)} throws immediately, simulating a worker that
     * cannot be spawned — used to exercise admission-permit rollback on a failed launch.
     */
    void setLaunchFailing(boolean failing) {
        this.launchFailing = failing;
    }
```

At the very top of `launch(String protocol, int controlPort)` (before allocating the service), add:

```java
        if (launchFailing) {
            throw new IllegalStateException("simulated launch failure");
        }
```

- [ ] **Step 3: Write the failing test**

Create `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorGovernanceTest.java`:

```java
package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the global concurrent-worker cap (IS-061) with an in-process launcher. */
class SupervisorGovernanceTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private Supervisor supervisor;

    private static RuntimeStartSpec spec() {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(), 0);
    }

    /** maxRestarts=0: an unexpected crash goes straight to ERROR (no recovery). */
    private static RestartPolicy noRetry() {
        return new RestartPolicy(Duration.ofMillis(1), 1.0, Duration.ofMillis(1), 0);
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    @Test
    void refusesStartBeyondCap() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(2));
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
        assertThatThrownBy(() -> supervisor.start("ds3", spec()))
                .isInstanceOf(RuntimeCapacityException.class)
                .hasMessageContaining("cap reached (2)");
        assertThat(launcher.launchCount()).isEqualTo(2);
    }

    @Test
    void stopFreesASlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        assertThatThrownBy(() -> supervisor.start("ds2", spec()))
                .isInstanceOf(RuntimeCapacityException.class);
        supervisor.stop("ds1");
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
        assertThat(launcher.launchCount()).isEqualTo(2);
    }

    @Test
    void idempotentRestartTakesNoSlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        supervisor.start("ds1", spec()); // idempotent: must not need a second permit
        assertThat(launcher.launchCount()).isEqualTo(1);
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    @Test
    void terminalWorkerFreesItsSlot() {
        supervisor = new Supervisor(launcher, noRetry(), new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        launcher.crashLast();
        assertThat(supervisor.state("ds1")).isEqualTo("ERROR");
        // The errored worker released its slot, so a different source can start.
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
    }

    @Test
    void replacingErroredWorkerReusesTheSlotWithoutLeaking() {
        supervisor = new Supervisor(launcher, noRetry(), new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        launcher.crashLast();
        assertThat(supervisor.state("ds1")).isEqualTo("ERROR");
        // Restart the same source: replaces the ERROR entry, takes a fresh permit.
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(launcher.launchCount()).isEqualTo(2);
        // Cap is still 1 and ds1 is active again, so a different source is refused — no leak.
        assertThatThrownBy(() -> supervisor.start("ds2", spec()))
                .isInstanceOf(RuntimeCapacityException.class);
    }

    @Test
    void failedLaunchReleasesItsSlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        launcher.setLaunchFailing(true);
        assertThatThrownBy(() -> supervisor.start("ds1", spec()))
                .isInstanceOf(RuntimeException.class);
        launcher.setLaunchFailing(false);
        // The failed launch must not have leaked the only permit.
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    @Test
    void unlimitedCapNeverRefuses() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(0));
        for (int i = 0; i < 5; i++) {
            assertThat(supervisor.start("ds" + i, spec())).isEqualTo("RUNNING");
        }
        assertThat(launcher.launchCount()).isEqualTo(5);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorGovernanceTest"`
Expected: FAIL — compilation error: no `Supervisor(WorkerLauncher, RestartPolicy, ResourceGovernancePolicy)` constructor.

- [ ] **Step 5: Add the import and the field to `Supervisor`**

In `Supervisor.java`, add to the `java.util.concurrent` import group (near line 48–56):

```java
import java.util.concurrent.Semaphore;
```

Add two fields next to `private final Map<String, ManagedWorker> running` (line 101):

```java
    /** Admission permits; {@code null} when unlimited. One permit == one occupied slot. */
    private final Semaphore admissionPermits;
    /** The configured cap, for the refusal message. {@code <= 0} means unlimited. */
    private final int workerCap;
```

- [ ] **Step 6: Add the new canonical constructor and delegate the existing one**

Replace the existing canonical 6-arg constructor (lines 145–168, the one that initializes all fields and starts the health loop) so it delegates to a new 7-arg constructor that also takes the governance policy. Concretely:

Change the existing 6-arg constructor's **signature line** from:

```java
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener,
            LiveValueListener valueListener) {
```

to a delegating body, and add the new canonical constructor below it. Replace the whole block (from that signature through its closing brace) with:

```java
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener,
            LiveValueListener valueListener) {
        this(launcher, restartPolicy, healthPolicy, clientActivityListener,
                runtimeActivityListener, valueListener, ResourceGovernancePolicy.DEFAULT);
    }

    /** Convenience for tests/callers tuning only restart + governance. */
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy,
            ResourceGovernancePolicy governance) {
        this(launcher, restartPolicy, HealthPolicy.DEFAULT, ClientActivityListener.NONE,
                RuntimeActivityListener.NONE, LiveValueListener.NONE, governance);
    }

    /** Canonical constructor: all collaborators plus the resource-governance policy (IS-061). */
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener,
            LiveValueListener valueListener,
            ResourceGovernancePolicy governance) {
        this.launcher = launcher;
        this.restartPolicy = restartPolicy;
        this.healthPolicy = healthPolicy;
        this.clientActivityListener = clientActivityListener == null
                ? ClientActivityListener.NONE : clientActivityListener;
        this.runtimeActivityListener = runtimeActivityListener == null
                ? RuntimeActivityListener.NONE : runtimeActivityListener;
        this.valueListener = valueListener == null ? LiveValueListener.NONE : valueListener;
        ResourceGovernancePolicy gov = governance == null ? ResourceGovernancePolicy.DEFAULT : governance;
        this.workerCap = gov.maxConcurrentWorkers();
        this.admissionPermits = gov.isLimited() ? new Semaphore(gov.maxConcurrentWorkers()) : null;
        AtomicInteger seq = new AtomicInteger();
        // One pool serves restarts and the health-monitoring loop; sized so a probe
        // (bounded by HealthPolicy.probeTimeout) cannot starve a pending restart.
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "worker-supervisor-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        long periodMillis = healthPolicy.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(
                this::pollHealth, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }
```

- [ ] **Step 7: Add the admission helpers**

Add these private methods near `pollHealth()` (after line 187):

```java
    /** Reserve a slot for a new worker. Always succeeds when unlimited. */
    private boolean tryAdmit() {
        return admissionPermits == null || admissionPermits.tryAcquire();
    }

    /** Release a previously reserved slot. No-op when unlimited. */
    private void releaseAdmission() {
        if (admissionPermits != null) {
            admissionPermits.release();
        }
    }
```

- [ ] **Step 8: Gate `start()`**

Replace the `running.compute(...)` block in `start()` (lines 200–208) with:

```java
        running.compute(dataSourceId, (id, existing) -> {
            if (existing != null && existing.isActive()) {
                return existing;
            }
            // Admission control (IS-061): a genuinely new worker needs a free slot.
            // A recovering worker keeps the permit it already holds, so only fresh
            // launches are gated here.
            if (!tryAdmit()) {
                throw new RuntimeCapacityException(
                        "concurrent-source cap reached (" + workerCap
                                + "); stop a running source or raise "
                                + "iotsim.runtime.governance.max-concurrent-workers");
            }
            ManagedWorker worker = new ManagedWorker(id, spec);
            try {
                worker.launchAndStart();
            } catch (RuntimeException e) {
                worker.releaseSlot(); // a failed launch must not hold a slot
                throw e;
            }
            launched.set(worker);
            return worker;
        });
```

Add the import for the exception near the other `platform.runtime` imports (the file already imports `com.ainclusive.iotsim.platform.runtime.RuntimeController` etc.):

```java
import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
```

- [ ] **Step 9: Add `releaseSlot()` + guard field to `ManagedWorker`**

Add a field to `ManagedWorker` next to its other fields (after `private int healthFailures;`, around line 643):

```java
        private final AtomicBoolean slotReleased = new AtomicBoolean();
```

(`java.util.concurrent.atomic.AtomicBoolean` is already imported at line 54.)

Add this method inside `ManagedWorker` (e.g. just before `boolean isActive()` at line 941):

```java
        /** Releases this worker's admission permit exactly once (IS-061). */
        private void releaseSlot() {
            if (slotReleased.compareAndSet(false, true)) {
                releaseAdmission();
            }
        }
```

- [ ] **Step 10: Release the slot at the two terminal transitions in `scheduleRestart()`**

In `scheduleRestart()`, add `releaseSlot();` immediately before each `return true;`.

First block (restart budget exhausted, lines 795–800) becomes:

```java
            if (restarts >= restartPolicy.maxRestarts()) {
                state = WorkerState.EXITED; // exhausted -> reported as ERROR
                lastError = new SourceError(HealthOrigin.SIMULATOR,
                        "restart budget exhausted after " + restarts + " attempts", Instant.now());
                releaseSlot();
                return true;
            }
```

Second block (scheduler rejected, lines 807–812) becomes:

```java
            } catch (RejectedExecutionException e) {
                state = WorkerState.EXITED; // scheduler shutting down; nothing more to try
                lastError = new SourceError(HealthOrigin.SIMULATOR,
                        "supervisor shutting down", Instant.now());
                releaseSlot();
                return true;
            }
```

- [ ] **Step 11: Release the slot on intentional stop**

In `stop()`, add `releaseSlot();` right after the `synchronized (this) { ... }` block closes (after line 864, before the `if (toCancel != null)` teardown):

```java
            }
            releaseSlot();
            // Tear down outside the lock; onWorkerExit will see stopping=true and skip restart.
```

(The `compareAndSet` guard makes this a no-op if the worker already released on a terminal EXITED transition, so stopping an already-ERROR worker — or draining it in `close()` — never over-releases.)

- [ ] **Step 12: Run the governance tests to verify they pass**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorGovernanceTest"`
Expected: PASS (7 tests).

- [ ] **Step 13: Run the full supervisor suite (no regressions)**

Run: `./gradlew :runtime-supervisor:test`
Expected: PASS — existing tests (`SupervisorTest`, `SupervisorRestartTest`, etc.) still green; the kept constructors delegate with `ResourceGovernancePolicy.DEFAULT`.

- [ ] **Step 14: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeCapacityException.java \
        runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/TestWorkerLauncher.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorGovernanceTest.java
git commit -m "feat(gen): IS-061 enforce concurrent-source cap in supervisor"
```

---

### Task 3: Config wiring (`RuntimeProperties` + `RuntimeConfig`)

**Files:**
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeProperties.java`
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java:60-62`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/config/RuntimePropertiesTest.java`

**Interfaces:**
- Consumes: `ResourceGovernancePolicy` (Task 1); the 7-arg `Supervisor` constructor (Task 2).
- Produces:
  - `RuntimeProperties` gains a 4th component `Governance governance` and a method `ResourceGovernancePolicy governancePolicy()`.
  - Nested `record RuntimeProperties.Governance(Integer maxConcurrentWorkers)` with `ResourceGovernancePolicy toPolicy()`.
  - Property key: `iotsim.runtime.governance.max-concurrent-workers`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ainclusive/iotsim/app/config/RuntimePropertiesTest.java`:

```java
package com.ainclusive.iotsim.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.supervisor.ResourceGovernancePolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePropertiesTest {

    @Test
    void governanceDefaultsWhenUnset() {
        RuntimeProperties props = new RuntimeProperties("supervisor", Map.of(), null, null);
        assertThat(props.governancePolicy().maxConcurrentWorkers())
                .isEqualTo(ResourceGovernancePolicy.DEFAULT.maxConcurrentWorkers());
    }

    @Test
    void governanceCapBinds() {
        RuntimeProperties props = new RuntimeProperties(
                "supervisor", Map.of(), null, new RuntimeProperties.Governance(8));
        assertThat(props.governancePolicy().maxConcurrentWorkers()).isEqualTo(8);
        assertThat(props.governancePolicy().isLimited()).isTrue();
    }

    @Test
    void nonPositiveCapMeansUnlimited() {
        RuntimeProperties props = new RuntimeProperties(
                "supervisor", Map.of(), null, new RuntimeProperties.Governance(0));
        assertThat(props.governancePolicy().isLimited()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.config.RuntimePropertiesTest"`
Expected: FAIL — `RuntimeProperties` has no 4-arg constructor and no `Governance` / `governancePolicy()`.

- [ ] **Step 3: Add `governance` to `RuntimeProperties`**

In `RuntimeProperties.java`:

Add the import next to `import com.ainclusive.iotsim.supervisor.RestartPolicy;`:

```java
import com.ainclusive.iotsim.supervisor.ResourceGovernancePolicy;
```

Change the record header from:

```java
public record RuntimeProperties(String mode, Map<String, List<String>> workers, Restart restart) {
```

to:

```java
public record RuntimeProperties(
        String mode, Map<String, List<String>> workers, Restart restart, Governance governance) {
```

In the compact constructor, add governance normalization after the `restart = ...` line:

```java
        governance = governance == null ? new Governance(null) : governance;
```

Add the policy builder next to `restartPolicy()`:

```java
    /** Builds the {@link ResourceGovernancePolicy}, applying its default for an unset cap. */
    public ResourceGovernancePolicy governancePolicy() {
        return governance.toPolicy();
    }
```

Add the nested record next to the `Restart` record:

```java
    /**
     * Resource-governance tuning (IS-061). {@code maxConcurrentWorkers} caps the
     * number of concurrent long-running source workers; unset inherits
     * {@link ResourceGovernancePolicy#DEFAULT} (50), and a value {@code <= 0} means
     * unlimited. Property: {@code iotsim.runtime.governance.max-concurrent-workers}.
     */
    public record Governance(Integer maxConcurrentWorkers) {

        ResourceGovernancePolicy toPolicy() {
            return maxConcurrentWorkers == null
                    ? ResourceGovernancePolicy.DEFAULT
                    : new ResourceGovernancePolicy(maxConcurrentWorkers);
        }
    }
```

Also extend the class-level Javadoc bullet list with a `governance` entry (keeps config docs in one place per AGENTS.md), after the `restart` bullet:

```java
 *   <li>{@code governance.max-concurrent-workers} caps concurrent source workers
 *       (default 50; {@code <= 0} = unlimited). See {@link Governance}.
```

- [ ] **Step 4: Pass the policy into the Supervisor in `RuntimeConfig`**

In `RuntimeConfig.java`, change the `Supervisor` construction (lines 60–62) from:

```java
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, clientListener, runtimeListener, liveValuesHub);
```

to:

```java
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, clientListener, runtimeListener, liveValuesHub,
                    props.governancePolicy());
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.config.RuntimePropertiesTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeProperties.java \
        app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java \
        app/src/test/java/com/ainclusive/iotsim/app/config/RuntimePropertiesTest.java
git commit -m "feat(gen): IS-061 wire governance cap via iotsim.runtime.governance"
```

---

### Task 4: Map `RuntimeCapacityException` → 503

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `RuntimeCapacityException` (Task 2).
- Produces: `GlobalExceptionHandler.capacityExceeded(RuntimeCapacityException)` returning a 503 `ProblemDetail`.

- [ ] **Step 1: Add the failing test case**

In `app/src/test/java/com/ainclusive/iotsim/app/GlobalExceptionHandlerTest.java`, add the import next to the `CaptureException` import:

```java
import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
```

Add this test method:

```java
    @Test
    void runtimeCapacityMapsTo503() {
        ProblemDetail pd = handler.capacityExceeded(
                new RuntimeCapacityException("concurrent-source cap reached (50)"));
        assertThat(pd.getStatus()).isEqualTo(503);
        assertThat(pd.getDetail()).isEqualTo("concurrent-source cap reached (50)");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.GlobalExceptionHandlerTest"`
Expected: FAIL — compilation error: `GlobalExceptionHandler` has no `capacityExceeded` method.

- [ ] **Step 3: Add the handler**

In `GlobalExceptionHandler.java`, add the import next to the other platform import:

```java
import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
```

Add this handler method (e.g. after `captureFailed`):

```java
    @ExceptionHandler(RuntimeCapacityException.class)
    public ProblemDetail capacityExceeded(RuntimeCapacityException e) {
        // The request is valid; the supervisor is simply at its concurrent-worker cap.
        // Retryable once a running source is stopped or the cap is raised (IS-061).
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.GlobalExceptionHandlerTest"`
Expected: PASS (4 tests — the 3 existing capture cases plus the new 503 case).

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java \
        app/src/test/java/com/ainclusive/iotsim/app/GlobalExceptionHandlerTest.java
git commit -m "feat(api): IS-061 map RuntimeCapacityException to 503"
```

---

### Task 5: Flip the catalog checkbox + full green build

This is the final integration gate (the `/open-pr` flow flips the catalog checkbox in the same PR per the CI catalog-sync rule; do it here so the PR is ready).

**Files:**
- Modify: `backend-specs/TASKS.md:183`

- [ ] **Step 1: Run the whole build**

Run: `./gradlew build`
Expected: PASS — full compile + all module tests green. (Testcontainers ITs may SKIP silently under a plain `./gradlew build` on Colima — that is expected per [[testcontainers-colima-env]], not a failure.)

- [ ] **Step 2: Flip the catalog checkbox**

In `backend-specs/TASKS.md`, change line 183 from:

```
- [ ] IS-061 [BE] ⬜ [runtime] Resource governance (concurrent-source caps, backpressure) — 02
```

to:

```
- [x] IS-061 [BE] ✅ [runtime] Resource governance (concurrent-source caps, backpressure) — 02
```

- [ ] **Step 3: Commit**

```bash
git add backend-specs/TASKS.md
git commit -m "docs(gen): IS-061 mark resource governance done in catalog"
```

---

## Self-Review

**Spec coverage:**
- Global concurrent-worker cap → Task 1 (policy) + Task 2 (Semaphore gate in `start()`). ✅
- Admission control / refuse new starts at cap → Task 2 (`tryAdmit()` + throw). ✅
- 503 on refusal → Task 4. ✅
- Configurable cap, default 50, `≤0` = unlimited → Task 1 (`DEFAULT`, `isLimited()`) + Task 3 (`Governance`/`governancePolicy()`). ✅
- Permit reused across restart-with-backoff (recovery not re-admitted) → Task 2 (restart path untouched; permit released only on terminal/stop). Covered by `replacingErroredWorkerReusesTheSlotWithoutLeaking` + the fact that `restart()` never calls `tryAdmit()`. ✅
- Release exactly once (at-most-once guard) → Task 2 Step 9 (`AtomicBoolean` + `compareAndSet`); exercised by `terminalWorkerFreesItsSlot`, `stopFreesASlot`. ✅
- Launch-failure rollback → Task 2 Step 8 catch + `failedLaunchReleasesItsSlot`. ✅
- Idempotent re-start takes no permit → `idempotentRestartTakesNoSlot`. ✅
- Unlimited never refuses → `unlimitedCapNeverRefuses`. ✅
- Transient scan/capture out of scope → untouched (they use `launcher.launch(...)` directly, never `start()`); no task needed. ✅
- Docs in one place → Task 3 Javadoc bullet. ✅

**Placeholder scan:** No TBD/TODO; every code step shows full code; every command has expected output. ✅

**Type consistency:** `ResourceGovernancePolicy(int maxConcurrentWorkers)`, `isLimited()`, `DEFAULT`, `RuntimeCapacityException(String)`, `tryAdmit()`/`releaseAdmission()`/`releaseSlot()`, 7-arg + 3-arg `Supervisor` constructors, `RuntimeProperties.Governance(Integer)`/`governancePolicy()`, `GlobalExceptionHandler.capacityExceeded` — names match across all tasks. ✅
