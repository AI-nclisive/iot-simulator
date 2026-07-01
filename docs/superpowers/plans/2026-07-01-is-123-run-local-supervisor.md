# IS-123 `/run-local` = supervisor mode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax. **This is a docs-only change** (a skill markdown file + README) — there is no compiled code and no unit test; verification is a real `/run-local` run.

**Goal:** Make `/run-local` bring the platform up in **supervisor mode** (real out-of-process OPC UA workers) instead of the memory stub, so that starting a data source in the UI spawns a real Milo OPC UA server an edge device can connect to.

**Architecture:** Rewrite `.claude/skills/run-local/SKILL.md`: build the OPC UA worker (`installDist`), launch the backend with `IOTSIM_RUNTIME_MODE=supervisor` + the worker command wired via `SPRING_APPLICATION_JSON`, and teardown that also kills lingering worker processes (stopgap until IS-090). Touch README to match. No auto-created source (the user drives the UI). Builds on IS-124 (deterministic `runtimeConfig.listenPort`).

**Tech Stack:** Claude Code skill (markdown); Gradle (`installDist`, `bootRun`); Spring Boot config via env (`SPRING_APPLICATION_JSON`, `IOTSIM_RUNTIME_MODE`); docker compose; cross-platform bash + PowerShell.

## Global Constraints

- `/run-local` is **supervisor-only** — the memory path is removed from the skill (memory stays the app default `IOTSIM_RUNTIME_MODE:memory` for tests / manual runs; not offered by the skill).
- Worker command binds to `iotsim.runtime.workers.OPC_UA[0]`; `RuntimeProperties(mode, Map<String,List<String>> workers, …)` reads it, and `RuntimeConfig` builds the real `Supervisor` when `mode=supervisor`.
- Inject config via env inherited by `:app:bootRun`: `IOTSIM_MODE=local`, `IOTSIM_RUNTIME_MODE=supervisor`, and `SPRING_APPLICATION_JSON={"iotsim":{"runtime":{"workers":{"OPC_UA":["<abs path>"]}}}}`.
- Worker binary: `./gradlew :workers:worker-opcua:installDist` → `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua` (`.bat` on Windows).
- The skill does NOT create/start any source; starting a source in the UI is what spawns the worker.
- Teardown kills lingering `worker-opcua` processes (stopgap until IS-090; the teardown STOPGAP note already committed in `fb72527`).
- Cross-platform: give bash (macOS/Linux) and PowerShell (Windows) forms, matching the file's existing style.
- Docs-only: CI `build` passes trivially; catalog `IS-123` line already exists on master — flip it `[x]` in the PR via `/open-pr`.

---

## File Structure

```
.claude/skills/run-local/SKILL.md   (modify) — frontmatter desc, intro, +worker-build step,
                                     backend-launch env, report note, teardown worker-kill,
                                     drop the "not covered here" memory note
README.md                           (modify) — /run-local now runs supervisor; keep IOTSIM_RUNTIME_MODE row
```

---

## Task 1: Rewrite the `/run-local` skill for supervisor mode

**Files:**
- Modify: `.claude/skills/run-local/SKILL.md`

- [ ] **Step 1: Update the frontmatter `description`**

Replace lines 3-9 (the `description:` block) so it reflects the real supervisor stack:
```yaml
description: >-
  Bring up the full local stack — Postgres + backend (:8080, supervisor mode with
  real OPC UA workers) + frontend dev server (:4173) — for manual end-to-end
  testing in a browser: create/start a data source and connect an OPC UA client to
  the worker it spawns. Cross-platform (macOS/Linux/Windows). Invoke as `/run-local`
  (add `down` to tear it back down).
```

- [ ] **Step 2: Rewrite the intro (lines 14-21)**

Replace the "Default modes only: … memory (no protocol workers) …" paragraph with:
```markdown
Brings up the processes the UI needs to talk to a real backend. Runs
`IOTSIM_MODE=local` (auth off) and `IOTSIM_RUNTIME_MODE=supervisor` — so starting a
data source spawns a **real out-of-process OPC UA worker** (Eclipse Milo) that an
edge device can connect to, not the in-memory stub. The Vite dev server proxies
`/api` → `:8080` (`vite.config.ts`), so the browser hits one origin.

(The app's own default is `IOTSIM_RUNTIME_MODE=memory` — no workers — which tests
use and which you can still run by hand; this skill deliberately runs supervisor so
the platform behaves as it does in real use.)

**Argument:** none = bring the stack **up**. `down` = tear it down (see last section).
```

