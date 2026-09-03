## 1. Contract and persistence

- [x] 1.1 Add typed unit-id fields to platform and worker client request contracts.
- [x] 1.2 Add a collision-safe migration and repository/model support for the persisted field.
- [x] 1.3 Migrate/import/export legacy suffix-form endpoints safely.

## 2. Application and worker flow

- [x] 2.1 Use the typed field for test, scan, create, rescan, and capture.
- [x] 2.2 Remove suffix parsing from Modbus client code and validate new endpoints.
- [x] 2.3 Expose the persisted field consistently in API and frontend models.

## 3. Verification

- [x] 3.1 Add regression coverage for unit `7` across connect, scan, and capture, plus suffix rejection.
- [x] 3.2 Run OpenSpec validation, backend build, and frontend typecheck/test/build.
