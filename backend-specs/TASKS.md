# Backend Task Register

One prioritized list: tasks are grouped by delivery **wave** (priority order) and
each carries its **status**. Derived from `backend-specs/00–08` and the
capabilities in `SPEC.md`.

Legend: `[x]` ✅ done · `[ ]` 🟡 partial · `[ ]` ⬜ todo. Each line tags its
`[area]` and the owning spec (`01`–`08`) or `SPEC` epic.

IDs are project-wide **`IS-XXX`** (one sequence across the project). Every entry
here is board **Area = BE** (frontend tasks are tracked separately as `FE`).
Legacy `BE-*` IDs map to the new ones via the crosswalk below. `SDLC-*` IDs are a
separate repo-process series and keep their names.

Prioritization basis: `ARCHITECTURE.md` ranks runtime fidelity, fault isolation,
determinism and reproducible evidence **above CRUD convenience**; `frontend/docs/DESIGN.md`
anchors on `Scan → Record → Replay`; `SPEC.md` sets P0/P1/P2. Waves are
dependency-ordered. (Optimized for "make the simulator real end to end, then
broaden, then harden for teams" — can be re-weighted if the near-term goal
differs.)

Snapshot: **build green, 80 tests / 22 suites, 0 skipped.** ~38 done · 2 partial ·
~57 todo.

<details>
<summary>ID crosswalk — legacy <code>BE-*</code> → <code>IS-XXX</code></summary>

| IS-XXX | legacy | IS-XXX | legacy | IS-XXX | legacy | IS-XXX | legacy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IS-001 | BE-F1 | IS-025 | BE-SC1 | IS-049 | BE-P7 | IS-073 | BE-IO1 |
| IS-002 | BE-F2 | IS-026 | BE-SC2 | IS-050 | BE-P6 | IS-074 | BE-API7 |
| IS-003 | BE-F3 | IS-027 | BE-RR1 | IS-051 | BE-OBS4 | IS-075 | BE-AUTH4 |
| IS-004 | BE-F4 | IS-028 | BE-RR2 | IS-052 | BE-OBS3 | IS-076 | BE-AUTH5 |
| IS-005 | BE-F5 | IS-029 | BE-API1 | IS-053 | BE-OBS5 | IS-077 | BE-AUTH6 |
| IS-006 | BE-F6 | IS-030 | BE-API2 | IS-054 | BE-OBS6 | IS-078 | BE-AUTH3 |
| IS-007 | BE-P1 | IS-031 | BE-API3 | IS-055 | BE-OBS1 | IS-079 | BE-P8 |
| IS-008 | BE-P2 | IS-032 | BE-API4 | IS-056 | BE-P9 | IS-080 | BE-AUTH7 |
| IS-009 | BE-P3 | IS-033 | BE-AUTH1 | IS-057 | BE-OBS7 | IS-081 | BE-API8 |
| IS-010 | BE-P4 | IS-034 | BE-AUTH2 | IS-058 | BE-IO3 | IS-082 | BE-AUTH8 |
| IS-011 | BE-M1 | IS-035 | BE-W9 | IS-059 | BE-R7 | IS-083 | BE-OBS2 |
| IS-012 | BE-M2 | IS-036 | BE-R5 | IS-060 | BE-M5 | IS-084 | BE-PR4 |
| IS-013 | BE-M3 | IS-037 | BE-M4 | IS-061 | BE-R10 | IS-085 | BE-GEN3 |
| IS-014 | BE-W1 | IS-038 | BE-R6 | IS-062 | BE-GEN1 | IS-086 | BE-GEN4 |
| IS-015 | BE-W2 | IS-039 | BE-R8 | IS-063 | BE-GEN2 | IS-087 | BE-GEN5 |
| IS-016 | BE-W3 | IS-040 | BE-R9 | IS-064 | BE-M6 | IS-088 | BE-W7 |
| IS-017 | BE-W4 | IS-041 | BE-R11 | IS-065 | BE-DS6 | IS-089 | BE-API6 |
| IS-018 | BE-R1 | IS-042 | BE-DS7 | IS-066 | BE-DS3 | IS-090 | BE-W8 |
| IS-019 | BE-R2 | IS-043 | BE-DS4 | IS-067 | BE-DS5 | IS-091 | BE-IO4 |
| IS-020 | BE-R3 | IS-044 | BE-SC3 | IS-068 | BE-RR4 | IS-092 | BE-IO5 |
| IS-021 | BE-R4 | IS-045 | BE-RR3 | IS-069 | BE-RR5 | IS-093 | BE-P5 |
| IS-022 | BE-PR1 | IS-046 | BE-API5 | IS-070 | BE-RR6 (alias BE-IO2) | IS-094 | BE-SC4 |
| IS-023 | BE-DS1 | IS-047 | BE-W5 | IS-071 | BE-PR2 | IS-095 | BE-F7 |
| IS-024 | BE-DS2 | IS-048 | BE-W6 | IS-072 | BE-PR3 | IS-096 | BE-F8 |