- [ ] **Step 3: Add a "Build the OPC UA worker" step before the backend start**

Insert a new section between "## 1. Start Postgres" and "## 2. Start the backend" (renumber the backend/frontend/verify sections +1, i.e. backend becomes step 3, frontend 4, verify 5):
````markdown
## 2. Build the OPC UA worker (once per code change)

Supervisor mode launches an out-of-process worker; package it so the supervisor can
spawn it. Skip if `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua`
already exists and the worker code is unchanged.

```bash
./gradlew :workers:worker-opcua:installDist
```

The launch script lands at `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua`
(`worker-opcua.bat` on Windows). Note its **absolute** path for the next step.
````

- [ ] **Step 4: Wire supervisor env into the backend launch (now step 3)**

Replace the backend-launch code blocks so the env is set. `SPRING_APPLICATION_JSON` carries the worker command (list binding is unambiguous cross-platform); `IOTSIM_RUNTIME_MODE`/`IOTSIM_MODE` are plain env. Use the absolute worker path from Step 3:
````markdown
Launch via the agent's background-run capability and capture logs — never block the
session. Set supervisor mode + the OPC UA worker command via env (inherited by
`bootRun`). Substitute `<WORKER>` with the absolute path from Step 2.

```bash
# bash — <WORKER> = <repo>/workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua
export IOTSIM_MODE=local
export IOTSIM_RUNTIME_MODE=supervisor
export SPRING_APPLICATION_JSON='{"iotsim":{"runtime":{"workers":{"OPC_UA":["<WORKER>"]}}}}'
./gradlew :app:bootRun > /tmp/iotsim-backend.log 2>&1
```
```powershell
# PowerShell — <WORKER> = <repo>\workers\worker-opcua\build\install\worker-opcua\bin\worker-opcua.bat
$env:IOTSIM_MODE = 'local'
$env:IOTSIM_RUNTIME_MODE = 'supervisor'
$env:SPRING_APPLICATION_JSON = '{"iotsim":{"runtime":{"workers":{"OPC_UA":["<WORKER>"]}}}}'
.\gradlew.bat :app:bootRun *> $env:TEMP\iotsim-backend.log
```

(Windows JSON path: use forward slashes or escaped backslashes inside the JSON string
so it stays valid, e.g. `.../bin/worker-opcua.bat`.)
````
Keep the existing health-poll blocks unchanged.

- [ ] **Step 5: Add the supervisor endpoint note to the "verify + report" section**

In the report section (the "You now have:" URL list), add a bullet:
```markdown
- **Real OPC UA workers:** starting an `OPC_UA` data source in the UI spawns a Milo
  worker that binds the source's `runtimeConfig.listenPort` and serves
  `opc.tcp://127.0.0.1:<port>/iotsim` — point an external OPC UA client there. (No
  `listenPort` set → the port is ephemeral and not surfaced; set one when creating
  the source. IS-124.)
```

- [ ] **Step 6: Make teardown actually kill workers; drop the stale memory note**

In "## Tear down", after the port-kill blocks (the STOPGAP `>` note is already there
from `fb72527`), add the actual kill commands to the `down` procedure:
```markdown
```bash
# bash — stop lingering supervisor workers (stopgap until IS-090)
pkill -f worker-opcua 2>/dev/null || true
```
```powershell
# PowerShell
Get-Process worker-opcua -ErrorAction SilentlyContinue | Stop-Process -Force
```
```
Then find and **remove** the note that defers real workers (currently near the end,
the bullet reading roughly *"Need real protocol workers (OPC UA / Modbus), not the
memory runtime: run with `IOTSIM_RUNTIME_MODE=supervisor` … not covered here."*) —
it is now the default path, so the note is obsolete.

- [ ] **Step 7: Commit**

```bash
git add .claude/skills/run-local/SKILL.md
git commit -m "feat(IS-123): /run-local brings up supervisor mode (real OPC UA workers)"
```

---

## Task 2: README touch-up

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Adjust the `/run-local` / Configuration references**

- In the "Configuration (environment variables)" section, keep the `IOTSIM_RUNTIME_MODE` row (memory is still the app default and a valid manual choice), but add a sentence that **`/run-local` runs `supervisor`** (real workers) while the app default remains `memory`.
- If a "Run it locally" / "Quick start" section references `/run-local` as memory-only, update it to say it now runs the supervisor stack (build the worker via the already-listed `:workers:worker-opcua:installDist`, then `/run-local`).

Keep edits minimal and factual — do not restructure the README.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs(IS-123): note /run-local runs supervisor mode"
```

