# IS-127 — Bind-failure surfacing (Plan 2: worker + supervisor)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a worker cannot bind its serve port, the data source ends up in runtime state **ERROR** with a clear reason — instead of the current behaviour where the failure surfaces as a raw gRPC transport error and the source is left with no recorded error state.

**Architecture:** The OPC UA worker's `Start` RPC catches the Milo bind failure and returns `Ack(ok=false, message)` (a clean, inspectable result) instead of letting the exception become a gRPC status error. The supervisor's worker client treats `ok=false` as a `WorkerBindException`; the supervisor records it as the managed worker's `lastError`, marks the worker `EXITED` (which `stateName()` maps to `ERROR`), and leaves it queryable so `RuntimeController.state()/health()` report the failure. The domain/API then naturally show `runtimeState=ERROR` with the reason via the health surface (IS-053).

**Tech Stack:** Java 21, gRPC (worker-contract proto), Eclipse Milo (OPC UA), JUnit 5 + AssertJ, in-process gRPC test (`OpcUaWorkerGrpcTest` pattern), supervisor ITs.

**Prerequisite:** Execute `2026-07-02-is-127-datasource-payload-redesign.md` first — the serve port is now a known, user-set value (`simulatorPort`), so a bind failure is a real, reachable condition. Same branch (`feat/IS-127-datasource-payload-redesign`).

## Global Constraints

- No proto change is required: `Ack{ bool ok; string message; }` already exists on `Configure`/`Start`.
- Only the OPC UA worker binds today; the Modbus worker is a scaffold (`ModbusWorkerMain` TODO) — no bind path yet, so nothing to change there now.
- Every commit message ends with:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Supervisor ITs spawn real worker processes and need the Colima env exported or they skip silently. (memory: testcontainers-colima-env)
- Run a module `:check`/full `build` before the PR (Checkstyle/Spotless). (memory: checkstyle-runs-only-in-full-build)

---

## File Structure

**Worker (OPC UA)**
- `workers/worker-opcua/src/main/java/.../opcua/OpcUaServerRuntime.java` — MODIFY. Detect bind failure in `start()` and throw a typed exception.
- `workers/worker-opcua/src/main/java/.../opcua/BindFailedException.java` — CREATE. Marker for a port-bind failure.
- `workers/worker-opcua/src/main/java/.../opcua/OpcUaProtocolService.java` — MODIFY. `start(...)` catches it → `Ack(ok=false, msg)`.
- `workers/worker-opcua/src/test/java/.../opcua/OpcUaWorkerGrpcTest.java` — MODIFY/EXTEND. Bind-conflict returns `ok=false`.

**Supervisor**
- `runtime-supervisor/src/main/java/.../supervisor/WorkerBindException.java` — CREATE.
- `runtime-supervisor/src/main/java/.../supervisor/WorkerClient.java` — MODIFY. `start()` checks `Ack.ok`.
- `runtime-supervisor/src/main/java/.../supervisor/Supervisor.java` — MODIFY. Record `lastError`, mark `EXITED`, keep queryable; `start()` returns `ERROR` on bind failure.
- `runtime-supervisor/src/test/java/.../supervisor/SupervisorBindFailureIT.java` — CREATE (or extend an existing supervisor IT).

---

## Task 1: OPC UA worker reports bind failure as `Ack(ok=false)`

**Files:**
- Modify: `workers/worker-opcua/src/main/java/.../opcua/OpcUaServerRuntime.java`
- Create: `workers/worker-opcua/src/main/java/.../opcua/BindFailedException.java`
- Modify: `workers/worker-opcua/src/main/java/.../opcua/OpcUaProtocolService.java`
- Test: `workers/worker-opcua/src/test/java/.../opcua/OpcUaWorkerGrpcTest.java`

**Interfaces:**
- Produces: `OpcUaServerRuntime.start()` throws `BindFailedException` (RuntimeException) when the port is taken. `OpcUaProtocolService.start(...)` returns `Ack(ok=false, "...")` in that case.

- [ ] **Step 1: Write the failing test**

Extend `OpcUaWorkerGrpcTest` (in-process gRPC; it already `configure(...)`s with ports like 48400). Add a test that holds a port, then configures + starts on it and asserts a non-ok `Ack`:

