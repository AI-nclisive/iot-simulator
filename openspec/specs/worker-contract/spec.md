# worker-contract Specification

## Purpose
The `ProtocolDataSource` gRPC contract and loopback IPC are the single seam
through which every protocol integrates with the runtime supervisor, so
adding a protocol means adding a worker, never changing the supervisor.
## Requirements
### Requirement: Loopback-only transport with versioned handshake
Supervisor-to-worker IPC SHALL use gRPC over loopback TCP (127.0.0.1) only,
never bound to a public interface. Every worker SHALL report a semver
`contractVersion` on `Hello`; the supervisor SHALL refuse a worker whose
major version does not match, with no tolerant fallback.

#### Scenario: Worker on a different major contract version is refused
- **WHEN** a worker reports a `contractVersion` with a different major
  component than the supervisor expects
- **THEN** the supervisor refuses to configure/start that worker

### Requirement: One worker process per running data source
Each running data-source instance SHALL be served by exactly one worker
process, so an unexpected worker crash only affects that one data source.

#### Scenario: Isolated crash
- **WHEN** one worker process crashes unexpectedly
- **THEN** other running data sources continue unaffected

### Requirement: Core RPC surface
The contract SHALL expose: `Hello`, `Configure`, `Start`, `Stop`,
`TestConnection`, `Scan`, `Capture` (server-streamed), `ApplyValues`
(client-streamed), `ClientEvents` (server-streamed), `RuntimeEvents`
(server-streamed), `InjectFault`, `Health`, and `Shutdown`. `Configure`
carries an optional `security_config` describing the simulated endpoint's
accepted user tokens (Anonymous and/or hashed-password UserName); an empty
`security_config` means None security / Anonymous only, so unset configs and
pre-1.3.0 workers behave exactly as before.

#### Scenario: Empty security config is anonymous-only
- **WHEN** `Configure` is sent with no `security_config`
- **THEN** the worker's simulated endpoint accepts only Anonymous connections

### Requirement: Lifecycle and fault-recovery policy
A worker SHALL progress through
`SPAWNED -> READY (Hello ok) -> CONFIGURED -> RUNNING -> STOPPED -> EXITED`.
Intentional faults injected via `InjectFault` SHALL never be auto-healed.
Only an *unexpected* worker exit SHALL trigger a supervisor restart with
exponential backoff up to a cap, with a runtime event emitted and health
state reflected to the API.

#### Scenario: Injected fault persists
- **WHEN** a fault is injected via `InjectFault`
- **THEN** the supervisor does not clear or restart the worker to resolve it;
  it stays active until explicitly cleared

#### Scenario: Unexpected exit triggers backoff restart
- **WHEN** a worker process exits without a preceding `Stop`/`Shutdown`
- **THEN** the supervisor restarts it with exponential backoff, up to a
  configured cap, and emits a runtime event

### Requirement: Recording-in via client-mode Capture
Recording from a real source SHALL be driven by a worker running in client
mode via the `Capture` RPC: the worker connects to the real endpoint, obtains
the initial readable value of every configured variable node, subscribes to
subsequent changes without sampling, and streams neutral value batches back
until the call is cancelled. A value that cannot be read or converted for one
node SHALL not prevent capture from continuing for the remaining nodes. This
is symmetric with `Scan`/`TestConnection`, keeping all protocol-specific code
in the worker. If the Capture stream terminates unexpectedly, the supervisor
SHALL tear down its capture session and the owning recording SHALL no longer
report an active capture.

#### Scenario: Capture includes static initial values
- **WHEN** recording starts against a reachable real source whose configured
  variable value does not change during the capture
- **THEN** the worker streams that variable's initial readable value to the
  supervisor

#### Scenario: Capture continues after the initial snapshot
- **WHEN** a configured variable changes after recording begins
- **THEN** the worker streams the new value until capture is cancelled

#### Scenario: One unreadable variable does not suppress other values
- **WHEN** one configured variable cannot be read or converted during the
  initial snapshot and another configured variable is readable
- **THEN** the worker streams the readable variable's value and keeps capture
  active

#### Scenario: Cancelling Capture stops recording
- **WHEN** the supervisor cancels an in-flight `Capture` call
- **THEN** the worker stops subscribing and no further value batches are sent

#### Scenario: Capture stream fails unexpectedly
- **WHEN** a live Capture stream terminates with an error or completes without
  a caller cancellation
- **THEN** the supervisor tears down its capture resources and the recording
  status reports that capture is no longer active

### Requirement: Schema transport field set
The schema message `Configure` sends to a worker carries the original
folder/variable-era attribute set only. It does NOT yet carry
`arrayDimensions`, `typeDefinition`, or a `DATA_TYPE` node's `members`, so the
extended address-space attributes in the `protocol-model` capability cannot
reach a worker over the contract today. A schema relying on them SHALL be
refused or reported as unsupported at configure time rather than sent with
those attributes silently stripped.

#### Scenario: Extended attributes are not silently dropped on the wire
- **WHEN** a schema whose nodes carry extended address-space attributes is
  sent to a worker via `Configure`
- **THEN** the caller learns those attributes are unsupported, instead of the
  worker serving a silently degraded address space

