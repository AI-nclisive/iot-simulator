## 1. Write the requirements

- [x] 1.1 Scenario authoring CRUD + duplicate, and validate-without-running
- [x] 1.2 Asynchronous scenario run start + per-run SSE step-event stream
- [x] 1.3 Data-source schema read and whole-set replace with versioning
- [x] 1.4 Recordings as a browsable, creatable, deletable resource
- [x] 1.5 Runs as one unified pollable resource across every run kind

## 2. Verify

- [x] 2.1 `openspec validate cover-scenario-schema-recording-runs --strict` passes
- [x] 2.2 Archive, then re-read the live `api-contract` spec and confirm the requirements landed
- [x] 2.3 Re-run the mapping-inventory diff and confirm no endpoint group is left uncovered
- [x] 2.4 `./gradlew build` and `npm run typecheck && npm run build` still green