```java
    @Test
    void startReturnsNotOkWhenPortIsAlreadyBound() throws Exception {
        try (java.net.ServerSocket held = new java.net.ServerSocket(0)) {
            int taken = held.getLocalPort();
            stub.configure(ConfigureRequest.newBuilder().setListenPort(taken).build());
            Ack ack = stub.start(StartRequest.newBuilder().build());
            assertThat(ack.getOk()).isFalse();
            assertThat(ack.getMessage()).contains(String.valueOf(taken));
        }
    }
```
(Import `com.ainclusive.iotsim.worker.v1.Ack` / `StartRequest` as the existing tests do; `assertThat` from AssertJ.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaWorkerGrpcTest'`
Expected: FAIL — today `start()` lets the bind error become a gRPC `StatusRuntimeException` (the call errors) rather than returning `ok=false`.

- [ ] **Step 3: Add `BindFailedException`**

```java
package com.ainclusive.iotsim.worker.opcua;

/** Raised when the OPC UA server cannot bind its configured listen port. */
public class BindFailedException extends RuntimeException {
    public BindFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Detect the bind failure in `OpcUaServerRuntime.start()`**

The port is bound by `await(server.startup())`. Wrap it and translate a bind failure into `BindFailedException`. Replace the current `start()`:

```java
    void start() {
        namespace.startup();
        try {
            await(server.startup());
        } catch (RuntimeException e) {
            if (isBindFailure(e)) {
                runtimeEventSink.accept(runtimeEvent("ERROR", "port " + port + " bind failed"));
                throw new BindFailedException("port " + port + " bind failed", e);
            }
            throw e;
        }
        runtimeEventSink.accept(runtimeEvent("SOURCE_START", ""));
    }

    private static boolean isBindFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.BindException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Address already in use")
                    || msg.contains("bind") || msg.contains("BindException"))) {
                return true;
            }
        }
        return false;
    }
```
(Keep the existing `port` field reference — `OpcUaServerRuntime` already captures `port` in its constructor to build `endpointUrl`. If `port` is not a field, capture it: `this.port = port;`.)

- [ ] **Step 5: Catch it in the gRPC `start` and return a non-ok `Ack`**

`OpcUaProtocolService.start(...)`:
```java
    @Override
    public void start(StartRequest request, StreamObserver<Ack> obs) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            try {
                runtime.start();
            } catch (BindFailedException e) {
                state.set("ERROR");
                obs.onNext(Ack.newBuilder().setOk(false).setMessage(e.getMessage()).build());
                obs.onCompleted();
                return;
            }
        }
        state.set("RUNNING");
        ackOk(obs, "started");
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaWorkerGrpcTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add workers/worker-opcua/src
git commit -m "feat(IS-127): OPC UA worker returns Ack(ok=false) on port bind failure

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Supervisor surfaces the bind failure as ERROR

**Files:**
- Create: `runtime-supervisor/src/main/java/.../supervisor/WorkerBindException.java`
- Modify: `runtime-supervisor/src/main/java/.../supervisor/WorkerClient.java`
- Modify: `runtime-supervisor/src/main/java/.../supervisor/Supervisor.java`
- Test: `runtime-supervisor/src/test/java/.../supervisor/SupervisorBindFailureIT.java`

**Interfaces:**
- Consumes: `Ack.getOk()/getMessage()` from `WorkerClient.start()`; the worker behaviour from Task 1.
- Produces: after a failed start, `RuntimeController.state(id) == "ERROR"` and `health(id)` carries the bind reason.

**Orient (read before editing — quote, don't guess):** open `Supervisor.java` and confirm these exact spots from the map:
- `ManagedWorker.connect()` `catch (RuntimeException e)` block (≈ lines 756-767) — tears down and re-throws.
- `ManagedWorker` fields `state` (≈ 697) and `lastError` (≈ 699); `stateName()` maps `EXITED -> ERROR` (≈ 1008-1017).
- `Supervisor.start()` `running.compute(...)` (≈ 227-272) and `health()` (≈ 290-295).
- `WorkerClient.start()` — currently calls the gRPC `Start` and ignores the returned `Ack`.

- [ ] **Step 1: Write the failing IT**

Create `SupervisorBindFailureIT` following the existing supervisor IT pattern (real worker installDist, supervisor runtime mode). Hold a port, start a source configured on it, assert the source ends up ERROR with a reason:

```java
    @Test
    void startOnAlreadyBoundPortLeavesSourceInError() throws Exception {
        try (java.net.ServerSocket held = new java.net.ServerSocket(0)) {
            int taken = held.getLocalPort();
            RuntimeStartSpec spec = new RuntimeStartSpec("OPC_UA", 0, java.util.List.of(), taken);
            supervisor.start("ds-bind", spec);
            assertThat(supervisor.state("ds-bind")).isEqualTo("ERROR");
            assertThat(supervisor.health("ds-bind").lastError()).isNotNull();
            assertThat(supervisor.health("ds-bind").lastError().reason()).contains(String.valueOf(taken));
        }
    }
```
(Match the concrete supervisor construction/fixture used by the existing supervisor IT. `SourceHealth.lastError()` and `SourceError.reason()` are the fields from the map.)

- [ ] **Step 2: Run to verify it fails**

Run (Colima env exported): `./gradlew :runtime-supervisor:test --tests '*SupervisorBindFailureIT'`
Expected: FAIL — today `start()` throws the gRPC error out to the caller; `state("ds-bind")` is `STOPPED` (worker never installed).
(If it is SKIPPED, Docker/worker dist is unavailable — fix the env before proceeding; a skipped test is not a pass.)

- [ ] **Step 3: Add `WorkerBindException`**

```java
package com.ainclusive.iotsim.supervisor;

/** The worker reported it could not bind its listen port (Ack ok=false on Start). */
public class WorkerBindException extends RuntimeException {
    public WorkerBindException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: `WorkerClient.start()` checks the Ack**

In `WorkerClient.start()`, after the blocking `Start` call returns its `Ack`, throw when not ok:
```java
    public void start() {
        Ack ack = blockingStub.start(StartRequest.newBuilder().build());
        if (!ack.getOk()) {
            throw new WorkerBindException(ack.getMessage());
        }
    }
```
(Adapt to the actual local name of the blocking stub and the current return handling in `WorkerClient`.)

- [ ] **Step 5: Record the failure in `ManagedWorker` and keep it queryable**

In `ManagedWorker.connect()`, split the catch so a `WorkerBindException` records state + error and is rethrown as-is (the raw teardown still runs). Change the catch block to:
```java
        } catch (RuntimeException e) {
            if (runtimeStream != null) {
                try {
                    runtimeStream.cancel();
                } catch (RuntimeException ignored) {
                }
            }
            newClient.close();
            newLaunched.close();
            if (e instanceof WorkerBindException) {
                this.state = WorkerState.EXITED;
                this.lastError = new SourceError(HealthOrigin.PROTOCOL, e.getMessage(), Instant.now());
            }
            throw e;
        }
