# IoT Data Source Simulator

Modular-monolith backend (Java 25 + Spring Boot) with out-of-process protocol
workers. See the design docs at the repo root (`SPEC.md`, `ARCHITECTURE.md`,
`STACK.md`), the UI docs under `frontend/docs/` (`DESIGN.md` and others), and the
implementation specs in `backend-specs/`.

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

## Developer setup

### Prerequisites

| Tool | Version | Used for |
| --- | --- | --- |
| JDK | 25 (toolchain target) | Backend build & run. Gradle itself may run on JDK 17+ or 25; the build provisions a JDK 25 toolchain to compile/run. |
| Docker | Docker Desktop / colima | Postgres for local runs, and Testcontainers integration tests (ITs skip locally if Docker is absent; CI always runs them). |
| Node.js | 18+ (with npm) | Frontend dev server (React + Vite). |

The Gradle wrapper (`./gradlew`) is committed — no separate Gradle install needed.

### Get the code & build

```bash
git clone https://github.com/AI-nclisive/iot-simulator.git
cd iot-simulator
./gradlew build        # compile + all tests (unit + ITs + ArchUnit)
```

### Run the backend

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

### API & OpenAPI documentation

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

### Run the frontend (UI)

The React/TypeScript/Vite UI lives in `frontend/` (config and `package.json` at
the repo root). From the repo root:

```bash
npm install
npm run dev        # Vite dev server on http://localhost:4173
```

Other scripts: `npm run build` (production bundle), `npm run preview` (serve the
build), `npm run typecheck` (`tsc --noEmit`). The dev server currently runs
standalone on in-memory data and does not yet call the backend API.

### Configuration (environment variables)

The backend reads its config from the environment (defaults match
`docker-compose.yml`), so the same build targets a local container or a managed
Postgres:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/iotsim` | JDBC URL for Postgres. |
| `DB_USER` / `DB_PASSWORD` | `iotsim` / `iotsim` | DB credentials. |
| `SERVER_PORT` | `8080` | HTTP port. |
| `IOTSIM_MODE` | `local` | `local` = auth off (implicit `local` principal); `shared` = OAuth2/OIDC resource server (set `spring.security.oauth2.resourceserver.jwt.issuer-uri`). See `backend-specs/08_AUTH_AND_MODES.md`. |
| `IOTSIM_RUNTIME_MODE` | `memory` | `memory` = no workers (default for dev); `supervisor` = real out-of-process protocol workers. |

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
