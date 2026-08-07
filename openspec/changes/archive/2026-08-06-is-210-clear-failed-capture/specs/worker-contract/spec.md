## MODIFIED Requirements

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
