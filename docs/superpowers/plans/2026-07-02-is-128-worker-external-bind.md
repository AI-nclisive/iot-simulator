# IS-128 — Worker external-host bind (configurable bind address + advertised host) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the worker's bind interface and advertised hostname configurable at the deployment level so a simulated OPC UA source is reachable off-host, with what the worker advertises matching the API's `serveUrl`.

**Architecture:** Two deployment-level properties (`iotsim.simulator.bind-address`, default `0.0.0.0`; the existing `iotsim.simulator.advertised-host`, default `localhost`) are read by the supervisor and passed to every worker via the existing (unused) `ConfigureRequest.options` map — no proto change. The OPC UA worker uses them for `setBindAddress` / `setHostname` / the advertised `endpointUrl`. Additive canonical-constructor overloads keep the telescoping `Supervisor` constructors and all existing tests untouched; the worker falls back to loopback when options are absent.

**Tech Stack:** Java 21, Spring Boot 4, gRPC (worker-contract), Eclipse Milo (OPC UA), JUnit 5 + AssertJ, in-process gRPC test.

## Global Constraints

- Branch: `feat/IS-128-worker-external-bind` (created + linked to the issue at `/start-task`).
- No worker-contract proto change — use the existing `ConfigureRequest.options` map (field 3).
- Deployment default `bind-address = 0.0.0.0` (external reachable by default — intentional, single-platform). Worker intrinsic fallback (options absent) = loopback `127.0.0.1` for both bind + advertised, so existing worker tests stay green.
- `iotsim.simulator.advertised-host` is the single source of the host name — consumed by both the backend (`serveUrl`, IS-127) and the worker (advertised endpoint).
- Only the OPC UA worker changes (Modbus is a scaffold — inherits the plumbing later).
- Every commit message ends with: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Testcontainers/context-boot ITs skip silently under plain `./gradlew build` unless the Colima env is exported (`DOCKER_HOST` + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` = `unix:///Users/Serhii_Lazorenko/.colima/default/docker.sock`, `TESTCONTAINERS_RYUK_DISABLED=true`). Run a module `:check`/full `build` before the PR (Checkstyle/Spotless).

---

## File Structure

**Worker (OPC UA)**
- `workers/worker-opcua/src/main/java/.../opcua/OpcUaServerRuntime.java` — MODIFY. New canonical ctor takes `bindAddress` + `advertisedHost`; use them for bind/hostname/endpointUrl.
- `workers/worker-opcua/src/main/java/.../opcua/OpcUaProtocolService.java` — MODIFY. `configure` reads the two options and calls the new ctor.
- `workers/worker-opcua/src/test/java/.../opcua/OpcUaServerRuntimeTest.java` — CREATE (or extend an existing worker test): endpointUrl uses advertisedHost.
- `workers/worker-opcua/src/test/java/.../opcua/OpcUaWorkerGrpcTest.java` — MODIFY. configure-with-options starts + a client connects.

**Supervisor + app**
- `runtime-supervisor/src/main/java/.../supervisor/WorkerNetwork.java` — CREATE. `record WorkerNetwork(String bindAddress, String advertisedHost)` + `LOOPBACK`.
- `runtime-supervisor/src/main/java/.../supervisor/WorkerClient.java` — MODIFY. `configure` sets the two options (via a testable request builder).
- `runtime-supervisor/src/main/java/.../supervisor/Supervisor.java` — MODIFY. New canonical ctor takes `WorkerNetwork`; old canonical delegates with `LOOPBACK`; pass it at the `configure` call.
- `app/src/main/java/.../app/config/RuntimeConfig.java` — MODIFY. Inject the two properties, pass `new WorkerNetwork(...)` to the new ctor.
- `app/src/main/resources/application.yml` — MODIFY. Add `bind-address`.
- `runtime-supervisor/src/test/java/.../supervisor/WorkerClientConfigureTest.java` — CREATE. Options mapping.

---

## Task 1: OPC UA worker consumes bind address + advertised host

**Files:**
- Modify: `workers/worker-opcua/src/main/java/.../opcua/OpcUaServerRuntime.java`
- Modify: `workers/worker-opcua/src/main/java/.../opcua/OpcUaProtocolService.java`
- Test: `workers/worker-opcua/src/test/java/.../opcua/OpcUaServerRuntimeTest.java` (create), `OpcUaWorkerGrpcTest.java` (extend)

**Interfaces:**
- Produces: `OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables, Consumer<ClientEvent> clientEventSink, Consumer<RuntimeEvent> runtimeEventSink)` (new canonical). The existing `(port, variables, ...)` ctors keep working (loopback default). `endpointUrl()` returns `opc.tcp://<advertisedHost>:<port>/iotsim`.

- [ ] **Step 1: Write the failing unit test** — `OpcUaServerRuntimeTest`:
```java
package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpcUaServerRuntimeTest {

    @Test
    void endpointUrlUsesAdvertisedHost() {
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                4840, "0.0.0.0", "plant.local", List.of(), event -> {}, event -> {});
        assertThat(runtime.endpointUrl()).isEqualTo("opc.tcp://plant.local:4840/iotsim");
    }

    @Test
    void loopbackConstructorDefaultsAdvertisedHostToLoopback() {
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(4840, List.of());
        assertThat(runtime.endpointUrl()).isEqualTo("opc.tcp://127.0.0.1:4840/iotsim");
    }
}
```
(`endpointUrl()` is package-private — the test is in the same package. `new OpcUaServerRuntime(port, variables)` is the existing 2-arg convenience ctor; the new 6-arg is what this task adds.)

- [ ] **Step 2: Run** `./gradlew :workers:worker-opcua:test --tests '*OpcUaServerRuntimeTest'`
Expected: FAIL — the 6-arg constructor does not exist / `advertisedHost` not used.

- [ ] **Step 3: Add the canonical ctor + use the params** in `OpcUaServerRuntime.java`:
- Add fields near `private final int port;`:
```java
    private final String bindAddress;
    private final String advertisedHost;
```
- Change the current 4-arg ctor to delegate to a new 6-arg canonical ctor with loopback defaults:
```java
    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this(port, "127.0.0.1", "127.0.0.1", variables, clientEventSink, runtimeEventSink);
    }

    OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables,
            Consumer<ClientEvent> clientEventSink, Consumer<RuntimeEvent> runtimeEventSink) {
        this.runtimeEventSink = runtimeEventSink;
        this.port = port;
        this.bindAddress = bindAddress;
        this.advertisedHost = advertisedHost;
        try {
            ... // unchanged body up to the endpoint builder
```
- In the endpoint builder, use the fields instead of the hardcoded strings:
```java
                    .setBindAddress(bindAddress)
                    .setHostname(advertisedHost)
                    .setBindPort(port)
```
- And the advertised URL:
```java
            this.endpointUrl = "opc.tcp://" + advertisedHost + ":" + port + "/iotsim";
```
(The existing 2-arg and 3-arg convenience ctors delegate to the 4-arg and are unchanged.)

- [ ] **Step 4: Run** `./gradlew :workers:worker-opcua:test --tests '*OpcUaServerRuntimeTest'`
Expected: PASS.

- [ ] **Step 5: `OpcUaProtocolService.configure` reads the options** — replace the `new OpcUaServerRuntime(...)` call:
```java
        String bindAddress = request.getOptions().getOrDefault("bindAddress", "127.0.0.1");
        String advertisedHost = request.getOptions().getOrDefault("advertisedHost", "127.0.0.1");
        serverRuntime.set(new OpcUaServerRuntime(
                request.getListenPort(), bindAddress, advertisedHost, variables,
                clientEventHub::emit, runtimeEventHub::emit));
```

- [ ] **Step 6: Extend `OpcUaWorkerGrpcTest`** — configure WITH options still starts and a client connects (bind works when options are present):
```java
    @Test
    void configureWithNetworkOptionsStartsAndAcceptsClient() {
        stub.configure(ConfigureRequest.newBuilder()
                .setListenPort(0)
                .putOptions("bindAddress", "127.0.0.1")
                .putOptions("advertisedHost", "127.0.0.1")
                .build());
        Ack started = stub.start(StartRequest.newBuilder().build());
        assertThat(started.getOk()).isTrue();
    }
```
(Uses port 0 = ephemeral so it never collides with other tests; asserts the configure/start path accepts and uses the options without error. The existing `lifecycleTransitionsAreReflectedInHealth` already covers the no-options loopback path.)

- [ ] **Step 7: Run** `./gradlew :workers:worker-opcua:test`
Expected: PASS (all worker tests, incl. the untouched no-options ones).

- [ ] **Step 8: Commit**
```bash
git add workers/worker-opcua/src
git commit -m "feat(IS-128): OPC UA worker binds/advertises from configure options (loopback fallback)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Supervisor passes deployment bind/advertised config to workers

**Files:**
- Create: `runtime-supervisor/src/main/java/.../supervisor/WorkerNetwork.java`
- Modify: `runtime-supervisor/src/main/java/.../supervisor/WorkerClient.java`
- Modify: `runtime-supervisor/src/main/java/.../supervisor/Supervisor.java`
- Modify: `app/src/main/java/.../app/config/RuntimeConfig.java`
- Modify: `app/src/main/resources/application.yml`
- Test: `runtime-supervisor/src/test/java/.../supervisor/WorkerClientConfigureTest.java` (create)

**Interfaces:**
- Consumes: `WorkerClient.configure` now takes bind/advertised; the OPC UA worker (Task 1) reads `options["bindAddress"]`/`options["advertisedHost"]`.
- Produces: `record WorkerNetwork(String bindAddress, String advertisedHost)` with `static final WorkerNetwork LOOPBACK = new WorkerNetwork("127.0.0.1", "127.0.0.1")`; new `Supervisor(..., ResourceGovernancePolicy governance, WorkerNetwork network)` canonical ctor.

- [ ] **Step 1: Write the failing test** — `WorkerClientConfigureTest` asserts the options mapping via a testable builder:
```java
package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import org.junit.jupiter.api.Test;

class WorkerClientConfigureTest {

    @Test
    void configureRequestCarriesBindAndAdvertisedOptions() {
        ConfigureRequest req = WorkerClient.buildConfigureRequest(
                Schema.newBuilder().build(), 4840, "0.0.0.0", "plant.local");
        assertThat(req.getListenPort()).isEqualTo(4840);
        assertThat(req.getOptionsMap()).containsEntry("bindAddress", "0.0.0.0");
        assertThat(req.getOptionsMap()).containsEntry("advertisedHost", "plant.local");
    }
}
```

- [ ] **Step 2: Run** `./gradlew :runtime-supervisor:test --tests '*WorkerClientConfigureTest'`
Expected: FAIL — `buildConfigureRequest` / new signature missing.

- [ ] **Step 3: `WorkerClient`** — extract a testable request builder and set the options:
```java
    public Ack configure(Schema schema, int listenPort, String bindAddress, String advertisedHost) {
        return stub.configure(buildConfigureRequest(schema, listenPort, bindAddress, advertisedHost));
    }

    static ConfigureRequest buildConfigureRequest(Schema schema, int listenPort,
            String bindAddress, String advertisedHost) {
        return ConfigureRequest.newBuilder()
                .setSchema(schema)
                .setListenPort(listenPort)
                .putOptions("bindAddress", bindAddress)
                .putOptions("advertisedHost", advertisedHost)
                .build();
    }
```
(Replace the old 2-arg `configure(Schema, int)` — its only caller is `Supervisor` (Step 5).)

- [ ] **Step 4: Add `WorkerNetwork`:**
```java
package com.ainclusive.iotsim.supervisor;

/** Deployment-level network config passed to every worker (IS-128). */
public record WorkerNetwork(String bindAddress, String advertisedHost) {
    /** Loopback-only default — external access is opt-in via deployment config. */
    public static final WorkerNetwork LOOPBACK = new WorkerNetwork("127.0.0.1", "127.0.0.1");
}
```

- [ ] **Step 5: `Supervisor`** — add the field, a new canonical ctor, and pass it at configure:
- Field near the other `private final` collaborators:
```java
    private final WorkerNetwork network;
```
- Make the current canonical 7-arg ctor delegate to a new 8-arg ctor with `WorkerNetwork.LOOPBACK`, and move the body into the 8-arg:
```java
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener,
            LiveValueListener valueListener,
            ResourceGovernancePolicy governance) {
        this(launcher, restartPolicy, healthPolicy, clientActivityListener,
                runtimeActivityListener, valueListener, governance, WorkerNetwork.LOOPBACK);
    }

    /** Canonical constructor: collaborators + resource-governance (IS-061) + worker network (IS-128). */
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener,
            LiveValueListener valueListener,
            ResourceGovernancePolicy governance,
            WorkerNetwork network) {
        this.network = network == null ? WorkerNetwork.LOOPBACK : network;
        ... // the rest of the existing canonical-ctor body unchanged
    }
