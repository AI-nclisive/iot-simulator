# IS-220: Configure Modbus register encoding and scaling

## Why

Modbus register maps commonly vary in byte order, word order, and engineering
scale. The fixed MSW-first, unscaled conversion cannot represent many real
devices faithfully.

## What changes

- Persist optional byte order, word order, and numeric scale with a Modbus
  register binding.
- Carry those fields through API, supervisor, and worker configuration.
- Apply the conversion symmetrically while serving values and while capturing
  values from a real Modbus device.
- Let authors configure the fields in the Modbus manual-schema editor.

## Impact

Affected capabilities: protocol-model, db-schema, worker-contract,
api-contract, frontend-screens.
