## 1. Contract and persistence

- [x] 1.1 Add validated Modbus encoding and scale metadata to schema nodes and API DTOs.
- [x] 1.2 Persist normalized-schema metadata with an append-only Flyway migration.
- [x] 1.3 Carry metadata through the worker schema wire message and supervisor.

## 2. Modbus runtime

- [x] 2.1 Implement configurable register byte/word ordering and scale transforms.
- [x] 2.2 Apply the transforms in both server projection and capture decoding.
- [x] 2.3 Add representative signed, unsigned, float, and default-compatibility tests.

## 3. Manual-schema editor

- [x] 3.1 Expose validated Modbus encoding and scale controls for register variables.
- [x] 3.2 Preserve the fields when saving and add UI tests.

## 4. Verification

- [x] 4.1 Run focused backend and frontend tests.
- [x] 4.2 Run full Gradle and frontend checks.