</details>

## SDLC & Repo Foundation

Enables parallel work by humans and agents (feature branches → PRs → review →
merge). Decisions: GitHub Actions CI, squash + linear history, live status in
GitHub Issues/Project (TASKS.md = catalog). Admin-only steps (branch protection,
trunk merge, project board, repo settings): see `.github/OWNER_SETUP.md`.

Tier 1 — gate & baseline:
- [ ] SDLC-1 ⬜ Establish trunk: review & merge the foundation into `master` so branches fork from a stable baseline
- [x] SDLC-2 ✅ CI pipeline (GitHub Actions): `./gradlew build` on PR + push (= IS-095)
- [ ] SDLC-3 🟡 Branch protection on `master` (PR + green CI + 1 approval, squash, linear) — documented; apply via `gh` after auth
- [x] SDLC-4 ✅ ITs run in CI (ubuntu runner has Docker; Testcontainers not skipped)

Tier 2 — contribution hygiene:
- [x] SDLC-5 ✅ `CONTRIBUTING.md` (branch/commit conventions, DoD, local run, parallel-work rules)
- [x] SDLC-6 ✅ PR template
- [ ] SDLC-8 🟡 Issue templates + labels done; Project board pending (token lacks `project` scope — owner creates it)
- [x] SDLC-9 ✅ `AGENTS.md` extended to code contributions

Tier 3 — quality automation:
- [x] SDLC-10 ✅ Spotless (import order + whitespace hygiene) — runs in `check`/CI
- [x] SDLC-11 ✅ Static analysis: Checkstyle (lean ruleset, generated code excluded; Error Prone/SpotBugs can be ratcheted on later)
- [x] SDLC-12 ✅ JaCoCo coverage (XML+HTML) finalizing `test`
- [x] SDLC-13 ✅ Dependabot (gradle + github-actions)

Tier 4 — parallel-conflict mitigations:
- [x] SDLC-14 ✅ Flyway migration version-collision convention (documented)
- [x] SDLC-15 ✅ Task-tracking model: Issues/Project = live status, TASKS.md = catalog (documented)
- [x] SDLC-16 ✅ Generated code kept out of VCS (standard documented)

## Wave 0 — Done: foundation & primary-flow plumbing ✅

- [x] IS-001 ✅ [build] Gradle Kotlin-DSL multi-module monolith — 07
- [x] IS-002 ✅ [build] build-logic convention plugin (Java 25, Spring BOM) — 07
- [x] IS-003 ✅ [build] Version catalog — 07
- [x] IS-004 ✅ [build] Module boundary enforcement (Gradle graph + ArchUnit) — 07
- [x] IS-005 ✅ [build] Spring Boot app bootstrap — 07
- [x] IS-006 ✅ [build] Dockerfile + docker-compose (Postgres) — 07/08
- [x] IS-007 ✅ [persist] Flyway migrations V1–V6 — 04
- [x] IS-008 ✅ [persist] jOOQ codegen from migration SQL (offline) — 04
- [x] IS-009 ✅ [persist] Project/DataSource/Schema/Recording repositories — 04
- [x] IS-010 ✅ [persist] Value-timeline repository (append/range/all/count) — 04
- [x] IS-011 ✅ [model] Types: DataType/NodeKind/ValueRank/Access/Quality — 01
- [x] IS-012 ✅ [model] SchemaNode + NeutralValue — 01
- [x] IS-013 ✅ [model] ValueCodec — 01/04
- [x] IS-014 ✅ [ipc] ProtocolDataSource proto + contract version — 02
- [x] IS-015 ✅ [ipc] gRPC loopback + Hello handshake (mismatch refused) — 02
- [x] IS-016 ✅ [ipc] Configure/Start/Stop/Health RPCs — 02
- [x] IS-017 ✅ [ipc] ApplyValues client-streaming — 02
- [x] IS-018 ✅ [runtime] Supervisor lifecycle (port/launch/handshake/start/stop/track) — 02
- [x] IS-019 ✅ [runtime] WorkerClient (blocking + streaming) — 02
- [x] IS-020 ✅ [runtime] WorkerLauncher + ProcessWorkerLauncher — 02
- [x] IS-021 ✅ [runtime] RuntimeController port + in-memory default (config-wired) — 02/08
- [x] IS-022 ✅ [project] Project CRUD + optimistic concurrency — SPEC: Save/Manage Projects
- [x] IS-023 ✅ [source] DataSource CRUD (protocol/basis, JSONB config) — SPEC: Manage Data Sources
- [x] IS-024 ✅ [source] Start/stop via runtime controller — SPEC: Manage Data Sources
- [x] IS-025 ✅ [schema] Versioned schema CRUD + editor save — 01
- [x] IS-026 ✅ [schema] Node validation (kind/type/uniqueness) — 01
- [x] IS-027 ✅ [recording] Recording create/list/get + timeline capture — SPEC: Record/Store
- [x] IS-028 ✅ [replay] Replay timeline to a running source — SPEC: Replay
- [x] IS-029 ✅ [api] REST /api/v1 (project/source/schema/recording/replay) — 05
- [x] IS-030 ✅ [api] OpenAPI + Swagger UI — 05
- [x] IS-031 ✅ [api] ETag/If-Match optimistic concurrency — 05
- [x] IS-032 ✅ [api] ProblemDetail error mapping — 05
- [x] IS-033 ✅ [auth] External DB connection (env DataSource) — 08
- [x] IS-034 ✅ [auth] Deployment-mode runtime wiring — 08

