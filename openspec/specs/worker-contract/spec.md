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
the initial readable value of every configured variable node, observes
subsequent changes, and streams neutral value batches back until the call is
cancelled. A value that cannot be read or converted for one node SHALL not
prevent capture from continuing for the remaining nodes. This is symmetric
with `Scan`/`TestConnection`, keeping all protocol-specific code in the
worker. If the Capture stream terminates unexpectedly, the supervisor SHALL
tear down its capture session and the owning recording SHALL no longer report
an active capture.

For a protocol with a native push/subscribe mechanism, "observes subsequent
changes" means subscribing without sampling. For a protocol with no such
mechanism, the worker SHALL instead poll every configured variable at a
bounded interval and stream a value only when it changed since the previous
poll; "without sampling" then means no change is missed at a granularity
finer than the poll interval, not literal push delivery.

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

#### Scenario: Polling-based capture streams a value only on change
- **WHEN** a worker for a protocol with no native subscribe mechanism polls a
  configured variable and its value is unchanged from the previous poll
- **THEN** the worker does not emit a redundant value for that poll cycle

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

### Requirement: Scan discovery for protocols without native browsing
A protocol with no server-advertised address-space metadata SHALL discover
nodes by having the worker itself actively probe a bounded set of candidate
addresses against the real endpoint, in lieu of browsing. An address that
responds without a protocol-level error SHALL be surfaced as a discovered
`VARIABLE` node; an address that errors or times out SHALL be excluded rather
than surfaced as a broken node. A discovered node whose data type cannot be
determined with certainty (e.g. a multi-register pairing inferred
heuristically) SHALL be surfaced as needing user confirmation, following the
same "unknown type requires resolution" pattern as an unmapped browsed type.

#### Scenario: Responsive address is discovered
- **WHEN** a probed address answers without a protocol-level error during
  Scan
- **THEN** a `VARIABLE` node is added to the scan result for that address

#### Scenario: Unresponsive address is excluded, not surfaced as broken
- **WHEN** a probed address errors or times out during Scan
- **THEN** no node is created for that address, and the scan continues with
  the remaining candidates

#### Scenario: Heuristically inferred type needs confirmation
- **WHEN** Scan infers a multi-register type for a pair of addresses rather
  than reading it from server metadata
- **THEN** the inferred node is surfaced as requiring user confirmation before
  the data source can be created from that scan

### Requirement: Schema wire message carries an optional Modbus register binding
`SchemaNodeMsg` SHALL carry `modbus_register_kind` and `modbus_address`
fields so a worker receives an explicit, persisted register/coil override
over `Configure` without a separate RPC. An empty `modbus_register_kind`
SHALL mean "no override, use the default layout rule" — the same
empty-string-means-unset convention this message already uses for `access`
and `data_type`.

#### Scenario: Empty register kind means no override
- **WHEN** `Configure` sends a `SchemaNodeMsg` with an empty
  `modbus_register_kind`
- **THEN** the worker computes the default contiguous address for that node
  rather than reading `modbus_address`

#### Scenario: Explicit binding reaches the worker unchanged
- **WHEN** `Configure` sends a `SchemaNodeMsg` with a non-empty
  `modbus_register_kind` and a `modbus_address`
- **THEN** the worker projects that node at exactly that register/coil kind
  and address

### Requirement: OPC UA simulation isolates incomplete native declarations
When an OPC UA worker starts a simulated address space, a variable that refers
to a non-standard native data type whose declaration is unavailable SHALL be
excluded from that address space. The worker SHALL continue starting all other
materializable configured nodes and emit a runtime warning that identifies the
excluded node and unavailable native type. It SHALL not silently coerce the
variable to a different data type or let this condition escape the Start RPC as
an uncaught application error.

#### Scenario: One opaque native variable is excluded
- **WHEN** an OPC UA schema contains a scalar variable and a variable with a
  non-standard data type that has no supplied declaration
- **THEN** Start succeeds, the scalar variable is served, the opaque variable
  is absent, and a runtime warning identifies the opaque variable and type