```

Then in `Supervisor.start()`'s `running.compute(...)`, catch `WorkerBindException` so the worker is **kept in the map** (in its EXITED/ERROR state with `lastError`) and the method returns `ERROR` instead of throwing:
```java
        ManagedWorker worker = new ManagedWorker(id, spec);
        try {
            worker.launchAndStart();
        } catch (WorkerBindException e) {
            worker.releaseSlot();
            // keep the worker in the map so state()/health() report ERROR + reason
            return worker;
        } catch (RuntimeException e) {
            worker.releaseSlot();
            throw e;
        }
        launched.set(worker);
        return worker;
```
After the `compute`, only the RUNNING path returns `RUNNING`; when the stored worker is in an EXITED/ERROR state, return its `stateName()`:
```java
        ManagedWorker current = running.get(dataSourceId);
        return current == null ? STOPPED : current.stateName();
```
(Replace the current unconditional `return RUNNING;` with this. Confirm `stateName()` returns `ERROR` for `EXITED` — it does per the map. Verify `Supervisor.state(id)` reads `running.get(id).stateName()` so a stored EXITED worker reports `ERROR`; if `state(id)` special-cases missing workers as `STOPPED`, the stored ERROR worker is found and reported correctly.)

`HealthOrigin.PROTOCOL` / `.SIMULATOR`: use whichever value the codebase already uses for worker-side faults (the map shows `HealthOrigin.SIMULATOR` used for restart-budget). Pick `PROTOCOL` if it exists; otherwise reuse `SIMULATOR`. Confirm the enum constant before compiling.

- [ ] **Step 6: Run the IT to verify it passes**

Run: `./gradlew :runtime-supervisor:test --tests '*SupervisorBindFailureIT'`
Expected: PASS — `state("ds-bind") == "ERROR"`, `health(...).lastError().reason()` mentions the port.

- [ ] **Step 7: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (all modules, Checkstyle/Spotless/ArchUnit). Confirm the domain/API `start` path now reports `runtimeState=ERROR` for a bind conflict (the memory `InMemoryRuntimeController` never binds, so unit tests are unaffected; this is a supervisor-mode behaviour).

- [ ] **Step 8: Commit**

```bash
git add runtime-supervisor/src
git commit -m "feat(IS-127): supervisor surfaces worker bind failure as source ERROR + reason

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed against the spec §4)

- **Spec coverage:** worker reports bind failure rather than reporting healthy (Task 1) ✓; supervisor transitions the source to ERROR with the reason via the existing health surface (Task 2) ✓; closes the TOCTOU gap and the "port held by an external process" case (Task 1's test holds an external `ServerSocket`) ✓.
- **Placeholder scan:** worker-side code is complete and exact. The supervisor task carries an explicit "orient / confirm exact lines" step because `Supervisor.java` is large and a few surrounding names (blocking stub field, `HealthOrigin` constant, `state(id)` impl) must be read from source — the replacement code and the target blocks are given concretely.
- **Type consistency:** `WorkerBindException` (supervisor) is distinct from `BindFailedException` (worker); the boundary is the `Ack(ok=false)` returned by `WorkerClient.start()`. `WorkerState.EXITED` → `stateName()` `"ERROR"` → `RuntimeState.ERROR` in the domain. `SourceError(HealthOrigin, String reason, Instant)` matches the mapped record.
```
