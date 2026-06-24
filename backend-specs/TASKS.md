# Backend Task Register

One prioritized list: tasks are grouped by delivery **wave** (priority order) and
each carries its **status**. Derived from `backend-specs/00–08` and the
capabilities in `SPEC.md`.

Legend: `[x]` ✅ done · `[ ]` 🟡 partial · `[ ]` ⬜ todo. Each line tags its
`[area]` and the owning spec (`01`–`08`) or `SPEC` epic.

Prioritization basis: `ARCHITECTURE.md` ranks runtime fidelity, fault isolation,
determinism and reproducible evidence **above CRUD convenience**; `frontend/docs/DESIGN.md`
anchors on `Scan → Record → Replay`; `SPEC.md` sets P0/P1/P2. Waves are
dependency-ordered. (Optimized for "make the simulator real end to end, then
broaden, then harden for teams" — can be re-weighted if the near-term goal
differs.)

Snapshot: **build green, 80 tests / 22 suites, 0 skipped.** ~38 done · 2 partial ·
~57 todo.

## SDLC & Repo Foundation

Enables parallel work by humans and agents (feature branches → PRs → review →
merge). Decisions: GitHub Actions CI, squash + linear history, live status in
GitHub Issues/Project (TASKS.md = catalog). Admin-only steps (branch protection,
trunk merge, project board, repo settings): see `.github/OWNER_SETUP.md`.

Tier 1 — gate & baseline:
- [ ] SDLC-1 ⬜ Establish trunk: review & merge the foundation into `master` so branches fork from a stable baseline
- [x] SDLC-2 ✅ CI pipeline (GitHub Actions): `./gradlew build` on PR + push (= BE-F7)
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

- [x] BE-F1 ✅ [build] Gradle Kotlin-DSL multi-module monolith — 07
- [x] BE-F2 ✅ [build] build-logic convention plugin (Java 25, Spring BOM) — 07
- [x] BE-F3 ✅ [build] Version catalog — 07
- [x] BE-F4 ✅ [build] Module boundary enforcement (Gradle graph + ArchUnit) — 07
- [x] BE-F5 ✅ [build] Spring Boot app bootstrap — 07
- [x] BE-F6 ✅ [build] Dockerfile + docker-compose (Postgres) — 07/08
- [x] BE-P1 ✅ [persist] Flyway migrations V1–V6 — 04
- [x] BE-P2 ✅ [persist] jOOQ codegen from migration SQL (offline) — 04
- [x] BE-P3 ✅ [persist] Project/DataSource/Schema/Recording repositories — 04
- [x] BE-P4 ✅ [persist] Value-timeline repository (append/range/all/count) — 04
- [x] BE-M1 ✅ [model] Types: DataType/NodeKind/ValueRank/Access/Quality — 01
- [x] BE-M2 ✅ [model] SchemaNode + NeutralValue — 01
- [x] BE-M3 ✅ [model] ValueCodec — 01/04
- [x] BE-W1 ✅ [ipc] ProtocolDataSource proto + contract version — 02
- [x] BE-W2 ✅ [ipc] gRPC loopback + Hello handshake (mismatch refused) — 02
- [x] BE-W3 ✅ [ipc] Configure/Start/Stop/Health RPCs — 02
- [x] BE-W4 ✅ [ipc] ApplyValues client-streaming — 02
- [x] BE-R1 ✅ [runtime] Supervisor lifecycle (port/launch/handshake/start/stop/track) — 02
- [x] BE-R2 ✅ [runtime] WorkerClient (blocking + streaming) — 02
- [x] BE-R3 ✅ [runtime] WorkerLauncher + ProcessWorkerLauncher — 02
- [x] BE-R4 ✅ [runtime] RuntimeController port + in-memory default (config-wired) — 02/08
- [x] BE-PR1 ✅ [project] Project CRUD + optimistic concurrency — SPEC: Save/Manage Projects
- [x] BE-DS1 ✅ [source] DataSource CRUD (protocol/basis, JSONB config) — SPEC: Manage Data Sources
- [x] BE-DS2 ✅ [source] Start/stop via runtime controller — SPEC: Manage Data Sources
- [x] BE-SC1 ✅ [schema] Versioned schema CRUD + editor save — 01
- [x] BE-SC2 ✅ [schema] Node validation (kind/type/uniqueness) — 01
- [x] BE-RR1 ✅ [recording] Recording create/list/get + timeline capture — SPEC: Record/Store
- [x] BE-RR2 ✅ [replay] Replay timeline to a running source — SPEC: Replay
- [x] BE-API1 ✅ [api] REST /api/v1 (project/source/schema/recording/replay) — 05
- [x] BE-API2 ✅ [api] OpenAPI + Swagger UI — 05
- [x] BE-API3 ✅ [api] ETag/If-Match optimistic concurrency — 05
- [x] BE-API4 ✅ [api] ProblemDetail error mapping — 05
- [x] BE-AUTH1 ✅ [auth] External DB connection (env DataSource) — 08
- [x] BE-AUTH2 ✅ [auth] Deployment-mode runtime wiring — 08

