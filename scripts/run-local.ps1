#!/usr/bin/env pwsh
#
# run-local.ps1 - bring up / tear down the full local e2e stack (Windows).
#
# Mirrors the `run-local` skill (.claude/skills/run-local/SKILL.md):
#   Postgres (:5432) + backend (:8080, IOTSIM_MODE=local, IOTSIM_RUNTIME_MODE=supervisor
#   with a real out-of-process OPC UA worker) + frontend Vite dev server (:4173).
#
# Usage:
#   .\scripts\run-local.ps1            # or `up` - bring the stack up
#   .\scripts\run-local.ps1 down       # stop backend/frontend/postgres (KEEPS db data)
#   .\scripts\run-local.ps1 down -Wipe # also drop the pgdata volume (clean slate)
#
# Runs on Windows PowerShell 5.1 and PowerShell 7+. This file is intentionally
# ASCII-only: 5.1 reads a BOM-less .ps1 as ANSI, so any non-ASCII character
# (e.g. an em-dash) would be mis-decoded and break parsing. Keep it ASCII.
#
[CmdletBinding()]
param(
  [ValidateSet('up', 'down')]
  [string]$Action = 'up',
  [switch]$Wipe
)

$ErrorActionPreference = 'Stop'
# Native (external) commands returning non-zero must NOT throw - we check
# $LASTEXITCODE by hand in the wait loops (pg_isready fails while starting).
# The preference variable below is meaningful only on PowerShell 7.4+, but
# setting it on any 7.x is a harmless no-op; on Windows PowerShell 5.1 native
# commands never throw on non-zero anyway, so gating on major -ge 7 keeps 5.1
# from creating a stray no-op variable.
if ($PSVersionTable.PSVersion.Major -ge 7) {
  $PSNativeCommandUseErrorActionPreference = $false
}

# --- locate repo root (this script lives in <repo>\scripts) -------------------
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$BackendPort  = 8080
$FrontendPort = 4173
$DbPort       = 5432
$BackendLog   = Join-Path $env:TEMP 'iotsim-backend.log'
$BackendErr   = Join-Path $env:TEMP 'iotsim-backend.err.log'
$FrontendLog  = Join-Path $env:TEMP 'iotsim-frontend.log'
$FrontendErr  = Join-Path $env:TEMP 'iotsim-frontend.err.log'
$Worker       = Join-Path $RepoRoot 'workers\worker-opcua\build\install\worker-opcua\bin\worker-opcua.bat'

function Write-Log { param([string]$Msg) Write-Host "[run-local] $Msg" -ForegroundColor Cyan }
function Write-Err { param([string]$Msg) Write-Host "[run-local] $Msg" -ForegroundColor Red }

# --- compose command detection (docker compose v2 plugin, else standalone) ----
$script:ComposeV2 = $false
function Initialize-Compose {
  docker compose version *> $null
  if ($LASTEXITCODE -eq 0) { $script:ComposeV2 = $true; return }
  if (Get-Command docker-compose -ErrorAction SilentlyContinue) { $script:ComposeV2 = $false; return }
  throw "neither 'docker compose' nor 'docker-compose' is available"
}
function Invoke-Compose {
  # docker/compose write progress + status (e.g. "Container ... Running") to
  # stderr while still exiting 0. On Windows PowerShell 5.1 that stderr becomes a
  # terminating error under $ErrorActionPreference='Stop' (PS7 opts out via
  # $PSNativeCommandUseErrorActionPreference; 5.1 has no equivalent). Run the call
  # under 'Continue' and fold stderr into stdout as plain text so it prints
  # instead of throwing. $LASTEXITCODE still reflects docker's real exit code.
  $eap = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    if ($script:ComposeV2) { & docker compose @args 2>&1 | ForEach-Object { "$_" } }
    else { & docker-compose @args 2>&1 | ForEach-Object { "$_" } }
  } finally {
    $ErrorActionPreference = $eap
  }
}

