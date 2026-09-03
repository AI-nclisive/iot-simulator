## ADDED Requirements

### Requirement: Modbus worker supports a selected TCP or RTU transport

The Modbus worker configuration SHALL carry a transport selection. For TCP it
SHALL use the configured network endpoint; for RTU it SHALL use structured
serial settings consisting of port identifier, baud rate, data bits, parity,
stop bits, and unit ID. The worker SHALL apply the selected transport for
serving, TestConnection, Scan, and Capture, and an RTU failure SHALL not alter
the existing TCP path.

#### Scenario: RTU capture uses configured serial settings

- **WHEN** a Modbus data source configured with RTU starts Capture
- **THEN** the worker opens the configured serial connection, addresses
  requests to the configured unit ID, and streams captured values through the
  existing Capture RPC

#### Scenario: TCP remains unchanged

- **WHEN** a Modbus TCP data source is configured without RTU settings
- **THEN** its worker operations use the existing TCP connection behaviour
