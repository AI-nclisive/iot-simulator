---
name: run-local
description: >-
  Bring up the full local stack — Postgres + backend (:8080, supervisor mode with
  real OPC UA workers) + frontend dev server (:4173) — for manual end-to-end
  testing in a browser: create/start a data source and connect an OPC UA client to
  the worker it spawns. Cross-platform (macOS/Linux/Windows). Invoke as `/run-local`
  (add `down` to tear it back down).
---

# Run the local e2e stack (backend + frontend)

Brings up the processes the UI needs to talk to a real backend. Runs
`IOTSIM_MODE=local` (auth off) and `IOTSIM_RUNTIME_MODE=supervisor` — so starting a
data source spawns a **real out-of-process OPC UA worker** (Eclipse Milo) that an
edge device can connect to, not the in-memory stub. The Vite dev server proxies
`/api` → `:8080` (`vite.config.ts`), so the browser hits one origin.

(The app's own default is `IOTSIM_RUNTIME_MODE=memory` — no workers — which tests
use and which you can still run by hand; this skill deliberately runs supervisor so
the platform behaves as it does in real use.)

**Argument:** none = bring the stack **up**. `down` = tear it down (see last
section).

## Platform

Works on **macOS, Linux, and Windows**. Use the command set matching the
session's OS (the environment reports the platform). The important part:

- **`docker compose`, `npm`, and `curl` are identical on every OS** — `curl` ships
  with Windows 10+. Only shell **loops**, the **Gradle wrapper**, **port checks**,
  and **process kill** differ; those are given as **bash** (macOS/Linux) and
  **PowerShell** (Windows) below.
- **Launch the long-lived processes (backend, frontend) via the agent's
  background-run capability**, not a raw `&`/`Start-Process`. That gives
  "detached + survives the turn + logged" the same way on all platforms, and lets
  you stop them by task id at teardown.

## 0. Preflight

- **Docker running?** Docker Desktop (Windows/macOS) or Colima (macOS —
  [[testcontainers-colima-env]]). `docker info` must succeed; if not, ask the user
  to start it (`colima start` / launch Docker Desktop) — don't start it yourself.
- **Compose command.** The commands below use `docker compose` **literally**
  (Docker Desktop ships the v2 plugin on Win/macOS). Confirm it works:
  `docker compose version`. If a machine only has the standalone `docker-compose`
  (some macOS/Homebrew setups), use **that** form throughout instead. Do **not**
  stash it in a shell variable: each step runs in its own shell (a var wouldn't
  survive), and zsh doesn't word-split unquoted `$COMPOSE` — `$COMPOSE up …`
  becomes a `command not found: docker compose`.
- **Frontend deps present?** If `node_modules/` is missing → `npm ci` (same on all OSes).
- **Already up?** If `curl -fsS http://localhost:8080/actuator/health` shows
  `"status":"UP"` and `:4173` answers, the stack is already running — just report
  the URLs, don't double-start. If a port is held by something else, say so and stop.

## 1. Start Postgres (data persists in the `pgdata` volume)

```bash
docker compose up -d postgres          # localhost:5432, db/user/pass = iotsim
```

Wait until it accepts connections — runs `pg_isready` **inside** the container
(identical command; only the loop differs). Don't use `ps --format {{.Health}}`
(v2-only flag, errors on the standalone binary):

```bash
# bash (macOS/Linux)
until docker compose exec -T postgres pg_isready -U iotsim >/dev/null 2>&1; do sleep 1; done
```
```powershell
# PowerShell (Windows)
while ($true) { docker compose exec -T postgres pg_isready -U iotsim 2>$null; if ($LASTEXITCODE -eq 0) { break }; Start-Sleep 1 }
```

## 2. Build the OPC UA worker (once per worker-code change)

Supervisor mode launches an out-of-process worker; package it so the supervisor can
spawn it. Skip if `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua`
already exists and the worker code is unchanged.

```bash
./gradlew :workers:worker-opcua:installDist
```

The launch script lands at `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua`
(`worker-opcua.bat` on Windows). Note its **absolute** path for the next step.

## 3. Start the backend (background, supervisor mode), then poll health

Launch via the agent's background-run capability and capture logs — never block
the session. Flyway migrates the DB on startup. Set supervisor mode + the OPC UA
worker command via env (inherited by `bootRun`); substitute `<WORKER>` with the
absolute path from step 2.

```bash
# bash — <WORKER> = <repo>/workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua
export IOTSIM_MODE=local
export IOTSIM_RUNTIME_MODE=supervisor
export SPRING_APPLICATION_JSON='{"iotsim":{"runtime":{"workers":{"OPC_UA":["<WORKER>"]}}}}'
./gradlew :app:bootRun > /tmp/iotsim-backend.log 2>&1
```
```powershell
# PowerShell — <WORKER> = <repo>/workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua.bat
#              (use forward slashes inside the JSON so the string stays valid)
$env:IOTSIM_MODE = 'local'
$env:IOTSIM_RUNTIME_MODE = 'supervisor'
$env:SPRING_APPLICATION_JSON = '{"iotsim":{"runtime":{"workers":{"OPC_UA":["<WORKER>"]}}}}'
.\gradlew.bat :app:bootRun *> $env:TEMP\iotsim-backend.log
```