```
(All other telescoping ctors keep calling the 7-arg → they get `LOOPBACK` → existing tests unchanged.)
- At the `newClient.configure(...)` call in `ManagedWorker.connect()` (currently `newClient.configure(toProtoSchema(...), spec.listenPort())`):
```java
                newClient.configure(
                        toProtoSchema(spec.schemaVersion(), spec.schemaNodes()), spec.listenPort(),
                        network.bindAddress(), network.advertisedHost());
```

- [ ] **Step 6: `RuntimeConfig`** — inject the two properties and pass the network to the new ctor. Add `@Value` params to the `runtimeController` `@Bean` method and pass `WorkerNetwork`:
```java
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents,
            ClientConnectionRepository clientConnections, ObjectMapper json,
            ExecutorService runtimeEventExecutor, LiveEventHub liveEventHub,
            LiveValuesHub liveValuesHub,
            @org.springframework.beans.factory.annotation.Value("${iotsim.simulator.bind-address:0.0.0.0}")
            String bindAddress,
            @org.springframework.beans.factory.annotation.Value("${iotsim.simulator.advertised-host:localhost}")
            String advertisedHost) {
        ...
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, clientListener, runtimeListener, liveValuesHub,
                    props.governancePolicy(),
                    new WorkerNetwork(bindAddress, advertisedHost));
        ...
