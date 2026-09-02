## 1. API and domain request flow

- [x] 1.1 Add validated optional Modbus unit-id fields to real-source scan/test and create-from-scan API requests.
- [x] 1.2 Encode the resolved Modbus unit id into the persisted real-device endpoint while leaving OPC UA unchanged.

## 2. Worker dispatch

- [x] 2.1 Permit the supervisor to run the existing Modbus TCP worker for client-mode test, scan, and capture operations.

## 3. Verification

- [x] 3.1 Add regression tests for default and non-default Modbus unit ids across request, persistence, and worker dispatch paths.
- [ ] 3.2 Run OpenSpec validation and the Gradle build.