---

## Task 3: Verify by running, then open the PR

- [ ] **Step 1: Real verification run** (the DoD — this is not code, so this is the test)

From the repo root, with Docker (Colima) up:
```bash
./gradlew :workers:worker-opcua:installDist
WORKER="$(pwd)/workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua"
docker compose up -d postgres
export IOTSIM_MODE=local IOTSIM_RUNTIME_MODE=supervisor
export SPRING_APPLICATION_JSON="{\"iotsim\":{\"runtime\":{\"workers\":{\"OPC_UA\":[\"$WORKER\"]}}}}"
./gradlew :app:bootRun > /tmp/iotsim-backend.log 2>&1 &   # background
# poll health
until curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; do sleep 2; done
```
Then confirm supervisor mode is active end to end:
```bash
# create a synthetic OPC_UA source with a fixed listen port
curl -fsS -X POST http://localhost:8080/api/v1/projects -H 'Content-Type: application/json' -d '{"name":"local-e2e"}'   # note the project id
# (use the returned project id below as PID)
curl -fsS -X POST "http://localhost:8080/api/v1/projects/$PID/data-sources" -H 'Content-Type: application/json' \
  -d '{"name":"opcua-1","protocol":"OPC_UA","basis":"MANUAL","runtimeConfig":"{\"listenPort\":4840}"}'   # note the source id SID
curl -fsS -X POST "http://localhost:8080/api/v1/projects/$PID/data-sources/$SID/start"
# verify a worker process is up and bound the port
pgrep -fl worker-opcua
lsof -nP -iTCP:4840 -sTCP:LISTEN
```
Expected: the `/start` returns 200, a `worker-opcua` process is running, and something is listening on `4840` (`opc.tcp://127.0.0.1:4840/iotsim`). If the exact create-source payload differs from the real API (field names/enums), adjust to the live OpenAPI (`http://localhost:8080/swagger-ui.html`) — the point is to confirm a real worker + bound endpoint, which is the whole IS-123 deliverable.

Tear down:
```bash
for p in 8080 4173; do lsof -ti tcp:$p | xargs kill 2>/dev/null; done
pkill -f worker-opcua 2>/dev/null || true
docker compose stop postgres
```
Record the observed result (worker PID, listening port) in the PR description. If anything in the skill's documented steps was wrong, fix the skill and re-verify before opening the PR.

- [ ] **Step 2: Open the PR**

Hand off to `/open-pr` — it flips the `IS-123` catalog line `[x]` in `backend-specs/TASKS.md` (the line exists on master), creates the PR with `Implements: IS-123`, arms squash auto-merge, and moves the board to In review. (CI `build` passes trivially — docs-only.)

---

## Self-Review

**Spec coverage:**
- `/run-local` = supervisor-only (memory removed from skill; app default unchanged) → Task 1 Steps 1-2, 6. ✅
- Build worker (`installDist`) → Task 1 Step 3. ✅
- Backend in supervisor mode + worker wired via `SPRING_APPLICATION_JSON` → Task 1 Step 4. ✅
- Report the endpoint note → Task 1 Step 5. ✅
- Teardown kills workers (stopgap until IS-090; note already committed) → Task 1 Step 6. ✅
- README match → Task 2. ✅
- No auto-created source → nothing does; the verify run creates one only to prove the path, not the skill. ✅
- Verification is a real run (not code) → Task 3. ✅

**Placeholder scan:** No TBD/TODO. `<WORKER>`/`<repo>`/`$PID`/`$SID` are explicit substitution placeholders for a shell run (values discovered at runtime), and the Task-3 note says to adjust the create-source payload to the live OpenAPI — an explicit verify-or-adjust instruction, not an unwritten step.

**Consistency:** `SPRING_APPLICATION_JSON` worker path, `IOTSIM_RUNTIME_MODE=supervisor`, and the `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua` path are identical across Task 1 (steps 3-4) and Task 3 (verify). Section renumbering (Postgres 1, worker-build 2, backend 3, frontend 4, verify 5) is applied consistently in Task 1 Step 3.
