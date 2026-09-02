## Why

Manual Modbus schemas currently start as a blank register map. A maintained
SunSpec inverter profile gives users a recognizable, standards-based starting
point while preserving the simulator's explicit register bindings and fixed
Modbus word order.

## What Changes

- Add a `ModbusTemplates` catalog containing a SunSpec three-phase inverter
  profile with the Common Model (1) and Inverter Model (103) fields.
- Bind the profile's variables to their public SunSpec holding-register
  addresses using the existing explicit Modbus binding fields.
- Cover the catalog and representative register/type assignments with domain
  tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `domain-model`: Manual-schema creation gains a built-in, standards-based
  Modbus device-profile template.

## Impact

- `domain` gains the template catalog and its unit tests.
- No API, persistence, frontend, worker-contract, or dependency change is
  required; the template uses the existing `SchemaNode` Modbus binding model.
