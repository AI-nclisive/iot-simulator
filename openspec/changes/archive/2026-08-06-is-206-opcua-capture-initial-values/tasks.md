## 1. Initial snapshot capture

- [x] 1.1 Read configured OPC UA variables in bounded batches after capture
  subscriptions are established.
- [x] 1.2 Convert and emit each readable initial value without stopping on an
  individual node conversion or read failure.

## 2. Verification

- [x] 2.1 Extend OPC UA capture integration coverage for a static initial value
  and a subsequent value change.
- [x] 2.2 Run the affected worker tests and the complete Gradle build.
