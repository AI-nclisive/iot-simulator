# IoT Data Source Simulator

Modular-monolith backend (Java 25 + Spring Boot) with out-of-process protocol
workers, and a React/TypeScript Web UI. See the design docs at the repo root
(`SPEC.md`, `ARCHITECTURE.md`, `STACK.md`), the UI docs under `frontend/docs/`
(`DESIGN.md` and others), and the implementation specs in `backend-specs/`.

## Module map

Dependencies flow downward only (`backend-specs/07_MODULE_STRUCTURE.md`):

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

| Tool | Version | Used for |
| --- | --- | --- |
| JDK | 25 (toolchain target) | Backend build & run. Gradle itself may run on JDK 17+ or 25; the build provisions a JDK 25 toolchain to compile/run. |
| Docker | Docker Desktop / colima | Postgres for local runs, and Testcontainers integration tests (ITs skip locally if Docker is absent; CI always runs them). |
| Node.js | 20 (with npm) | Frontend dev server, tests, and build (React + Vite). Pinned in `.nvmrc`. |

The Gradle wrapper (`./gradlew`) is committed — no separate Gradle install needed.

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

```bash
./gradlew build      # backend: compile + all tests (unit + ITs + ArchUnit)
npm run typecheck    # frontend: TypeScript check
npm test             # frontend: Vitest suite
```

Integration tests use Testcontainers and need Docker running; they skip silently
under `./gradlew build` if Docker is absent (CI always runs them).

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
| `IOTSIM_MODE` | `local` | `local` = auth off (implicit `local` principal); `shared` = OAuth2/OIDC resource server (set `spring.security.oauth2.resourceserver.jwt.issuer-uri`). See `backend-specs/08_AUTH_AND_MODES.md`. |
| `IOTSIM_RUNTIME_MODE` | `memory` | `memory` = no workers (default for dev); `supervisor` = real out-of-process protocol workers. |

**Frontend** (`.env.example`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | empty | Base URL for API calls. Leave **unset in dev** so requests stay relative (`/api/...`) and go through the Vite proxy. Only set it for a production static build deployed on a different origin than the API. |

### Useful Gradle tasks

```bash
./gradlew :persistence:generateJooq         # regenerate jOOQ types (needs Docker; off the default build)
./gradlew :workers:worker-opcua:installDist # package a worker for supervisor mode
./gradlew :app:bootJar                       # build the runnable app jar (used by the Dockerfile)
```

## Project tracking

- **Board:** [IoT Simulator](https://github.com/orgs/AI-nclisive/projects/1) —
  live status by `IS-XXX` / `Area` (Todo / In Progress / In review / Done).
- **Task catalog:** [`backend-specs/TASKS.md`](backend-specs/TASKS.md) — the source list of `IS-XXX` task IDs.

## Notes

- gRPC/protobuf code is generated from `worker-contract/src/main/proto`.
- jOOQ code generation runs against a Flyway-migrated Testcontainers Postgres and
  is a separate task (kept off the default build so `build` stays offline).