Poll `/actuator/health` until `UP` (first run compiles — allow ~2 min):

```bash
# bash
until curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; do sleep 2; done
```
```powershell
# PowerShell
while ($true) { try { if ((Invoke-RestMethod http://localhost:8080/actuator/health).status -eq 'UP') { break } } catch {}; Start-Sleep 2 }
```

If it never comes up, read the backend log and report the cause (common: port 8080
busy, Postgres not healthy).

## 4. Start the frontend dev server (background)

```bash
# bash
npm run dev > /tmp/iotsim-frontend.log 2>&1
```
```powershell
# PowerShell
npm run dev *> $env:TEMP\iotsim-frontend.log
```

Wait for Vite to be ready:

```bash
# bash
until curl -fsS http://localhost:4173 >/dev/null; do sleep 1; done
```
```powershell
# PowerShell
while ($true) { try { Invoke-WebRequest http://localhost:4173 -UseBasicParsing > $null; break } catch { Start-Sleep 1 } }
```

## 5. Verify the wiring, then report to the user

Prove the browser→proxy→backend→DB path works (in `local` mode no token is needed).
`curl` works on all OSes; PowerShell alt given:

```bash
curl -fsS http://localhost:4173/api/v1/projects          # proxied to :8080 → {"items":[...]}
```
```powershell
Invoke-RestMethod http://localhost:4173/api/v1/projects   # → object with .items
```

Then tell the user the stack is up and give the entry points:

- **UI (use this):** http://localhost:4173
- Swagger UI: http://localhost:8080/swagger-ui.html
- API base: http://localhost:8080/api/v1 · Health: http://localhost:8080/actuator/health
- Logs: `/tmp/iotsim-*.log` (bash) or `%TEMP%\iotsim-*.log` (Windows)
- **Real OPC UA workers:** starting an `OPC_UA` data source in the UI spawns a Milo
  worker that binds the source's `runtimeConfig.listenPort` and serves
  `opc.tcp://127.0.0.1:<port>/iotsim` — point an external OPC UA client (the edge
  device under test) there. No `listenPort` set → the port is ephemeral and not
  surfaced, so set one when creating the source (IS-124).

## Tear down (`/run-local down`)

Stop the two background tasks you started (by task id — cleanest, cross-platform),
then stop Postgres. **Keep the DB data by default** — `stop`, not `down -v`:

```bash
docker compose stop postgres           # keeps the pgdata volume
```

Manual kill if needed — **by listening port**, because `bootRun` forks the app
into a child JVM whose command line has no "bootRun" in it (matching on the process
name/args misses it and leaves :8080 held):

```bash
# bash — kill whatever listens on the backend + frontend ports
for p in 8080 4173; do lsof -ti tcp:$p | xargs kill 2>/dev/null; done
```
```powershell
# PowerShell
Get-NetTCPConnection -LocalPort 8080,4173 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

> **Windows — also stop worker processes.** Graceful Shutdown RPC (IS-090) is
> implemented: on bash, plain `kill` sends SIGTERM, Spring runs its shutdown hooks,
> and `Supervisor.closeQuietly()` asks each worker to exit before closing the
> channel — no manual cleanup needed. `Stop-Process -Force` on PowerShell is a hard
> kill that bypasses that graceful path entirely, so Windows still needs an explicit
> kill of lingering workers:
> - PowerShell: `Get-Process worker-opcua -ErrorAction SilentlyContinue | Stop-Process -Force`

Only wipe the database when the user explicitly wants a clean slate:
`docker compose down -v`.

## Troubleshooting

- **Port already in use (8080/4173/5432):** something is already running — reuse it
  or stop it. Check with:
  - bash: `lsof -nP -iTCP:<port> -sTCP:LISTEN`
  - PowerShell: `Get-NetTCPConnection -LocalPort <port> -State Listen` (or `netstat -ano | findstr :<port>`)
- **Backend health never UP:** read the backend log. Postgres not healthy or a
  Flyway migration failure are the usual causes.
- **UI loads but data views are empty / network errors:** the backend isn't up or
  the proxy target changed — confirm step 5's request returns JSON with `items`.
- **Source shows RUNNING but no OPC UA endpoint / client can't connect:** the worker
  didn't spawn — check the worker command path in `SPRING_APPLICATION_JSON` (step 3)
  and that `:workers:worker-opcua:installDist` ran; confirm a `worker-opcua` process
  is up (`pgrep -fl worker-opcua`) and the source has a `runtimeConfig.listenPort`.
