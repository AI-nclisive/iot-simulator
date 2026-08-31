## MODIFIED Requirements

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

## ADDED Requirements

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
