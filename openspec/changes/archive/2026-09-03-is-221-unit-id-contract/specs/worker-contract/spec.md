## ADDED Requirements

### Requirement: Client-mode Modbus unit ID is a typed worker request field
The worker contract's client-mode `TestConnection`, `Scan`, and `Capture`
requests SHALL carry an optional typed Modbus unit-id field in addition to the
endpoint URL. The Modbus worker SHALL use that field, defaulting an omitted
value to `1`, and SHALL not parse a unit id from an endpoint suffix. Workers
for other protocols SHALL ignore the field.

#### Scenario: Modbus client operation uses a typed non-default unit
- **WHEN** the supervisor sends a Modbus scan or capture request with endpoint
  `tcp://device:502` and unit id `7`
- **THEN** the worker connects to that endpoint and addresses Modbus unit `7`
  without requiring `#7` in the endpoint
