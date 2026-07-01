# Backend & SDLC Task Register

One prioritized list: tasks are grouped by delivery **wave** (priority order) and
each carries its **status**. Derived from `backend-specs/00–08` and the
capabilities in `SPEC.md`.

Naming: backend and repo/process tasks are **`IS-XXX [AREA] short name`**, where
`IS-XXX` is the backend/SDLC task ID and `[AREA]` is `[BE]` (backend) or
`[SDLC]` (repo/process). This register holds the `[BE]` and `[SDLC]` tasks;
frontend (`[FE]`) tasks use `UI-XXX` IDs and are tracked in
`frontend/docs/UI_TASKS.md`. Legacy `BE-*` and `SDLC-*` IDs map to the new
`IS-*` IDs via the crosswalk below.

Legend: `[x]` ✅ done · `[ ]` 🟡 partial · `[ ]` ⬜ todo. Each line reads
`IS-XXX [AREA]` · status · `[module]` tag · owning spec (`01`–`08`) or `SPEC` epic.

Prioritization basis: `ARCHITECTURE.md` ranks runtime fidelity, fault isolation,
determinism and reproducible evidence **above CRUD convenience**; `frontend/docs/DESIGN.md`
anchors on `Scan → Record → Replay`; `SPEC.md` sets P0/P1/P2. Waves are
dependency-ordered. (Optimized for "make the simulator real end to end, then
broaden, then harden for teams" — can be re-weighted if the near-term goal
differs.)

Snapshot: **build green.** 102 done · 19 todo (121 total). Live status is the board; this line is a periodic snapshot.

<details>
<summary>ID crosswalk — legacy <code>BE-*</code> / <code>SDLC-*</code> → <code>IS-XXX</code></summary>

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

| IS-XXX | legacy | IS-XXX | legacy | IS-XXX | legacy | IS-XXX | legacy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IS-097 | SDLC-1 | IS-101 | SDLC-5 | IS-105 | SDLC-10 | IS-109 | SDLC-14 |
| IS-098 | SDLC-2 | IS-102 | SDLC-6 | IS-106 | SDLC-11 | IS-110 | SDLC-15 |
| IS-099 | SDLC-3 | IS-103 | SDLC-8 | IS-107 | SDLC-12 | IS-111 | SDLC-16 |
| IS-100 | SDLC-4 | IS-104 | SDLC-9 | IS-108 | SDLC-13 | | |

</details>

## SDLC & Repo Foundation

Enables parallel work by humans and agents (feature branches → PRs → review →
merge). Decisions: GitHub Actions CI, squash + linear history, live status in
GitHub Issues/Project (TASKS.md = catalog). Admin-only steps (branch protection,
project board, repo settings): see the config record `.github/OWNER_SETUP.md`.

Tier 1 — gate & baseline:
- [x] IS-097 [SDLC] ✅ Establish trunk: foundation merged into `master` (PRs squash-merged); branches fork from a stable baseline
- [x] IS-098 [SDLC] ✅ CI pipeline (GitHub Actions): `./gradlew build` on PR + push (= IS-095)
- [x] IS-099 [SDLC] ✅ Branch protection on `master` applied (PR + green CI `build` check + 1 approval, squash, linear, no force-push) — see `.github/OWNER_SETUP.md`
- [x] IS-100 [SDLC] ✅ ITs run in CI (ubuntu runner has Docker; Testcontainers not skipped)

