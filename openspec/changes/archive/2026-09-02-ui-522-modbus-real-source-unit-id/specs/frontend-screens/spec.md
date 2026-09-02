## ADDED Requirements

### Requirement: Modbus real-source scans target a selected unit
The Create Data Source Wizard SHALL show a `Unit ID` field only when the user
selects both `Modbus TCP` and `Real source`. The field SHALL default to `1`,
accept whole-number values from `0` through `255`, and send the selected value
with the real-source connection test, scan, and creation from a completed scan.
The wizard SHALL omit the field and its request property for protocols that do
not use a Modbus unit ID.

#### Scenario: A Modbus real-source scan uses a non-default unit
- **WHEN** a user selects Modbus TCP, selects Real source, and enters unit ID
  `7`
- **THEN** the wizard sends `unitId: 7` when testing, scanning, and creating
  the source from that scan

#### Scenario: OPC UA scan setup has no unit ID
- **WHEN** a user selects OPC UA and Real source
- **THEN** the wizard does not show a Unit ID field or send a `unitId` property