function Write-Report {
  Write-Log "stack is up - entry points:"
  Write-Host "  UI (use this):  http://localhost:$FrontendPort"
  Write-Host "  Swagger UI:     http://localhost:$BackendPort/swagger-ui.html"
  Write-Host "  API base:       http://localhost:$BackendPort/api/v1"
  Write-Host "  Health:         http://localhost:$BackendPort/actuator/health"
  Write-Host "  Logs:           $BackendLog , $FrontendLog"
  Write-Host ""
  Write-Host "  Real OPC UA workers: starting an OPC_UA data source in the UI spawns a Milo"
  Write-Host "  worker that binds the source's runtimeConfig.listenPort and serves"
  Write-Host "  opc.tcp://127.0.0.1:<port>/iotsim - point your external OPC UA client there."
  Write-Host "  (Set a listenPort when creating the source, else the port is ephemeral.)"
  Write-Host ""
  Write-Host "  Tear down with: .\scripts\run-local.ps1 down"
}

function Invoke-Up {
  # 0. Preflight -------------------------------------------------------------
  docker info *> $null
  if ($LASTEXITCODE -ne 0) {
    throw "Docker is not running - start Docker Desktop first."
  }
  Initialize-Compose

  # Install deps when node_modules is missing OR stale. npm writes
  # node_modules/.package-lock.json on every install, so if package-lock.json is
  # newer than that marker (e.g. after a git pull that bumped deps), the install
  # is out of date and we reinstall. Up-to-date installs are left alone so warm
  # starts stay fast.
  $lockFile   = 'package-lock.json'
  $installMark = Join-Path 'node_modules' '.package-lock.json'
  $needsInstall = $false
  if (-not (Test-Path 'node_modules')) {
    $needsInstall = $true
    Write-Log "node_modules missing - running 'npm ci'..."
  } elseif (-not (Test-Path $installMark)) {
    # Folder exists but no install marker - treat as stale to be safe.
    $needsInstall = $true
    Write-Log "node_modules has no install marker - running 'npm ci'..."
  } elseif ((Get-Item $lockFile).LastWriteTimeUtc -gt (Get-Item $installMark).LastWriteTimeUtc) {
    $needsInstall = $true
    Write-Log "node_modules is stale (package-lock.json is newer) - running 'npm ci'..."
  }
  if ($needsInstall) {
    & npm ci
  }

  # Already up? Reuse it, don't double-start.
  $alreadyUp = $false
  try {
    if ((Invoke-RestMethod "http://localhost:$BackendPort/actuator/health" -TimeoutSec 2).status -eq 'UP') {
      Invoke-WebRequest "http://localhost:$FrontendPort" -UseBasicParsing -TimeoutSec 2 *> $null
      $alreadyUp = $true
    }
  } catch { }
  if ($alreadyUp) {
    Write-Log "stack already running - reusing it."
    Write-Report
    return
  }

  # 1. Postgres --------------------------------------------------------------
  Write-Log "starting Postgres (:$DbPort)..."
  Invoke-Compose up -d postgres
  Write-Log "waiting for Postgres to accept connections..."
  while ($true) {
    Invoke-Compose exec -T postgres pg_isready -U iotsim *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep 1
  }

  # 2. Build the OPC UA worker (once per worker-code change) ------------------
  if (-not (Test-Path $Worker)) {
    Write-Log "building OPC UA worker (installDist)..."
    & .\gradlew.bat :workers:worker-opcua:installDist
  } else {
    Write-Log "OPC UA worker already built (delete build\install to force rebuild)."
  }

  # 3. Backend (background, supervisor mode) ---------------------------------
  Write-Log "starting backend (:$BackendPort, supervisor mode) - log: $BackendLog"
  $env:IOTSIM_MODE         = 'local'
  $env:IOTSIM_RUNTIME_MODE = 'supervisor'
  # Forward slashes inside the JSON so the path string stays valid.
  $workerJson = $Worker -replace '\\', '/'
  $env:SPRING_APPLICATION_JSON =
    @{ iotsim = @{ runtime = @{ workers = @{ OPC_UA = @($workerJson) } } } } |
    ConvertTo-Json -Compress -Depth 6

  $be = Start-Process -FilePath (Join-Path $RepoRoot 'gradlew.bat') -ArgumentList ':app:bootRun' `
        -RedirectStandardOutput $BackendLog -RedirectStandardError $BackendErr -NoNewWindow -PassThru
  $be.Id | Out-File (Join-Path $env:TEMP 'iotsim-backend.pid')

  Write-Log "polling health (first run compiles - allow ~2 min)..."
  $deadline = (Get-Date).AddSeconds(240)
  while ($true) {
    try {
      if ((Invoke-RestMethod "http://localhost:$BackendPort/actuator/health" -TimeoutSec 3).status -eq 'UP') { break }
    } catch { }
    if ((Get-Date) -gt $deadline) {
      $tail = Get-Content $BackendLog -Tail 40 -ErrorAction SilentlyContinue
      # A changed migration after a git pull leaves the local DB volume on an
      # older schema; Flyway then refuses to start. Surface the fix instead of
      # only a raw stack trace.
      if ($tail -match 'checksum mismatch' -or $tail -match 'FlywayValidateException' -or $tail -match 'Migrations have failed validation') {
        Write-Err "backend failed: the local database is from an older schema version."
        Write-Err "recreate it, then start again:"
        Write-Err "    .\scripts\run-local.ps1 down -Wipe"
        Write-Err "    .\scripts\run-local.ps1"
        Write-Err "(this drops local DB data). Backend log tail:"
      } else {
        Write-Err "backend never reported UP. Last 40 log lines:"
      }
      $tail
      exit 1
    }
    Start-Sleep 2
  }
  Write-Log "backend is UP."

  # 4. Frontend dev server (background) --------------------------------------
  Write-Log "starting frontend dev server (:$FrontendPort) - log: $FrontendLog"
  $fe = Start-Process -FilePath $env:ComSpec -ArgumentList '/c', 'npm run dev' `
        -RedirectStandardOutput $FrontendLog -RedirectStandardError $FrontendErr -NoNewWindow -PassThru
  $fe.Id | Out-File (Join-Path $env:TEMP 'iotsim-frontend.pid')

  Write-Log "waiting for Vite..."
  $deadline = (Get-Date).AddSeconds(60)
  while ($true) {
    try { Invoke-WebRequest "http://localhost:$FrontendPort" -UseBasicParsing -TimeoutSec 3 *> $null; break } catch { }
    if ((Get-Date) -gt $deadline) {
      Write-Err "frontend (:$FrontendPort) never came up. Last 40 log lines:"
      Get-Content $FrontendLog -Tail 40 -ErrorAction SilentlyContinue
      exit 1
    }
    Start-Sleep 1
  }

  # 5. Verify the browser -> proxy -> backend -> DB path ---------------------
  try {
    Invoke-RestMethod "http://localhost:$FrontendPort/api/v1/projects" -TimeoutSec 5 *> $null
    Write-Log "wiring verified: :$FrontendPort/api proxied to backend."
  } catch {
    Write-Err "warning: proxied /api/v1/projects did not answer - check $BackendLog."
  }

  Write-Report
}

function Invoke-Down {
  Initialize-Compose

  # Kill by listening PORT: bootRun forks a child JVM whose args have no
  # "bootRun" in them, so matching on process name/args would miss it.
  Get-NetTCPConnection -LocalPort $BackendPort, $FrontendPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
      Write-Log "stopping process id $_"
      Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }

  # STOPGAP (IS-123): supervisor-spawned workers don't shut down gracefully
  # yet - graceful Shutdown RPC is IS-090. Kill lingering workers explicitly.
  Get-Process worker-opcua -ErrorAction SilentlyContinue | Stop-Process -Force

  if ($Wipe) {
    Write-Log "wiping database (docker compose down -v)..."
    Invoke-Compose down -v
  } else {
    Write-Log "stopping Postgres (keeping pgdata volume)..."
    Invoke-Compose stop postgres
  }

  Remove-Item (Join-Path $env:TEMP 'iotsim-backend.pid'), (Join-Path $env:TEMP 'iotsim-frontend.pid') -ErrorAction SilentlyContinue
  Write-Log "stack is down."
}

switch ($Action) {
  'up'   { Invoke-Up }
  'down' { Invoke-Down }
}
