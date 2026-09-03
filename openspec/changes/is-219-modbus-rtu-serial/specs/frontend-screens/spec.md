## ADDED Requirements

### Requirement: Real-source Modbus setup exposes RTU serial settings

The Create Data Source Wizard SHALL let a user selecting a real Modbus source
choose TCP or RTU. When RTU is selected, it SHALL show port identifier, baud
rate, data bits, parity, stop bits, and unit ID controls and submit them as
structured connection settings; TCP endpoint controls remain available only
for TCP.

#### Scenario: User configures an RTU source

- **WHEN** a user selects Modbus RTU and enters valid serial settings
- **THEN** the wizard sends those settings with the connection test, scan, and
create requests