## Wave A — Real runtime fidelity · P0

Turn the in-memory worker into a real OPC UA simulator (the stated core risk).

- [x] BE-W9 ✅ [ipc] Real Configure: push schema + protocol listen port to the worker — 02
- [x] BE-R5 ✅ [runtime] worker-opcua gRPC + lifecycle — 02
- [x] BE-M4 ✅ [model] OPC UA address-space projection (schema → variable nodes) — 01
- [x] BE-R6 ✅ [runtime] worker-opcua: real Eclipse Milo server + value projection — 02
- [ ] BE-R8 ⬜ [runtime] Real worker process spawn E2E (installDist + ProcessWorkerLauncher) — 02
- [ ] BE-R9 ⬜ [runtime] Restart-with-backoff on unexpected failure (intentional faults excluded) — 02
- [ ] BE-R11 ⬜ [runtime] Health monitoring loop + stale/error state propagation — 02

## Wave B — Primary flow against a real source · P0

Make `Scan → Record → Replay` work against real instruments, not provided values.

- [ ] BE-DS7 ⬜ [source] Credential handling (secrets, never persisted/exported) — 08/05
- [ ] BE-DS4 ⬜ [source] Create from scan (real-source discovery) — SPEC: Create From Scan
- [ ] BE-SC3 ⬜ [schema] Scan-derived schema population — 01
- [ ] BE-RR3 ⬜ [recording] Live capture from a running real source → recording — SPEC: Record Real Data

## Wave C — Observability & evidence · P0

Make runs observable and produce the P0 evidence artifact.

- [ ] BE-API5 ⬜ [api] SSE infrastructure (live endpoints) — 05
- [ ] BE-W5 ⬜ [ipc] ClientEvents stream (worker → supervisor) — 02
- [ ] BE-W6 ⬜ [ipc] RuntimeEvents stream (worker → supervisor) — 02
- [ ] BE-P7 ⬜ [persist] runtime_events repository (activity_events in Wave E) — 04
- [ ] BE-P6 ⬜ [persist] Repos: Evidence, Run (Sample/Fault/Scenario as those land) — 04
- [ ] BE-OBS4 ⬜ [observ] Live values + runtime state over SSE — SPEC: Observe Live Values
- [ ] BE-OBS3 ⬜ [observ] Connected-client observation per source — SPEC: Observe Connected Clients
- [ ] BE-OBS5 ⬜ [observ] Source health & error surfacing — SPEC: Observe Health
- [ ] BE-OBS6 ⬜ [observ] Project overview aggregation (running/recent/attention) — SPEC: Observe Enabled/Running
- [ ] BE-OBS1 ⬜ [observ] Runtime-event history — SPEC: Runtime Event History
- [ ] BE-P9 ⬜ [persist] ObjectStore filesystem adapter — 08
- [ ] BE-OBS7 ⬜ [observ] Evidence assembly + export — SPEC: Export Run Evidence (P0)
- [ ] BE-IO3 ⬜ [io] Evidence export format (bundle + JSON summary) — 06

## Wave D — Modbus + creation/reuse breadth · P1

