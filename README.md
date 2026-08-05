# IoT Data Source Simulator

Modular-monolith backend (Java 25 + Spring Boot) with out-of-process protocol
workers, and a React/TypeScript Web UI. See the design docs at the repo root
(`ARCHITECTURE.md`, `STACK.md`), `frontend/docs/UI_PLAN.md`, and the living
capability specs under `openspec/specs/`.

## Table of contents

- [Module map](#module-map)
- [Developer setup](#developer-setup)
- [Run it locally](#run-it-locally)
  - [Database data persistence](#database-data-persistence)
  - [Backend only](#backend-only)
  - [Frontend only](#frontend-only)
- [Test locally](#test-locally)
- [API & OpenAPI documentation](#api--openapi-documentation)
- [Configuration (environment variables)](#configuration-environment-variables)
  - [Useful Gradle tasks](#useful-gradle-tasks)
- [Specs & workflow (OpenSpec)](#specs--workflow-openspec)
- [Project tracking](#project-tracking)
- [Device templates](#device-templates)
- [Debug tools](#debug-tools)
- [Notes](#notes)

## Module map

Dependencies flow downward only (`openspec/specs/module-structure/spec.md`):

| Module | Role |
| --- | --- |
| `protocol-model` | Protocol-neutral schema & value model (shared kernel) |
| `worker-contract` | `ProtocolDataSource` contract + gRPC IPC |
| `platform` | Cross-cutting ports (object store, clock, ids, secrets) |
| `persistence` | Flyway migrations, jOOQ, repositories |
| `domain` | Projects, schemas, recordings, scenarios, faults, evidence |
| `runtime-supervisor` | Worker lifecycle, IPC, health, ports |
| `api` | REST/OpenAPI + SSE; authz enforcement |
| `app` | Spring Boot bootstrap |
| `workers/worker-opcua` | OPC UA worker (Eclipse Milo), lean JVM |
| `workers/worker-modbus` | Modbus TCP worker (j2mod), lean JVM |

The frontend (React + Vite) lives in `frontend/`, with its build config and
`package.json` at the repo root.

## Developer setup

### Prerequisites

JDK 25, Node.js 20, Docker — see
[`CONTRIBUTING.md` → Prerequisites](CONTRIBUTING.md#prerequisites). The Gradle
wrapper (`./gradlew`) is committed, so no separate Gradle install is needed.

### Get the code & install dependencies

```bash
git clone https://github.com/AI-nclisive/iot-simulator.git
cd iot-simulator
nvm use        # optional, uses Node 20 from .nvmrc when nvm/fnm/asdf is installed
npm ci         # install locked frontend dependencies
```

## Run it locally

There are three common ways to run the project. For day-to-day development with
the UI, use **the full stack (backend + frontend)** below.

### Quick start — full stack (backend + frontend) for end-to-end testing

This is the setup you want to click through the UI against a real backend:

```bash
# 1. Start Postgres (in the background)
docker compose up -d postgres                       # localhost:5432, db/user/pass = iotsim

# 2. Start the backend (new terminal) — Flyway migrates the DB on startup
./gradlew :app:bootRun                              # http://localhost:8080

# 3. Start the frontend dev server (new terminal)
npm run dev                                         # http://localhost:4173
```

Open **http://localhost:4173** in a browser. The Vite dev server proxies every
`/api` request to the backend on `:8080` (configured in `vite.config.ts`), so the
UI and API share an origin and there are no CORS issues. Because the backend runs
in `local` mode by default (auth off), no login or token is needed.

Vite binds to `0.0.0.0`, so the same dev server can be opened from another device
on the LAN via `http://<your-lan-ip>:4173/`.

> `run-local.sh` (macOS/Linux) / `run-local.ps1` (Windows) automate this whole
> stack and run it in **supervisor mode** — real out-of-process OPC UA workers —
> so starting a data source spawns an Eclipse Milo server an edge device can
> connect to (`opc.tcp://127.0.0.1:<listenPort>/iotsim`):
>
> ```bash
> ./run-local.sh          # up
> ./run-local.sh down     # down (keeps DB data)
> ```
>
> The **`/run-local`** Claude Code skill does the same thing conversationally. The
> manual steps above run the app's default `memory` mode (no workers) — fine for
> UI/API clicking, but there is no real protocol endpoint.

### Database data persistence

Postgres data **survives restarts** — it lives in the named Docker volume
`pgdata` (`docker-compose.yml`), not inside the container. So `docker compose up`,
`stop`, `restart`, and `down` all keep your data, and restarting the backend only
applies new Flyway migrations without touching existing rows.

To wipe the database and start clean (e.g. to replay all migrations from scratch):

```bash
docker compose down -v && docker compose up -d postgres   # -v removes the pgdata volume
```

### Backend only

**Option A — full stack in Docker** (app + Postgres, nothing else to install):

```bash
docker compose up --build
```

App on http://localhost:8080, Postgres on `localhost:5432` (db/user/password
`iotsim`). Stop with `docker compose down` (add `-v` to also drop the DB volume).

**Option B — run the app from Gradle against a local Postgres** (faster
iteration, hot rebuilds). Start only the database, then boot the app:

```bash
docker compose up -d postgres      # Postgres on localhost:5432
./gradlew :app:bootRun             # app on localhost:8080
```

The app's default datasource (`localhost:5432/iotsim`, user/password `iotsim`)
already matches that container, so no extra config is needed. Flyway applies the
migrations on startup.

### Frontend only

The React/TypeScript/Vite UI lives in `frontend/` (config and `package.json` at
the repo root). From the repo root:

```bash
nvm use            # optional, uses Node 20 from .nvmrc
npm ci             # first time only
npm run dev        # Vite dev server on http://localhost:4173
```

The dev server calls the backend through the `/api` proxy, so for anything beyond
static rendering you'll want the backend running too (see the quick start above).
Without a backend the requests fail and data-backed views stay empty.

Frontend scripts:

| Script | What it does |
| --- | --- |
| `npm run dev` | Vite dev server on http://localhost:4173 (proxies `/api` → `:8080`). |
| `npm run build` | Production bundle. |
| `npm run preview` | Serve the production build locally. |
| `npm run typecheck` | `tsc --noEmit` against `tsconfig.app.json`. |
| `npm test` | Run the Vitest unit/component suite. |
| `npm run generate:api` | Regenerate `frontend/src/generated/api.ts` from a running backend's `/openapi.json` (start the backend first). |

## Test locally

The command set and what must be green before a PR live in
[`CONTRIBUTING.md` → Build & test](CONTRIBUTING.md#build--test). Integration
tests use Testcontainers and need Docker running; they skip silently under
`./gradlew build` if Docker is absent (CI always runs them).

## API & OpenAPI documentation

Once the backend is up on port 8080:

| What | URL |
| --- | --- |
| REST API base (path-versioned) | http://localhost:8080/api/v1 |
| **Swagger UI** (interactive docs) | http://localhost:8080/swagger-ui.html |
| **OpenAPI spec** (JSON) | http://localhost:8080/openapi.json |
| Actuator health | http://localhost:8080/actuator/health |

The docs and health endpoints stay public even in `shared` (auth) mode. The
OpenAPI document is generated from the controllers via springdoc — keep public
behavior changes reflected there.

## Configuration (environment variables)

**Backend** reads its config from the environment (defaults match
`docker-compose.yml`), so the same build targets a local container or a managed
Postgres:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/iotsim` | JDBC URL for Postgres. |
| `DB_USER` / `DB_PASSWORD` | `iotsim` / `iotsim` | DB credentials. |
| `SERVER_PORT` | `8080` | HTTP port. |
| `IOTSIM_MODE` | `local` | `local` = auth off (implicit `local` principal); `shared` = OAuth2/OIDC resource server (set `spring.security.oauth2.resourceserver.jwt.issuer-uri`). See `openspec/specs/auth-modes/spec.md`. |
| `IOTSIM_RUNTIME_MODE` | `memory` | `memory` = no workers (app default for dev/tests); `supervisor` = real out-of-process protocol workers (the `/run-local` skill runs this). |

**Frontend** (`.env.example`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | unset → empty | Base URL for API calls. Leave it **unset in dev** so requests stay relative (`/api/...`) and go through the Vite proxy — no `.env` file is committed, so that is the default. Only set it for a production static build served from a different origin than the API; `.env.example` shows that form (`http://localhost:8080`) and is a template, not a loaded file. |

### Useful Gradle tasks

```bash
./gradlew :persistence:generateJooq         # regenerate jOOQ types (needs Docker; off the default build)
./gradlew :workers:worker-opcua:installDist # package a worker for supervisor mode
./gradlew :app:bootJar                       # build the runnable app jar (used by the Dockerfile)
```

## Specs & workflow (OpenSpec)

This project is spec-driven via [OpenSpec](https://github.com/Fission-AI/OpenSpec).
Three paths carry the whole picture:

| Path | What it holds |
| --- | --- |
| `openspec/specs/<capability>/spec.md` | **What the system does today** — the living behavior contract, one file per capability. |
| `openspec/changes/<id>-<slug>/` | **What is changing now** — one in-flight task's `proposal.md`, `specs/` delta, `design.md`, `tasks.md`. |
| `openspec/changes/archive/` | Completed changes, kept for history. |

Capabilities: `protocol-model`, `worker-contract`, `domain-model`, `db-schema`,
`api-contract`, `artifact-formats`, `module-structure`, `auth-modes`,
`frontend-shell`, `frontend-screens`.

The CLI is a pinned devDependency (`npm ci` installs it) — invoke it as
`npx openspec` from the repo root:

```bash
npx openspec list --specs                 # what capabilities exist
npx openspec spec show api-contract       # read one capability
npx openspec list                         # changes currently in flight
npx openspec validate --strict            # check specs + changes are well-formed
```

### The loop for one task

```
/start-task IS-038      →  claim on the board, create the linked branch,
                           and propose the change (openspec/changes/is-038-…/)
/opsx:propose            →  proposal.md → specs delta → design.md → tasks.md
   ...implement...       →  work tasks.md; keep the spec delta honest
/open-pr                 →  DoD checks, `npx openspec archive is-038-…`,
                           open the PR, arm auto-merge, board → In review
/review-loop             →  work the automated review until it approves
```

Two rules bite on a first change — never hand-editing `openspec/specs/*.md`, and
only `### Requirement:` blocks surviving archive. They are stated once, in
[`AGENTS.md` → Working with openspec](AGENTS.md#working-with-openspec); the rest
of the workflow (including the CI gate that pairs `Implements: IS-XXX` with an
archived change) is in
[`CONTRIBUTING.md` → Spec workflow](CONTRIBUTING.md#spec-workflow).

## Project tracking

- **Board:** [IoT Simulator](https://github.com/orgs/AI-nclisive/projects/1) —
  live status by `IS-XXX` / `Area` (Todo / In Progress / In review / Done).
- **Capability specs:** [`openspec/specs/`](openspec/specs/) — what the system does today.
- **Change history:** [`openspec/changes/archive/`](openspec/changes/archive/) — completed `IS-XXX`/`UI-XXX` tasks.

## Device templates

[`docs/TEMPLATES_GUIDE.md`](docs/TEMPLATES_GUIDE.md) describes the 15 OPC UA device
templates the manual schema editor offers. The definitions live in
`domain/.../manualschema/OpcUaTemplates.java` and are the source of truth.

## Debug tools

- **`OpcUaScanTool`** (`workers/worker-opcua/src/test/java/.../OpcUaScanTool.java`)
  — connects to a real OPC UA server via this repo's own `OpcUaDiscovery` and
  prints its address space (tree + per-type node counts). Handy for diagnosing
  "wizard shows 0/wrong nodes" reports without going through the full app. It's
  a manual utility (no `@Test` methods, never run by `./gradlew test`), so
  compile it once and run it directly:

  ```bash
  ./gradlew :workers:worker-opcua:installDist :workers:worker-opcua:compileTestJava
  java -cp "workers/worker-opcua/build/classes/java/test:workers/worker-opcua/build/install/worker-opcua/lib/*" \
    com.ainclusive.iotsim.worker.opcua.OpcUaScanTool "opc.tcp://host:4840/path" 50
  ```

  The optional second argument caps the number of browsed nodes (default: 5000);
  use a small cap when testing a shared public server. Omit the endpoint argument to default to the public Prosys demo server
  (`opc.tcp://uademo.prosysopc.com:53530/OPCUA/SimulationServer`).

## Notes

- gRPC/protobuf code is generated from `worker-contract/src/main/proto`.
- jOOQ code generation runs against a Flyway-migrated Testcontainers Postgres and
  is a separate task (kept off the default build so `build` stays offline).