Tier 2 — contribution hygiene:
- [x] IS-101 [SDLC] ✅ `CONTRIBUTING.md` (branch/commit conventions, DoD, local run, parallel-work rules)
- [x] IS-102 [SDLC] ✅ PR template
- [x] IS-103 [SDLC] ✅ Issue templates + labels + org Project #1 board applied (fields `Status` / `Task ID` / `Area` BE/FE/SDLC; issues mirrored 1:1)
- [x] IS-104 [SDLC] ✅ `AGENTS.md` extended to code contributions
- [x] IS-113 [SDLC] ✅ Board status flow + review-completion rule documented (In Progress at start, In review at PR open; task done only when all review comments resolved) — `AGENTS.md`/`CONTRIBUTING.md`
- [x] IS-115 [SDLC] ✅ Task pre-flight checklist + board-wins tie-break + `catalog-sync` CI guard (a task-linked PR must flip its own catalog checkbox) — `AGENTS.md`/`.github/workflows/ci.yml`
- [x] IS-117 [SDLC] ✅ Claude Code skills for the contribution workflow under `.claude/skills/` (`/start-task`, `/open-pr`, `/review-loop`, `/new-worker`, `/flyway-migration`, `/board-sync`) + recommended built-in skills documented in `CONTRIBUTING.md` — executable procedures for `CONTRIBUTING.md`/`AGENTS.md`
- [x] IS-120 [SDLC] ✅ Local e2e run tooling — `/run-local` skill (cross-platform: Postgres + backend + Vite dev proxy, with teardown) + README run/test instructions + `docs/FRONTEND_BACKEND_CONTRACT_MAP.md` refresh for IS-074; wire FE list stores (projects/data-sources/recordings) to the `Page<T>` pagination envelope so they stop failing to load

Tier 3 — quality automation:
- [x] IS-105 [SDLC] ✅ Spotless (import order + whitespace hygiene) — runs in `check`/CI
- [x] IS-106 [SDLC] ✅ Static analysis: Checkstyle (lean ruleset, generated code excluded; Error Prone/SpotBugs can be ratcheted on later)
- [x] IS-107 [SDLC] ✅ JaCoCo coverage (XML+HTML) finalizing `test`
- [x] IS-108 [SDLC] ✅ Dependabot (gradle + github-actions)
- [x] IS-112 [SDLC] ✅ Claude PR code review (`anthropics/claude-code-action` on `pull_request`): reviews every PR diff against repo conventions, posts inline + verdict comments, and submits a formal review (APPROVE / REQUEST_CHANGES) that gates merge — the Claude GitHub App's APPROVE supplies branch protection's 1 required review; required check stays `build`. Auth = `CLAUDE_CODE_OAUTH_TOKEN` + Claude GitHub App — see `.github/OWNER_SETUP.md`
- [ ] IS-121 [SDLC] ⬜ Web-layer (MockMvc) controller test harness — add `spring-boot-starter-test` to the `api` module + `@WebMvcTest` slice pattern (HTTP status via `GlobalExceptionHandler`, JSON, headers); backfill `ScenarioController` then other CRUD controllers. Split out of IS-085.

Tier 4 — parallel-conflict mitigations:
- [x] IS-109 [SDLC] ✅ Flyway migration version-collision convention (documented)
- [x] IS-110 [SDLC] ✅ Task-tracking model: Issues/Project = live status, TASKS.md = catalog (documented)
- [x] IS-111 [SDLC] ✅ Generated code kept out of VCS (standard documented)

## Wave 0 — Done: foundation & primary-flow plumbing ✅

