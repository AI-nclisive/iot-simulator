## MODIFIED Requirements

### Requirement: Capture (recording) reports whether it is currently running
Starting a recording from a real source SHALL create a `Recording` and drive
a worker in client mode against `realDeviceEndpoint`. A status endpoint SHALL
report `{capturing, recordingId}` so a stuck or orphaned capture is
discoverable rather than only surfacing as a rejected start. While capture is
active, that recording's metadata SHALL report the full-fidelity value count
and byte size persisted so far, rather than a count inferred from conflated
live-update events.

#### Scenario: Orphaned capture is discoverable
- **WHEN** a data source's recording start returns "already capturing" but no
  UI session initiated it
- **THEN** the recording-status endpoint reports `{capturing: true,
  recordingId}` so the stuck capture can be found and stopped

#### Scenario: Active recording reports persisted progress
- **WHEN** a real-device capture has persisted values and has not yet stopped
- **THEN** retrieving its recording metadata reports the count and byte size of
  the values persisted so far