- [ ] BE-R7 ⬜ [runtime] worker-modbus (j2mod) gRPC server + Modbus slave — 02
- [ ] BE-M5 ⬜ [model] Modbus register-map binding — 01
- [ ] BE-R10 ⬜ [runtime] Resource governance (concurrent-source caps, backpressure) — 02
- [ ] BE-GEN1 ⬜ [gen] Synthetic generation (patterns + range + seed) — SPEC: Generate Synthetic
- [ ] BE-GEN2 ⬜ [gen] Deterministic run settings — SPEC: Run Deterministic
- [ ] BE-M6 ⬜ [model] Injectable clock + seeded RNG — 01
- [ ] BE-DS6 ⬜ [source] Create from synthetic setup — SPEC: Manually Create / Synthetic
- [ ] BE-DS3 ⬜ [source] Duplicate data source — SPEC: Manage Data Sources
- [ ] BE-DS5 ⬜ [source] Create from import / prepared data — SPEC: Manually Create
- [ ] BE-RR4 ⬜ [recording] Samples (named subset/snapshot) — SPEC: Store Multiple/Samples
- [ ] BE-RR5 ⬜ [replay] Replay configuration (timing/ordering/compat checks) — SPEC: Replay
- [ ] BE-RR6 ⬜ [recording] Recording/sample import & export (a.k.a. BE-IO2) — SPEC: Import/Export · 06
- [ ] BE-PR2 ⬜ [project] Duplicate project — SPEC: Manage Projects
- [ ] BE-PR3 ⬜ [project] Archive project — SPEC: Manage Projects
- [ ] BE-IO1 ⬜ [io] Project export/import (versioned ZIP+manifest, secret-free) — 06
- [ ] BE-API7 ⬜ [api] Cursor pagination + filtering on collections — 05

## Wave E — Shared-team & security · P1→P2

- [ ] BE-AUTH4 ⬜ [auth] OIDC resource server (validate JWT via JWKS) — 08
- [ ] BE-AUTH5 ⬜ [auth] Flexible permission model + role→permission mapping — 08
- [ ] BE-AUTH6 ⬜ [auth] API-layer authorization enforcement (admin/user) — 08
- [ ] BE-AUTH3 🟡 [auth] Local vs shared mode enforcement (flag exists, no enforcement) — 08
- [ ] BE-P8 ⬜ [persist] Auth table repositories (users/roles/permissions/leases) — 04
- [ ] BE-AUTH7 ⬜ [auth] Advisory edit leases (read-only while editing; stale recovery) — 08
- [ ] BE-API8 ⬜ [api] Edit-lease endpoints — 05
- [ ] BE-AUTH8 ⬜ [auth] Secrets via env/external store; structural export exclusion — 08
- [ ] BE-OBS2 ⬜ [observ] User-activity audit (separate stream from runtime) — SPEC: User Activity History
- [ ] BE-PR4 ⬜ [project] Project + environment settings — 05

## Wave F — Advanced workflows & hardening · P2

- [ ] BE-GEN3 ⬜ [gen] Scenario model + steps (start/stop/replay/synthetic/fault/wait/marker) — SPEC: Build Scenarios
- [ ] BE-GEN4 ⬜ [gen] Scenario validation + run execution — SPEC: Run Scenarios
- [ ] BE-GEN5 ⬜ [gen] Fault model + injection (neutral & protocol; never auto-healed) — SPEC: Simulate Faults
- [ ] BE-W7 ⬜ [ipc] InjectFault RPC — 02
- [ ] BE-API6 ⬜ [api] Runs resource + test-control endpoints (start/state/stop) — 05/SPEC: Control From Tests
- [ ] BE-W8 ⬜ [ipc] Shutdown RPC handling — 02
- [ ] BE-IO4 ⬜ [io] Artifact version compatibility (reject newer-than-supported) — 06
- [ ] BE-IO5 ⬜ [io] Retention & cleanup (size/age/dependency-aware) — SPEC + 06
- [ ] BE-P5 🟡 [persist] Value-timeline partitioning (table partition-ready) — 04
- [ ] BE-SC4 ⬜ [schema] Schema dependency/impact checks before save — 01
- [ ] BE-F7 ⬜ [build] CI pipeline (build + test on push) — 07
- [ ] BE-F8 ⬜ [build] Self-contained local distribution (jlink/jpackage) — 07/STACK

## Recommended immediate next

**Wave A, `BE-W9 → BE-R6`** (real Configure + Milo OPC UA projection): converts the
already-green runtime plumbing into an actual OPC UA endpoint an Edge Device can
connect to — the single biggest jump in product value.