- [x] IS-001 [BE] ✅ [build] Gradle Kotlin-DSL multi-module monolith — 07
- [x] IS-002 [BE] ✅ [build] build-logic convention plugin (Java 25, Spring BOM) — 07
- [x] IS-003 [BE] ✅ [build] Version catalog — 07
- [x] IS-004 [BE] ✅ [build] Module boundary enforcement (Gradle graph + ArchUnit) — 07
- [x] IS-005 [BE] ✅ [build] Spring Boot app bootstrap — 07
- [x] IS-006 [BE] ✅ [build] Dockerfile + docker-compose (Postgres) — 07/08
- [x] IS-007 [BE] ✅ [persist] Flyway migrations V1–V6 — 04
- [x] IS-008 [BE] ✅ [persist] jOOQ codegen from migration SQL (offline) — 04
- [x] IS-009 [BE] ✅ [persist] Project/DataSource/Schema/Recording repositories — 04
- [x] IS-010 [BE] ✅ [persist] Value-timeline repository (append/range/all/count) — 04
- [x] IS-011 [BE] ✅ [model] Types: DataType/NodeKind/ValueRank/Access/Quality — 01
- [x] IS-012 [BE] ✅ [model] SchemaNode + NeutralValue — 01
- [x] IS-013 [BE] ✅ [model] ValueCodec — 01/04
- [x] IS-014 [BE] ✅ [ipc] ProtocolDataSource proto + contract version — 02
- [x] IS-015 [BE] ✅ [ipc] gRPC loopback + Hello handshake (mismatch refused) — 02
- [x] IS-016 [BE] ✅ [ipc] Configure/Start/Stop/Health RPCs — 02
- [x] IS-017 [BE] ✅ [ipc] ApplyValues client-streaming — 02
- [x] IS-018 [BE] ✅ [runtime] Supervisor lifecycle (port/launch/handshake/start/stop/track) — 02
- [x] IS-019 [BE] ✅ [runtime] WorkerClient (blocking + streaming) — 02
- [x] IS-020 [BE] ✅ [runtime] WorkerLauncher + ProcessWorkerLauncher — 02
- [x] IS-021 [BE] ✅ [runtime] RuntimeController port + in-memory default (config-wired) — 02/08
- [x] IS-022 [BE] ✅ [project] Project CRUD + optimistic concurrency — SPEC: Save/Manage Projects
- [x] IS-023 [BE] ✅ [source] DataSource CRUD (protocol/basis, JSONB config) — SPEC: Manage Data Sources
- [x] IS-024 [BE] ✅ [source] Start/stop via runtime controller — SPEC: Manage Data Sources
- [x] IS-025 [BE] ✅ [schema] Versioned schema CRUD + editor save — 01
- [x] IS-026 [BE] ✅ [schema] Node validation (kind/type/uniqueness) — 01
- [x] IS-027 [BE] ✅ [recording] Recording create/list/get + timeline capture — SPEC: Record/Store
- [x] IS-028 [BE] ✅ [replay] Replay timeline to a running source — SPEC: Replay
- [x] IS-029 [BE] ✅ [api] REST /api/v1 (project/source/schema/recording/replay) — 05
- [x] IS-030 [BE] ✅ [api] OpenAPI + Swagger UI — 05
- [x] IS-031 [BE] ✅ [api] ETag/If-Match optimistic concurrency — 05
- [x] IS-032 [BE] ✅ [api] ProblemDetail error mapping — 05
- [x] IS-033 [BE] ✅ [auth] External DB connection (env DataSource) — 08
- [x] IS-034 [BE] ✅ [auth] Deployment-mode runtime wiring — 08

## Wave A — Real runtime fidelity · P0

Turn the in-memory worker into a real OPC UA simulator (the stated core risk).

- [x] IS-035 [BE] ✅ [ipc] Real Configure: push schema + protocol listen port to the worker — 02
- [x] IS-036 [BE] ✅ [runtime] worker-opcua gRPC + lifecycle — 02
- [x] IS-037 [BE] ✅ [model] OPC UA address-space projection (schema → variable nodes) — 01
- [x] IS-038 [BE] ✅ [runtime] worker-opcua: real Eclipse Milo server + value projection — 02
- [x] IS-039 [BE] ✅ [runtime] Real worker process spawn E2E (installDist + ProcessWorkerLauncher) — 02
- [x] IS-040 [BE] ✅ [runtime] Restart-with-backoff on unexpected failure (intentional faults excluded) — 02
- [x] IS-041 [BE] ✅ [runtime] Health monitoring loop + stale/error state propagation — 02
- [x] IS-114 [BE] ✅ [runtime] Worker teardown must kill the process tree (wrapper orphans worker JVM on Windows) — 02

## Wave B — Primary flow against a real source · P0

Make `Scan → Record → Replay` work against real instruments, not provided values.

- [x] IS-042 [BE] ✅ [source] Credential handling (secrets, never persisted/exported) — 08/05
- [x] IS-116 [BE] ✅ [source] Credential-handling test hardening (stale-update touches no secret, constructor secret normalization, mode mapping) — 08/05
- [x] IS-043 [BE] ✅ [source] Create from scan (real-source discovery) — SPEC: Create From Scan
- [x] IS-044 [BE] ✅ [schema] Scan-derived schema population — 01
- [x] IS-045 [BE] ✅ [recording] Live capture from a running real source → recording — SPEC: Record Real Data