## Wave A — Real runtime fidelity · P0

Turn the in-memory worker into a real OPC UA simulator (the stated core risk).

- [x] IS-035 ✅ [ipc] Real Configure: push schema + protocol listen port to the worker — 02
- [x] IS-036 ✅ [runtime] worker-opcua gRPC + lifecycle — 02
- [x] IS-037 ✅ [model] OPC UA address-space projection (schema → variable nodes) — 01
- [x] IS-038 ✅ [runtime] worker-opcua: real Eclipse Milo server + value projection — 02
- [ ] IS-039 ⬜ [runtime] Real worker process spawn E2E (installDist + ProcessWorkerLauncher) — 02
- [ ] IS-040 ⬜ [runtime] Restart-with-backoff on unexpected failure (intentional faults excluded) — 02
- [ ] IS-041 ⬜ [runtime] Health monitoring loop + stale/error state propagation — 02

## Wave B — Primary flow against a real source · P0

Make `Scan → Record → Replay` work against real instruments, not provided values.

- [ ] IS-042 ⬜ [source] Credential handling (secrets, never persisted/exported) — 08/05
- [ ] IS-043 ⬜ [source] Create from scan (real-source discovery) — SPEC: Create From Scan
- [ ] IS-044 ⬜ [schema] Scan-derived schema population — 01
- [ ] IS-045 ⬜ [recording] Live capture from a running real source → recording — SPEC: Record Real Data

## Wave C — Observability & evidence · P0

Make runs observable and produce the P0 evidence artifact.

- [ ] IS-046 ⬜ [api] SSE infrastructure (live endpoints) — 05
- [ ] IS-047 ⬜ [ipc] ClientEvents stream (worker → supervisor) — 02
- [ ] IS-048 ⬜ [ipc] RuntimeEvents stream (worker → supervisor) — 02
- [ ] IS-049 ⬜ [persist] runtime_events repository (activity_events in Wave E) — 04
- [ ] IS-050 ⬜ [persist] Repos: Evidence, Run (Sample/Fault/Scenario as those land) — 04
- [ ] IS-051 ⬜ [observ] Live values + runtime state over SSE — SPEC: Observe Live Values
- [ ] IS-052 ⬜ [observ] Connected-client observation per source — SPEC: Observe Connected Clients
- [ ] IS-053 ⬜ [observ] Source health & error surfacing — SPEC: Observe Health
- [ ] IS-054 ⬜ [observ] Project overview aggregation (running/recent/attention) — SPEC: Observe Enabled/Running
- [ ] IS-055 ⬜ [observ] Runtime-event history — SPEC: Runtime Event History
- [ ] IS-056 ⬜ [persist] ObjectStore filesystem adapter — 08
- [ ] IS-057 ⬜ [observ] Evidence assembly + export — SPEC: Export Run Evidence (P0)
- [ ] IS-058 ⬜ [io] Evidence export format (bundle + JSON summary) — 06

