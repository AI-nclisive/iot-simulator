## MODIFIED Requirements

### Requirement: Real-source Modbus TCP operations retain an explicit unit id
The real-source connection-test and scan requests for a `MODBUS_TCP` data source SHALL accept an
optional `unitId` in the inclusive Modbus range `0` through `255`. When omitted, the system SHALL
use unit id `1`. The endpoint SHALL be a connection URL without a `#<unitId>` suffix. Creating a
data source from that scan SHALL persist the selected unit id separately from the real-device
endpoint, so its later rescan and recording capture target the same unit. `OPC_UA` requests SHALL
not acquire a Modbus unit-id requirement or behavior.

#### Scenario: Non-default Modbus unit is scanned and recorded
- **WHEN** a client tests and scans a `MODBUS_TCP` endpoint with `unitId` set to `7`, then creates
  a data source from that scan
- **THEN** the endpoint remains suffix-free and the connection test, scan, subsequent rescan, and
  recording capture all target Modbus unit `7`

#### Scenario: Omitted Modbus unit remains compatible
- **WHEN** a client tests or scans a `MODBUS_TCP` endpoint without `unitId`
- **THEN** the operation targets Modbus unit `1`

#### Scenario: OPC UA remains unit-id independent
- **WHEN** a client tests or scans an `OPC_UA` endpoint
- **THEN** its connection and capture behavior is unchanged by the Modbus unit-id capability