## Wave C — Observability & evidence · P0

Make runs observable and produce the P0 evidence artifact.

- [x] IS-046 [BE] ✅ [api] SSE infrastructure (live endpoints) — 05
- [x] IS-047 [BE] ✅ [ipc] ClientEvents stream (worker → supervisor) — 02
- [x] IS-048 [BE] ✅ [ipc] RuntimeEvents stream (worker → supervisor) — 02
- [x] IS-049 [BE] ✅ [persist] runtime_events repository (activity_events in Wave E) — 04
- [x] IS-050 [BE] ✅ [persist] Repos: Evidence, Run (Sample/Fault/Scenario as those land) — 04
- [x] IS-051 [BE] ✅ [observ] Live values + runtime state over SSE — SPEC: Observe Live Values
- [x] IS-052 [BE] ✅ [observ] Connected-client observation per source — SPEC: Observe Connected Clients
- [x] IS-053 [BE] ✅ [observ] Source health & error surfacing — SPEC: Observe Health
- [x] IS-054 [BE] ✅ [observ] Project overview aggregation (running/recent/attention) — SPEC: Observe Enabled/Running
- [x] IS-055 [BE] ✅ [observ] Runtime-event history — SPEC: Runtime Event History
- [x] IS-056 [BE] ✅ [persist] ObjectStore filesystem adapter — 08
- [x] IS-057 [BE] ✅ [observ] Evidence assembly + export — SPEC: Export Run Evidence (P0)
- [x] IS-058 [BE] ✅ [io] Evidence export format (bundle + JSON summary) — 06

## Wave D — Creation/reuse breadth & synthetic generation · P1

Protocol-agnostic breadth: synthetic generation, determinism, creation/reuse, and
import/export. Modbus moved to Wave G (deferred) — see the note there.

- [x] IS-061 [BE] ✅ [runtime] Resource governance (concurrent-source caps, backpressure) — 02
- [x] IS-062 [BE] ✅ [gen] Synthetic generation (patterns + range + seed) — SPEC: Generate Synthetic
- [x] IS-063 [BE] ✅ [gen] Deterministic run settings — SPEC: Run Deterministic
- [x] IS-064 [BE] ✅ [model] Injectable clock + seeded RNG — 01
- [x] IS-065 [BE] ✅ [source] Create from synthetic setup — SPEC: Manually Create / Synthetic
- [x] IS-066 [BE] ✅ [source] Duplicate data source — SPEC: Manage Data Sources
- [x] IS-067 [BE] ✅ [source] Create from import / prepared data — SPEC: Manually Create
- [x] IS-068 [BE] ✅ [recording] Samples (named subset/snapshot) — SPEC: Store Multiple/Samples
- [x] IS-069 [BE] ✅ [replay] Replay configuration (timing/ordering/compat checks) — SPEC: Replay
- [x] IS-070 [BE] ✅ [recording] Recording/sample import & export (legacy alias BE-IO2) — SPEC: Import/Export · 06
- [x] IS-071 [BE] ⬜ [project] Duplicate project — SPEC: Manage Projects
- [x] IS-072 [BE] ✅ [project] Archive project — SPEC: Manage Projects
- [x] IS-073 [BE] ✅ [io] Project export/import (versioned ZIP+manifest, secret-free) — 06
- [x] IS-074 [BE] ✅ [api] Cursor pagination + filtering on collections — 05
- [ ] IS-119 [BE] ⬜ [runtime] Run synthetic source — continuous live feed (Model B / real-time pacing); low priority, pairs with IS-069 — 02
- [x] IS-122 [BE] ✅ [api] GET /projects/{id}/active-runs — list currently running recordings/replays/scenarios for the dashboard overview panel — 05

## Wave E — Shared-team & security · P1→P2

