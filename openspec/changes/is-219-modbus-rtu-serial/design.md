## Context

The supervisor owns neutral data-source configuration while the Modbus worker
owns protocol-specific serving and real-source operations. RTU must use this
same seam so the supervisor does not acquire serial-protocol code.

## Decisions

- Keep `MODBUS_TCP` as the protocol identity; introduce a Modbus transport
  choice within its connection configuration. Existing sources default to TCP.
- Persist structured RTU settings rather than embedding serial parameters in an
  endpoint string. Validate the port identifier, baud rate, data bits, parity,
  stop bits, and unit ID before a worker is configured.
- Use jSerialComm in the Modbus worker as the serial-port boundary. Isolate
  frame encoding and decoding from the port adapter so RTU protocol behaviour
  can be verified with an in-memory duplex transport; serial integration tests
  use a virtual/loopback port where the host provides one.
- Preserve the existing TCP path and its tests unchanged. A transport-specific
  failure is surfaced as a normal connection/worker error and must not affect
  another data source.

## Risks and mitigations

- Physical serial devices are unavailable in CI. Protocol framing and request
  dispatch are covered in-memory, while the jSerialComm adapter is tested
  against a virtual pair only when the platform exposes one.
- Serial ports can be unavailable or busy. Opening failures include the port
  identity and do not leave an open worker resource behind.