## Wave D — Modbus + creation/reuse breadth · P1

- [ ] IS-059 ⬜ [runtime] worker-modbus (j2mod) gRPC server + Modbus slave — 02
- [ ] IS-060 ⬜ [model] Modbus register-map binding — 01
- [ ] IS-061 ⬜ [runtime] Resource governance (concurrent-source caps, backpressure) — 02
- [ ] IS-062 ⬜ [gen] Synthetic generation (patterns + range + seed) — SPEC: Generate Synthetic
- [ ] IS-063 ⬜ [gen] Deterministic run settings — SPEC: Run Deterministic
- [ ] IS-064 ⬜ [model] Injectable clock + seeded RNG — 01
- [ ] IS-065 ⬜ [source] Create from synthetic setup — SPEC: Manually Create / Synthetic
- [ ] IS-066 ⬜ [source] Duplicate data source — SPEC: Manage Data Sources
- [ ] IS-067 ⬜ [source] Create from import / prepared data — SPEC: Manually Create
- [ ] IS-068 ⬜ [recording] Samples (named subset/snapshot) — SPEC: Store Multiple/Samples
- [ ] IS-069 ⬜ [replay] Replay configuration (timing/ordering/compat checks) — SPEC: Replay
- [ ] IS-070 ⬜ [recording] Recording/sample import & export (legacy alias BE-IO2) — SPEC: Import/Export · 06
- [ ] IS-071 ⬜ [project] Duplicate project — SPEC: Manage Projects
- [ ] IS-072 ⬜ [project] Archive project — SPEC: Manage Projects
- [ ] IS-073 ⬜ [io] Project export/import (versioned ZIP+manifest, secret-free) — 06
- [ ] IS-074 ⬜ [api] Cursor pagination + filtering on collections — 05

## Wave E — Shared-team & security · P1→P2

- [ ] IS-075 ⬜ [auth] OIDC resource server (validate JWT via JWKS) — 08
- [ ] IS-076 ⬜ [auth] Flexible permission model + role→permission mapping — 08
- [ ] IS-077 ⬜ [auth] API-layer authorization enforcement (admin/user) — 08
- [ ] IS-078 🟡 [auth] Local vs shared mode enforcement (flag exists, no enforcement) — 08
- [ ] IS-079 ⬜ [persist] Auth table repositories (users/roles/permissions/leases) — 04
- [ ] IS-080 ⬜ [auth] Advisory edit leases (read-only while editing; stale recovery) — 08
- [ ] IS-081 ⬜ [api] Edit-lease endpoints — 05
- [ ] IS-082 ⬜ [auth] Secrets via env/external store; structural export exclusion — 08
- [ ] IS-083 ⬜ [observ] User-activity audit (separate stream from runtime) — SPEC: User Activity History
- [ ] IS-084 ⬜ [project] Project + environment settings — 05

## Wave F — Advanced workflows & hardening · P2

- [ ] IS-085 ⬜ [gen] Scenario model + steps (start/stop/replay/synthetic/fault/wait/marker) — SPEC: Build Scenarios
- [ ] IS-086 ⬜ [gen] Scenario validation + run execution — SPEC: Run Scenarios
- [ ] IS-087 ⬜ [gen] Fault model + injection (neutral & protocol; never auto-healed) — SPEC: Simulate Faults
- [ ] IS-088 ⬜ [ipc] InjectFault RPC — 02
- [ ] IS-089 ⬜ [api] Runs resource + test-control endpoints (start/state/stop) — 05/SPEC: Control From Tests
- [ ] IS-090 ⬜ [ipc] Shutdown RPC handling — 02
- [ ] IS-091 ⬜ [io] Artifact version compatibility (reject newer-than-supported) — 06
- [ ] IS-092 ⬜ [io] Retention & cleanup (size/age/dependency-aware) — SPEC + 06
- [ ] IS-093 🟡 [persist] Value-timeline partitioning (table partition-ready) — 04
- [ ] IS-094 ⬜ [schema] Schema dependency/impact checks before save — 01
- [ ] IS-095 ⬜ [build] CI pipeline (build + test on push) — 07
- [ ] IS-096 ⬜ [build] Self-contained local distribution (jlink/jpackage) — 07/STACK

## Recommended immediate next

**Wave A, `IS-035 → IS-038`** (real Configure + Milo OPC UA projection): converts the
already-green runtime plumbing into an actual OPC UA endpoint an Edge Device can
connect to — the single biggest jump in product value.
