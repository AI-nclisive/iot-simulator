## ADDED Requirements

### Requirement: Modbus real-source requests use structured RTU settings

The API SHALL accept and return a Modbus transport selection and, for RTU,
structured serial settings. It SHALL reject a missing port, unsupported data
bits/parity/stop bits, non-positive baud rate, or a unit ID outside 0 through
255 with a validation problem response.

#### Scenario: Invalid RTU settings are rejected

- **WHEN** a client creates or tests an RTU source with an invalid serial
  setting
- **THEN** the API returns a validation problem response before starting a
  worker operation
