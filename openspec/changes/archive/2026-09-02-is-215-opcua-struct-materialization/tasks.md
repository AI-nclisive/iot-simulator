## 1. Tolerant OPC UA materialization

- [x] 1.1 Identify and omit variables whose non-standard native type has no
  supplied declaration, including nodes dependent on an omitted parent.
- [x] 1.2 Publish an existing-stream runtime warning with each omitted node and
  native type, and keep unexpected Start failures within the RPC boundary.

## 2. Verification

- [x] 2.1 Add a loopback gRPC regression test proving Start serves valid nodes,
  omits the opaque node, and reports the warning.
- [x] 2.2 Run the worker OPC UA tests and the complete Gradle build.
