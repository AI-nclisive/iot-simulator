## Why

The simulator can currently serve and capture Modbus TCP sources only. Devices
connected through serial RS-485 links require a Modbus RTU transport while
retaining the same schema, value, capture, and replay workflow.

## What Changes

- Add an explicitly configured Modbus RTU transport, including validated serial
  port, baud rate, data bits, parity, stop bits, and unit ID.
- Extend the Modbus worker to serve, test, scan, and capture via RTU without
  changing Modbus TCP behaviour.
- Expose the RTU connection settings in the real-source creation flow.
- Add the approved `jSerialComm` dependency for portable serial-port access.

## Impact

- Affected capabilities: `worker-contract`, `domain-model`, `api-contract`,
  `frontend-screens`.
- Affected modules: server, persistence, worker-contract, worker-modbus, and
  frontend.
