## 1. Capture failure lifecycle

- [x] 1.1 Propagate unexpected capture-stream termination through the capture
  port after runtime teardown.
- [x] 1.2 Clear and finalize the matching active recording on that callback.

## 2. Verification

- [x] 2.1 Cover supervisor teardown and owner notification after a stream
  error.
- [x] 2.2 Cover recording status and persisted value finalization after a
  capture failure.
- [x] 2.3 Run the complete Gradle build.
