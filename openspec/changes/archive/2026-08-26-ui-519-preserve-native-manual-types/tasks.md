## 1. Synthetic profile fidelity

- [x] 1.1 Preserve `dataTypeNodeId` and omit the primitive type for native manual-schema variables when emitting the synthetic configuration.
- [x] 1.2 Keep profile validation actionable for native variables and retain the existing primitive-variable defaults.

## 2. Regression coverage

- [x] 2.1 Add a frontend regression test that asserts a structured manual-schema variable is emitted without a `FLOAT64` fallback.
- [x] 2.2 Add or update domain coverage for manual-schema snapshot creation with a native type reference.

## 3. Verification

- [x] 3.1 Run targeted frontend and domain tests, then the required frontend checks and Gradle build.
- [x] 3.2 Re-run the manual-schema to synthetic-source E2E flow and verify the created source preserves the type or receives an explicit rejection.
