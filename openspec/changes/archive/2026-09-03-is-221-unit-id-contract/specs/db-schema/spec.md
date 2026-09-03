## ADDED Requirements

### Requirement: Data sources persist Modbus real-device unit IDs separately
`data_sources` SHALL store an optional `real_device_unit_id` separately from
`real_device_endpoint`. A migration SHALL split a valid legacy
`real_device_endpoint` suffix `#<unitId>` into that column and a suffix-free
endpoint, and SHALL assign unit `1` to an existing unsuffixed `MODBUS_TCP`
source. Non-Modbus data sources SHALL leave the column null.

#### Scenario: Legacy Modbus suffix migrates
- **WHEN** an existing `MODBUS_TCP` row has `real_device_endpoint` ending in
  `#7`
- **THEN** after migration its endpoint no longer ends in `#7` and its
  `real_device_unit_id` is `7`

#### Scenario: Unsuffixed legacy Modbus source remains compatible
- **WHEN** an existing `MODBUS_TCP` row has an endpoint without a unit suffix
- **THEN** after migration its `real_device_unit_id` is `1`
