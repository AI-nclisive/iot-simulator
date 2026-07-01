#!/usr/bin/env bash
#
# run-local.sh — bring up / tear down the full local e2e stack (macOS/Linux).
#
# Mirrors the `run-local` skill (.claude/skills/run-local/SKILL.md):
#   Postgres (:5432) + backend (:8080, IOTSIM_MODE=local, IOTSIM_RUNTIME_MODE=supervisor
#   with a real out-of-process OPC UA worker) + frontend Vite dev server (:4173).
#
# Usage:
#   scripts/run-local.sh            # or `up` — bring the stack up
#   scripts/run-local.sh down       # stop backend/frontend/postgres (KEEPS db data)
#   scripts/run-local.sh down --wipe  # also drop the pgdata volume (clean slate)
#
set -euo pipefail

# --- locate repo root (this script lives in <repo>/scripts) -------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

BACKEND_PORT=8080
FRONTEND_PORT=4173
DB_PORT=5432
BACKEND_LOG="/tmp/iotsim-backend.log"
FRONTEND_LOG="/tmp/iotsim-frontend.log"
BACKEND_PID_FILE="/tmp/iotsim-backend.pid"
FRONTEND_PID_FILE="/tmp/iotsim-frontend.pid"
WORKER="$REPO_ROOT/workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua"

log() { printf '\033[1;34m[run-local]\033[0m %s\n' "$*"; }
err() { printf '\033[1;31m[run-local]\033[0m %s\n' "$*" >&2; }

# --- compose command detection (docker compose v2 plugin, else standalone) ----
COMPOSE=(docker compose)
detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
  else
    err "neither 'docker compose' nor 'docker-compose' is available"
    exit 1
  fi
}

port_pids() { lsof -ti "tcp:$1" 2>/dev/null || true; }

report() {
  cat <<EOF

$(log "stack is up — entry points:")
  UI (use this):  http://localhost:${FRONTEND_PORT}
  Swagger UI:     http://localhost:${BACKEND_PORT}/swagger-ui.html
  API base:       http://localhost:${BACKEND_PORT}/api/v1
  Health:         http://localhost:${BACKEND_PORT}/actuator/health
  Logs:           ${BACKEND_LOG} , ${FRONTEND_LOG}

  Real OPC UA workers: starting an OPC_UA data source in the UI spawns a Milo
  worker that binds the source's runtimeConfig.listenPort and serves
  opc.tcp://127.0.0.1:<port>/iotsim — point your external OPC UA client there.
  (Set a listenPort when creating the source, else the port is ephemeral.)

  Tear down with: scripts/run-local.sh down
EOF
}

up() {
  # 0. Preflight -------------------------------------------------------------
  if ! docker info >/dev/null 2>&1; then
    err "Docker is not running — start Docker Desktop or 'colima start' first."
    exit 1
  fi
  detect_compose

  if [ ! -d node_modules ]; then
    log "node_modules missing — running 'npm ci'..."
    npm ci
  fi

  # Already up? Reuse it, don't double-start.
  if curl -fsS "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"' \
     && curl -fsS "http://localhost:${FRONTEND_PORT}" >/dev/null 2>&1; then
    log "stack already running — reusing it."
    report
    exit 0
  fi

  # 1. Postgres --------------------------------------------------------------
  log "starting Postgres (:${DB_PORT})..."
  "${COMPOSE[@]}" up -d postgres
  log "waiting for Postgres to accept connections..."
  until "${COMPOSE[@]}" exec -T postgres pg_isready -U iotsim >/dev/null 2>&1; do sleep 1; done

  # 2. Build the OPC UA worker (once per worker-code change) ------------------
  if [ ! -x "$WORKER" ]; then
    log "building OPC UA worker (installDist)..."
    ./gradlew :workers:worker-opcua:installDist
  else
    log "OPC UA worker already built (delete build/install to force rebuild)."
  fi

  # 3. Backend (background, supervisor mode) ---------------------------------
  log "starting backend (:${BACKEND_PORT}, supervisor mode) — log: ${BACKEND_LOG}"
  IOTSIM_MODE=local \
  IOTSIM_RUNTIME_MODE=supervisor \
  SPRING_APPLICATION_JSON="{\"iotsim\":{\"runtime\":{\"workers\":{\"OPC_UA\":[\"${WORKER}\"]}}}}" \
    nohup ./gradlew :app:bootRun >"$BACKEND_LOG" 2>&1 &
  echo $! >"$BACKEND_PID_FILE"

  log "polling health (first run compiles — allow ~2 min)..."
  deadline=$((SECONDS + 240))
  until curl -fsS "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    if (( SECONDS > deadline )); then
      err "backend never reported UP. Last 40 log lines:"
      tail -n 40 "$BACKEND_LOG" >&2 || true
      exit 1
    fi
    sleep 2
  done
  log "backend is UP."

  # 4. Frontend dev server (background) --------------------------------------
  log "starting frontend dev server (:${FRONTEND_PORT}) — log: ${FRONTEND_LOG}"
  nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
  echo $! >"$FRONTEND_PID_FILE"

  log "waiting for Vite..."
  deadline=$((SECONDS + 60))
  until curl -fsS "http://localhost:${FRONTEND_PORT}" >/dev/null 2>&1; do
    if (( SECONDS > deadline )); then
      err "frontend (:${FRONTEND_PORT}) never came up. Last 40 log lines:"
      tail -n 40 "$FRONTEND_LOG" >&2 || true
      exit 1
    fi
    sleep 1
  done

  # 5. Verify the browser -> proxy -> backend -> DB path ---------------------
  if curl -fsS "http://localhost:${FRONTEND_PORT}/api/v1/projects" >/dev/null 2>&1; then
    log "wiring verified: :${FRONTEND_PORT}/api proxied to backend."
  else
    err "warning: proxied /api/v1/projects did not answer — check ${BACKEND_LOG}."
  fi

  report
}

down() {
  local wipe="${1:-}"
  detect_compose

  # Kill by listening PORT: bootRun forks a child JVM whose args have no
  # "bootRun" in them, so matching on process name/args would miss it.
  for p in "$BACKEND_PORT" "$FRONTEND_PORT"; do
    pids="$(port_pids "$p")"
    if [ -n "$pids" ]; then
      log "stopping process(es) on :$p ($pids)"
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null || true
    fi
  done

  # STOPGAP (IS-123): supervisor-spawned workers don't shut down gracefully
  # yet — graceful Shutdown RPC is IS-090. Kill lingering workers explicitly.
  pkill -f worker-opcua 2>/dev/null || true

  if [ "$wipe" = "--wipe" ]; then
    log "wiping database (docker compose down -v)..."
    "${COMPOSE[@]}" down -v
  else
    log "stopping Postgres (keeping pgdata volume)..."
    "${COMPOSE[@]}" stop postgres
  fi

  rm -f "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"
  log "stack is down."
}

case "${1:-up}" in
  up)   up ;;
  down) down "${2:-}" ;;
  *)    err "usage: $0 [up|down [--wipe]]"; exit 2 ;;
esac
