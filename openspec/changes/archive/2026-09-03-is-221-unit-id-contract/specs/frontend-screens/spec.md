## MODIFIED Requirements

### Requirement: Modbus real-source scans target a selected unit
The Create Data Source Wizard SHALL show a `Unit ID` field only when the user
selects a Modbus TCP real source. It SHALL default to `1`, validate integers
from `0` through `255`, and send that separate `unitId` with test, scan, and
create-from-scan requests; the entered endpoint SHALL not be rewritten with a
unit-id suffix. OPC UA real sources SHALL not show or send a Modbus unit ID.

#### Scenario: A Modbus real-source scan uses a non-default unit
- **WHEN** a user selects Modbus TCP, selects Real source, and enters unit ID
  `7`
- **THEN** the wizard sends `unitId: 7` when testing, scanning, and creating
  from the completed scan without adding `#7` to the endpoint

#### Scenario: OPC UA scan setup has no unit ID
- **WHEN** a user selects OPC UA as a real source protocol
- **THEN** the wizard does not show a Unit ID field or send a `unitId` property
