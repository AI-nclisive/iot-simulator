## 1. Inventory

- [x] 1.1 Grep every `@RequestMapping` / `@*Mapping` in `api/src/main/java` into a full endpoint inventory
- [x] 1.2 Diff that inventory against the existing `api-contract` requirements to get the uncovered set

## 2. Write the requirements

- [x] 2.1 Scan materialisation (`/scan/{jobId}/create`) and rescan (`/{id}/rescan`, `/{id}/rescan/{jobId}/apply`)
- [x] 2.2 Credential clearing (`DELETE /{id}/credentials`)
- [x] 2.3 Synthetic source creation and live synthetic runs (`/data-sources/synthetic`, `/run-synthetic`)
- [x] 2.4 Recording profile derivation (`/derive-synthetic`) and recording schema/values browsing
- [x] 2.5 The shared recording+sample export/download/import shape, and samples as a resource
- [x] 2.6 Point-in-time observability queries (`/clients`, `/health`) including the unknown-id leniency
- [x] 2.7 Dashboard reads (`/active-runs`, `/projects/overview`)
- [x] 2.8 Admin user management and admin-scoped activity
- [x] 2.9 OPC UA NodeSet import diagnostics, API metadata, and the unauthenticated allowlist

## 3. Verify each behavioral claim against the code

- [x] 3.1 Confirm the synthetic duration cap really terminates the run (pacer tick finalizes it `COMPLETED`)
- [x] 3.2 Confirm sample download returns 404 before the first export (`openBundle(...).orElseThrow`)
- [x] 3.3 Confirm the unauthenticated allowlist contents — corrected the `/meta` claim, which was wrong
- [x] 3.4 `openspec validate cover-remaining-api-endpoints --strict` passes

## 4. Archive and confirm

- [x] 4.1 Archive the change, then re-read `openspec/specs/api-contract/spec.md` and confirm every requirement landed
- [x] 4.2 `./gradlew build` and `npm run typecheck && npm run build` still green
