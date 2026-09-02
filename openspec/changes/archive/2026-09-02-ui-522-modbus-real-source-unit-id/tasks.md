## 1. Wizard behavior

- [x] 1.1 Add separate real-device Modbus unit-ID state, range validation, and
  a scan-only setup input.
- [x] 1.2 Send the conditional unit ID in connection-test, scan, and
  scan-created-source requests; invalidate scan state when it changes.

## 2. Verification

- [x] 2.1 Cover Modbus field visibility, validation, request payloads, and
  scan invalidation, plus absence for OPC UA.
- [x] 2.2 Run OpenSpec validation and frontend typecheck, tests, and production
  build.
