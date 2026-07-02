# IS-128 — Worker external-host bind: configurable bind address + advertised host (design)

**Task:** IS-128 [BE] · worker/supervisor enabler
**Issue:** created at task claim via `/start-task` (board-sync mirrors it to Project #1)
**Owning spec:** `backend-specs/02_WORKER_CONTRACT_AND_IPC.md` (worker configure) + `08_AUTH_AND_MODES.md` (deployment config)
**Branch:** `feat/IS-128-worker-external-bind` (new, off `master`)
**Builds on:** IS-127 (introduced `iotsim.simulator.advertised-host` for the derived `serveUrl`) and the memory note `worker-bind-external-access`.

## Problem

Protocol workers bind only to `127.0.0.1` (loopback): `OpcUaServerRuntime` hardcodes
`setBindAddress("127.0.0.1")`, `setHostname("127.0.0.1")`, and
`endpointUrl = "opc.tcp://127.0.0.1:<port>/iotsim"`. So a simulated source is unreachable
from any other machine — an OPC UA client off-host cannot connect. IS-127 added
`iotsim.simulator.advertised-host` but it only feeds the *displayed* `serveUrl`; the worker
still advertises `127.0.0.1`, so even if it bound externally, GetEndpoints would hand a
remote client an unusable `127.0.0.1` endpoint.

## Goals / non-goals

**Goals:** make the worker's bind interface and advertised hostname configurable at the
deployment level so the simulator is reachable off-host, and make what the worker advertises
match the API's `serveUrl`.

**Non-goals:** per-data-source bind (deployment has one host — sources differ only by port);
authentication/security policy on the OPC UA endpoint (still `SecurityPolicy.None`/anonymous —
out of scope); a worker-contract proto change (the existing `options` map carries the config);
the Modbus worker's implementation (still a scaffold — it inherits the same plumbing when built).

## Decisions (agreed in brainstorming)

1. **Deployment-level config, one host** (not per-source): sources differ only by
   `simulatorPort`. Two properties under `iotsim.simulator`:
   - `bind-address: ${IOTSIM_SIMULATOR_BIND_ADDRESS:0.0.0.0}` — NEW; the interface the worker
     listens on. **Default `0.0.0.0`** (external reachable out of the box — a deliberate choice
     for this single-platform deployment).
   - `advertised-host: ${IOTSIM_SIMULATOR_HOST:localhost}` — EXISTING (IS-127); now consumed by
     BOTH the backend (`serveUrl`) and the worker (the hostname it advertises). Single source of
     the host name, so `serveUrl` == what the OPC UA server actually advertises.
2. **Transport via the existing `ConfigureRequest.options` map** (proto field 3, currently
   unused) — no contract change. The supervisor puts `bindAddress` + `advertisedHost` into
   `options` on every `configure`; the worker reads them.
3. **Worker intrinsic fallback = loopback** (`127.0.0.1` for both) when the options are absent,
   preserving current behavior for standalone/legacy worker launches and keeping existing worker
   tests green. The deployment default `0.0.0.0` lives in the supervisor's config, not the
   worker fallback.

## Components

### 1. Config

`app/src/main/resources/application.yml` — under `iotsim.simulator`, add:
```yaml
    bind-address: ${IOTSIM_SIMULATOR_BIND_ADDRESS:0.0.0.0}
```
next to the existing `advertised-host`. Comment: binding a non-loopback interface exposes the
simulator (anonymous, `SecurityPolicy.None`) on the network — intentional for this deployment.

### 2. Worker — `OpcUaServerRuntime`

Constructor gains `String bindAddress, String advertisedHost`:
- `.setBindAddress(bindAddress)` (was `"127.0.0.1"`)
- `.setHostname(advertisedHost)` (was `"127.0.0.1"`)
- `this.endpointUrl = "opc.tcp://" + advertisedHost + ":" + port + "/iotsim"` (was `127.0.0.1`)

### 3. Worker — `OpcUaProtocolService.configure`

Read the options with loopback fallbacks and pass them through:
```java
String bindAddress = request.getOptions().getOrDefault("bindAddress", "127.0.0.1");
String advertisedHost = request.getOptions().getOrDefault("advertisedHost", "127.0.0.1");
serverRuntime.set(new OpcUaServerRuntime(
        request.getListenPort(), bindAddress, advertisedHost, variables,
        clientEventHub::emit, runtimeEventHub::emit));
```

### 4. Supervisor — pass the config to every worker

- `WorkerClient.configure(Schema schema, int listenPort)` → gains
  `configure(Schema schema, int listenPort, String bindAddress, String advertisedHost)`, which
  sets `.putOptions("bindAddress", bindAddress).putOptions("advertisedHost", advertisedHost)` on
  the `ConfigureRequest`.
- `Supervisor` (supervisor-mode bean) reads `iotsim.simulator.bind-address` (default `0.0.0.0`)
  and `iotsim.simulator.advertised-host` (default `localhost`) — injected where the bean is
  constructed — holds them as fields, and passes them at the `newClient.configure(...)` call in
  `ManagedWorker.connect()`.
- **Multi-ctor gotcha** (memory: multi-ctor-bean-needs-autowired): `Supervisor` has a test
  constructor; add the two params to the production constructor / its `@Configuration` wiring and
  `@Autowired` the prod ctor if needed, else the context won't boot. Verify with a full build /
  `--rerun-tasks` (`SupervisorModeContextIT`, `ApplicationSmokeIT`).
- Memory-mode (`InMemoryRuntimeController`) spawns no workers — unaffected.

## Error handling

No new failure modes. A bad bind address surfaces through the existing IS-127 bind-failure path
(worker `Ack(ok=false)` → source `ERROR` with reason). Absent options → loopback fallback (never
throws).

## Security

Default `bind-address=0.0.0.0` exposes the OPC UA server (anonymous, `SecurityPolicy.None`) on
the host's network by default. This is a deliberate deployment choice (single-platform, trusted
network); documented here and in a code comment. Locking the endpoint down (auth / security
policy / firewalling) is a separate future concern, not part of this task.

## Testing

- **`OpcUaServerRuntime` / `OpcUaProtocolService` (worker gRPC test):** `configure` with
  `options{bindAddress, advertisedHost}` → the server's `endpointUrl()` uses `advertisedHost`
  (`opc.tcp://<advertisedHost>:<port>/iotsim`) and the server starts on the given bind address
  (a client connects). Asserting the exact bound interface is awkward; assert the advertised URL
  + a successful client connect.
- **Backward-compat:** existing worker tests that `configure` without options (and connect via
  `127.0.0.1`) stay green via the loopback fallback — do not change them.
- **Supervisor:** the deployment config values reach the worker in `options` — a focused test
  captures the `ConfigureRequest` (spy/fake `WorkerClient`) and asserts `options["bindAddress"]`
  == configured value and `options["advertisedHost"]` == configured value.
- **Config/context:** `bind-address` present in `application.yml`; `SupervisorModeContextIT` /
  `ApplicationSmokeIT` boot green with the new constructor wiring.
- True cross-host reachability is not reliably CI-testable — we verify the plumbing (options set,
  `endpointUrl` uses the advertised host), not actual off-host networking.

## Out of scope / deferred

- Modbus worker bind (scaffold; inherits the plumbing when implemented).
- OPC UA endpoint authentication / non-None security policy.
- Per-source bind address; proto changes.

## Definition of done

`./gradlew build` green (unit + ITs + ArchUnit + Checkstyle/Spotless; context-boot ITs run under
Colima env). IS-128 line added+checked in `backend-specs/TASKS.md` in the same PR; board → In
review via `/open-pr`.