- [x] IS-075 [BE] ✅ [auth] OIDC resource server (validate JWT via JWKS) — 08
- [ ] IS-076 [BE] ⬜ [auth] Flexible permission model + role→permission mapping — 08
- [x] IS-077 [BE] ✅ [auth] API-layer authorization enforcement (admin/user) — 08
- [x] IS-078 [BE] ✅ [auth] Local vs shared mode enforcement (flag exists, no enforcement) — 08
- [x] IS-079 [BE] ✅ [persist] Auth table repositories (users/roles/permissions/leases) — 04
- [ ] IS-080 [BE] ⬜ [auth] Advisory edit leases (read-only while editing; stale recovery) — 08
- [ ] IS-081 [BE] ⬜ [api] Edit-lease endpoints — 05
- [ ] IS-082 [BE] ⬜ [auth] Secrets via env/external store; structural export exclusion — 08
- [ ] IS-083 [BE] ⬜ [observ] User-activity audit (separate stream from runtime) — SPEC: User Activity History
- [ ] IS-084 [BE] ⬜ [project] Project + environment settings — 05
- [ ] IS-118 [BE] ⬜ [api] Admin/user-management endpoints (list users, role assignment, status; admin-only) — 05/08 · SPEC: Manage Access

## Wave F — Advanced workflows & hardening · P2

- [x] IS-085 [BE] ✅ [gen] Scenario model + steps (start/stop/replay/synthetic/fault/wait/marker) — SPEC: Build Scenarios
- [x] IS-086 [BE] ✅ [gen] Scenario validation + run execution — SPEC: Run Scenarios
- [ ] IS-087 [BE] ⬜ [gen] Fault model + injection (neutral & protocol; never auto-healed) — SPEC: Simulate Faults
- [ ] IS-088 [BE] ⬜ [ipc] InjectFault RPC — 02
- [ ] IS-089 [BE] ⬜ [api] Runs resource + test-control endpoints (start/state/stop) — 05/SPEC: Control From Tests
- [ ] IS-090 [BE] ⬜ [ipc] Shutdown RPC handling — 02
- [ ] IS-091 [BE] ⬜ [io] Artifact version compatibility (reject newer-than-supported) — 06
- [ ] IS-092 [BE] ⬜ [io] Retention & cleanup (size/age/dependency-aware) — SPEC + 06
- [x] IS-093 [BE] ✅ [persist] Value-timeline partitioning (table partition-ready) — 04
- [ ] IS-094 [BE] ⬜ [schema] Schema dependency/impact checks before save — 01
- [x] IS-095 [BE] ✅ [build] CI pipeline (build + test on push) — 07 (delivered as IS-098)
- [ ] IS-096 [BE] ⬜ [build] Self-contained local distribution (jlink/jpackage) — 07/STACK

## Wave G — Second protocol: Modbus · P2 (deferred)

Lowered from P1: Modbus is not near-term critical. The extension seam is already in
place and ArchUnit-guarded (IS-004) — the protocol-neutral model (`protocol-model`,
IS-011/012/013), the `ProtocolDataSource` worker contract (`worker-contract`,
IS-014–017), the reserved `workers/worker-modbus/` module (`07`), the specified Modbus
projection (`01 §5`), and the `/new-worker` scaffold. So this is pure worker
implementation: per `02 §1` it must not change the supervisor.

- [ ] IS-059 [BE] ⬜ [runtime] worker-modbus (j2mod) gRPC server + Modbus slave — 02
- [ ] IS-060 [BE] ⬜ [model] Modbus register-map binding — 01

## Recommended immediate next

**Wave D — synthetic generation & determinism (P1).** Waves A–C are complete: real
OPC UA runtime fidelity, the primary `Scan → Record → Replay` flow against a *real*
source, and observability + the P0 evidence export. The next P1 value is
protocol-agnostic breadth, starting with the determinism foundation that synthetic
generation builds on: injectable clock + seeded RNG (`IS-064`) → deterministic run
settings (`IS-063`) → synthetic generation (`IS-062`) → create-from-synthetic
(`IS-065`). Resource governance (`IS-061`) is independent supervisor work that can
land in parallel. Modbus (`IS-059`/`IS-060`) is deferred to Wave G.