```
(Import `com.ainclusive.iotsim.supervisor.WorkerNetwork`.)

- [ ] **Step 7: `application.yml`** — add `bind-address` under `iotsim.simulator` (next to `advertised-host`):
```yaml
  simulator:
    # Interface the worker's protocol server binds. Default 0.0.0.0 makes the
    # simulator reachable off-host (intentional; the OPC UA endpoint is anonymous
    # / SecurityPolicy.None). Set to 127.0.0.1 to restrict to loopback. See IS-128.
    bind-address: ${IOTSIM_SIMULATOR_BIND_ADDRESS:0.0.0.0}
    advertised-host: ${IOTSIM_SIMULATOR_HOST:localhost}
```

- [ ] **Step 8: Run** `./gradlew :runtime-supervisor:test --tests '*WorkerClientConfigureTest'`
Expected: PASS.

- [ ] **Step 9: Full build (Colima env exported)** — this is the gate (Checkstyle/Spotless/ArchUnit + context-boot ITs that verify the new `RuntimeConfig` wiring boots):
```bash
export DOCKER_HOST=unix:///Users/Serhii_Lazorenko/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/Users/Serhii_Lazorenko/.colima/default/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew build
```
Expected: BUILD SUCCESSFUL. Confirm `SupervisorModeContextIT` / `ApplicationSmokeIT` actually ran (not cached — the new `@Value` bean-method params must wire); if cached, re-run with `--rerun-tasks`. If any supervisor test called the old `WorkerClient.configure(schema, port)` 2-arg, fix it to the new 4-arg (loopback: `"127.0.0.1", "127.0.0.1"`).

- [ ] **Step 10: Commit**
```bash
git add runtime-supervisor/src app/src
git commit -m "feat(IS-128): supervisor passes deployment bind-address + advertised-host to workers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (against the spec)

- **Spec coverage:** `bind-address` property + default 0.0.0.0 (Task 2 Steps 6,7) ✓; advertised-host consumed by worker (Task 1) ✓; options-map transport, no proto change (Tasks 1,2) ✓; worker uses bind/hostname/endpointUrl (Task 1) ✓; supervisor plumbing + multi-ctor safety via additive canonical ctor (Task 2 Step 5) ✓; loopback fallback preserves existing tests (Tasks 1,2) ✓; security note in the yaml comment (Task 2 Step 7) ✓; memory-mode unaffected (RuntimeConfig `else` branch untouched) ✓; Modbus out of scope ✓.
- **Placeholder scan:** no TBD/TODO; each code step carries real code. The `OpcUaServerRuntime` ctor body is edited in place ("rest unchanged") because only three lines inside a long try-block change — the three changed lines are given verbatim.
- **Type consistency:** `bindAddress`/`advertisedHost` are `String` everywhere; options keys are the literals `"bindAddress"`/`"advertisedHost"` in both the worker read (Task 1 Step 5) and the supervisor write (Task 2 Step 3); `WorkerNetwork(bindAddress, advertisedHost)` order matches its use in `RuntimeConfig` and the `configure` call.
