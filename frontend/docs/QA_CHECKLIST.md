# QA Checklist — IoT Data Source Simulator (Web UI)

Direct user scenarios with expected results. Grounded in the **actually implemented**
frontend behavior (surfaces + tests), not just SPEC.md. Known stubs are flagged with ⚠️.

Legend: `[ ]` scenario to verify · **→** expected result · ⚠️ = stub / not-yet-wired.

---

## 1. Access & Entry

### 1.1 Login (`/login`)
- [ ] Open app in **local (trusted) mode** → "Trusted local mode" panel, no username/password, single **Open projects** button.
- [ ] Open app in **shared mode** → username + password fields + **Sign in** button.
- [ ] Shared mode, empty fields → **Sign in** disabled.
- [ ] Shared mode, only username OR only password → **Sign in** stays disabled.
- [ ] Enter `admin` / `admin`, Sign in → button shows "Signing in…", fields disabled, after ~800 ms navigates to `/projects`; mode=shared, role=admin.
- [ ] Enter any other credentials → after ~800 ms error "The username or password is incorrect…", fields re-enabled, no navigation.
- [ ] With error shown, edit a field → error clears; button stays disabled until both fields filled.
- [ ] `server-unavailable` scenario → "The authentication server is not reachable…".
- [ ] `session-expired` scenario → amber banner "Your session has expired…" above the form.
- ⚠️ Credential check is hardcoded (only `admin`/`admin`); no real auth API.

### 1.2 Project Entry (`/projects`)
- [ ] Page load → "Loading your saved simulator projects…" until fetch resolves.
- [ ] Fetch fails → "Projects could not be loaded." + error message.
- [ ] No projects → "No projects are available yet." empty state; admin sees **Create project**.
- [ ] Each project card shows name, last-activity timestamp, and 3 stats (sources / running / recordings) + **Open project**.
- [ ] **Open project** → sets current project, navigates to `/overview`.
- [ ] Admin-only card actions visible: **Rename**, **Duplicate**, **Archive**, **Delete** (delete in red).
- [ ] **Rename** → dialog pre-filled with current name; **Rename** disabled when blank or unchanged; valid new name enables it; save closes dialog and updates list; Esc cancels.
- [ ] **Duplicate** → no dialog; POST duplicate; copy appears in list; failure shows error toast.
- [ ] **Archive** → confirm dialog (amber) with running-sources count + "can be restored from admin"; confirm removes from active list; cancel = no change.
- [ ] **Delete** → confirm dialog (red) with running-sources + shared-impact + "not reversible"; confirm removes permanently; cancel = no change.
- [ ] **Create project** → dialog with required Name + optional Description; **Create** disabled until non-whitespace name; on create → "Creating…", then navigates to `/overview` with new project; Esc cancels.
- [ ] **Import project** → dialog with filename field, accepts `.iotsim`. ⚠️ mock validation only:
  - filename containing `old` → "Incompatible — cannot import", Import disabled.
  - filename matching existing project → amber overwrite warning.
  - filename containing `broken` → after ~1.2s "archive is malformed…" + **Retry import**.
  - otherwise → success panel + **Close**.

### 1.3 App Shell / Navigation
- [ ] Top bar shows "IoT Simulator"; hamburger visible only `<1024px`.
- [ ] Left rail: PROJECT section (current name), **Switch project** dropdown, **Project list** link, nav menu.
- [ ] Nav items: Overview, Data Sources, Recordings, Scenarios, Evidence, Settings; **Admin** only if admin.
- [ ] Desktop `≥1024px` → rail always visible, hamburger hidden.
- [ ] Mobile: click hamburger → rail shows, `aria-label`→"Close navigation", `aria-expanded=true`; click again reverses it.
- [ ] Mobile: click a nav link → navigates AND rail auto-closes.
- [ ] Switch project via dropdown → PROJECT name updates, content refreshes for new project.
- [ ] **Project list** → navigates to `/projects`.
- [ ] Navigate to any in-project route with no project selected → redirect to `/projects`.

### 1.4 Role-Aware Behavior (Access Policy)
- [ ] **Local** mode: all create/edit/run/stop/delete allowed; `canManageAdmin=false`, role label "Trusted local".
- [ ] **Shared / admin**: same as local + `canManageAdmin=true`; **Admin** nav visible.
- [ ] **Shared / user**: create/edit/duplicate/record/replay-config all hidden; can still **Run**/**Stop** scenarios and **Stop** sources; no admin actions; sees read-only notices.
- [ ] Non-admin: no Create/Import project, no per-card admin actions, no Admin nav.

---

## 2. Overview / Runtime Dashboard (`/overview`)
- [ ] Section titled **Runtime dashboard** renders on open.
- [ ] Live data sources box (if any): "LIVE DATA SOURCES", "X connected" + ("All healthy" | "X need attention") + **View data sources** link. Hidden when no sources.
- [ ] Live connection stale/reconnecting → amber banner ("Reconnecting to live runtime updates…" / "Live runtime updates have paused…").
- [ ] **ACTIVE RUNS** loading → 3 skeleton cards; fetch error → red banner.
- [ ] No active runs → empty state "No active runs." with guidance.
- [ ] Each run card: label, `initiator · startedAt`, process badge (Recording=warning / Scenario=neutral / Replay=accent), and state badge:
  - running → "{type} in progress", queued → "{type} waiting", completed → "…completed", stopped → "…stopped", failed → "…failed" (danger) + red banner + **Open source**.
- [ ] Run with related source → **Open source** links to `/data-sources/{id}`.
- ⚠️ No "Automated" badge and no per-run evidence badge rendered (referenced in tests, not shown).

---

## 3. Data Sources — List (`/data-sources`)
- [ ] Load → table with name, endpoint, protocol, parameter count (localized), State (Run/Off), Health (Healthy/Warning/Error), sorted by name asc.
- [ ] Loading → spinner "Loading data sources for this project".
- [ ] Error → error panel + **Retry** (re-fetches).
- [ ] Empty project → "No data sources exist yet…" + **Create source** (if permitted).
- [ ] Search by name/protocol/endpoint → instant case-insensitive filter + active-filter badge.
- [ ] Protocol filter (All / OPC UA / Modbus TCP) and State filter (All / Run / Off) → filter + badge; combinable.
- [ ] **Clear all** → resets search + both filters, removes badges.
- [ ] Filters with no match → "No sources match the current filters" + guidance.
- [ ] **Open** → `/data-sources/{id}` (Overview tab).
- [ ] **Record** action shown for SCAN/SYNTHETIC → `/data-sources/{id}/record`; hidden for IMPORT basis.
- [ ] **Simulate** (SCAN/SYNTHETIC) / **Replay recording** (IMPORT) → `/data-sources/{id}/replay` (label differs by basis).
- [ ] Replay running for a source → **Simulating** badge on row; **Stop simulation** action → POST run stop; badge clears.
- [ ] **Duplicate** → POST duplicate, list refreshes, success/error toast.
- [ ] **Stop source** (active source) → confirm dialog (endpoint + runtime impact + "reversible"); confirm sets Off.
- [ ] **Delete** (red) → confirm dialog (endpoint + shared impact + "not reversible"); confirm removes row.
- [ ] Row/toolbar actions hidden per role (no create/record/replay-config for shared user).

---

## 4. Data Sources — Create Wizard (`/data-sources/new`)
- [ ] Without `canCreateSource` → locked panel "Source creation is not available…".
- [ ] **Step 1 Protocol**: OPC UA / Modbus TCP cards (name, desc, port hint); select highlights, enables **Next**.
- [ ] **Step 2 Basis**: Real source (SCAN, "Recommended") / Prepared data (IMPORT) / Synthetic device (SYNTHETIC); selection updates suggested endpoint.
- [ ] **Step 3 (SCAN) Setup**: name (req), real endpoint (req), simulator port (1–65535); OPC UA security policy + node-id options; Modbus unit id + address base; shared mode adds credential mode (anonymous / password / external-ref).
  - [ ] **Test connection** → disabled while testing; "Connection ready" (accent) or "Enter a valid endpoint" (danger).
  - [ ] **Next** disabled if name empty / endpoint empty / port invalid / (shared) credentials incomplete.
  - [ ] Local mode does NOT enforce credentials; shared mode does (UI-116).
- [ ] **Step 3 (IMPORT)**: recording list loads; select one highlights and enables Next.
- [ ] **Step 3 (SYNTHETIC)**: name, port (auto-bumps if 4840/502 taken), schema-source picker, synthetic profile with ≥1 valid measurement; Next blocked until all valid.
- [ ] **Schedule step** (SCAN/IMPORT): optional start/end datetime checkboxes; info "starts immediately… no time limit" when off; Next blocked if a checked time is empty.
- [ ] **Review**: summary cards; **Create source** → POST `data-sources` (or `data-sources/synthetic`); success navigates to detail; error toast + retry.
- [ ] **Back** available from step 2+; **Cancel** → `/data-sources`.
- [ ] Blocked Next shows validation panel with specific message per step.
- ⚠️ No "Manual schema" basis offered (UI-121). SCAN discovered-schema review step depends on backend flow.

---

## 5. Data Sources — Detail (`/data-sources/{id}`)
### 5.1 Header & actions
- [ ] Header: name, endpoint (monospace), basis subtitle (Real device / Prepared data replay / etc.), State + Health badges + "Simulating" if replay running.
- [ ] SYNTHETIC not running → **Run** → POST run-synthetic; button→Stop; switches to Values tab; "Simulating" appears.
- [ ] SYNTHETIC running → **Stop** → POST run stop; button→Run; badge clears.
- [ ] **Record** (SCAN/SYNTHETIC) → `/record`; **Simulate**/**Replay recording** → `/replay`.
- [ ] Replay running → **Stop simulation** → POST run stop, button disappears.
- [ ] Active non-SYNTHETIC source → **Stop source** → confirm dialog → Off.
- [ ] Shared user → read-only notice; runtime setup read-only.
- [ ] Health Warning/Error → guidance block with specific remediation steps; Healthy → none.
- [ ] Tabs: Overview, Schema, Values, Clients, Events, Settings; active tab styled; `?tab=` updates.
- [ ] Switch tab with unsaved schema changes → "Leave without saving?" dialog; **Leave tab** discards.

### 5.2 Overview tab
- [ ] Three blocks (State / Health / Parameters); health follow-up block only when Warning/Error.
- [ ] Basis-specific description (IMPORT stopped vs running; SCAN/SYNTHETIC running vs stopped).
- [ ] Endpoint grid (serve URL, real device endpoint for SCAN, protocol, source type).
- [ ] Next-action links (Record / Simulate / Open Values / Open Schema).

### 5.3 Schema tab (editor)
- [ ] Loading → "Loading schema…"; active source → amber "changes won't affect running session, restart to apply".
- [ ] Tree: folders auto-expanded top-level, collapse/expand toggles, variable rows show data type; search filters variables (keeps parent folders).
- [ ] Select parameter → right editor: name + type (read-only), unit + description (editable).
- [ ] Edit unit/description → "Unsaved changes" badge; changing description → dependency warning about breaking recordings/replays/scenarios.
- [ ] **Save schema** → direct save if no warnings; confirm dialog if warnings; PUT schema; success clears badge + refreshes; error message.
- [ ] **Discard changes** → reverts, clears badge.
- [ ] Source stopped → **+ Add parameter** form (name req + type default FLOAT64 + unit); Add → new node, PUT, refresh. Hidden when source running.
- [ ] Delete parameter → removes node, PUT with node filtered out, tree refreshes, editor cleared.

### 5.4 Values tab
- [ ] Source Off → "Source is not running." empty state; badges Off + parameter count.
- [ ] Source running → SSE stream; columns Parameter / Type / Current value / Updated / State.
- [ ] Existing param update → row updated in place; new param → row appended.
- [ ] Quality GOOD → "Live" (accent); stale → "No updates" (warning).
- [ ] Search filters by path/name/type/value/updated; State filter (All/Live/No updates); Pinned filter (All/Pinned/Unpinned).
- [ ] Connection reconnecting → "Reconnecting to live values…" toast; stale → "Live values may be out of date." toast.
- [ ] Schema join → full path, mapped type, value with unit (e.g. "25.5 °C").
- ⚠️ Parameter pinning: filter present but no pin/unpin control on rows.

### 5.5 Clients tab
- [ ] No clients → "No clients connected." empty state.
- [ ] Clients present → table Client / State / Connected since / Disconnected + "N client(s) tracked".
- [ ] connected=true → "Connected" (accent); false → "Disconnected".
- [ ] Connected time HH:MM:SS; disconnectedAt null → "—".
- [ ] Source stopped → notice "live client tracking resumes when it runs again".

### 5.6 Events tab
- [ ] Loading / error / empty ("No runtime events recorded yet") states.
- [ ] Events list: level icon (●/◆/·), message, `timestamp · category`, level badge.
- [ ] Type→message humanized (SOURCE_START → "Source started"), payload.detail overrides.
- [ ] Level mapping: SOURCE_ERROR/HEALTH_ERROR=error; SOURCE_STALE/HEALTH_WARNING=warning; else info.
- [ ] Category mapping: connection / replay / recording / runtime.
- [ ] Click row → expands detail (timestamp, category, level, id), `aria-expanded=true`; only one open at a time.
- [ ] Category + Level filters (combinable); no match → "No matching events."; counter "X of Y event(s)".
- [ ] Live SSE event prepended (newest first); duplicates deduped; events for other sources ignored.

### 5.7 Settings tab
- [ ] Admin → editable; shared user → read-only message + disabled fields + "Read-only" badge.
- [ ] Active source → warning "editing changes saved config; current output stays separate until next start".
- [ ] Fields: name (always); real device endpoint (SCAN only); read-only serve URL / protocol / parameter count.
- [ ] Modify → "Unsaved changes" badge; no change → badge hidden + Save disabled.
- [ ] Empty name → "Name is required."; SCAN empty endpoint → "Real device endpoint is required."; Save disabled.
- [ ] **Save changes** → POST source update; "Saved" badge persists until next change (UI-137).
- [ ] **Reset** → reverts fields, clears badges.

### 5.8 Fault config panel
- [ ] No kind chosen → "Choose a fault kind above…".
- [ ] DELAY → "Added latency (ms)" required + timing; BAD_VALUE/CONNECTION_DROP/MISSING_VALUE/TIMEOUT/PROTOCOL_ERROR/SOURCE_UNAVAILABLE → timing only.
- [ ] Timing: "Start after (s)" + "Duration (s)"; negatives clamped to 0; "Leave empty to run until cleared".
- [ ] "What this does" plain-language description updates with values.
- [ ] Required param empty → "This field is required."; canEdit=false → all fields disabled.

---

## 6. Recordings & Samples

### 6.1 Recordings list (`/recordings`)
- [ ] Load → "Recordings" + count; loading spinner; merges captured + imported.
- [ ] Search by source/author (case-insensitive); Origin filter (Recorded/Imported); Source filter; "X results" updates.
- [ ] Row: name (or source id fallback), source, captured date + author, origin badge, value count.
- [ ] Click row → `/recordings/{id}`.
- [ ] **Create recording** (if permitted) → `/recordings/new`; **Import recording** → import dialog.
- [ ] No recordings / no matches / load error → respective empty & error panels.

### 6.2 Create recording wizard (`/recordings/new`)
- [ ] Step 1 Source: list of selectable sources; Next disabled until one selected; no sources → "No data sources available.".
- [ ] Step 2 Scan type: "Schema + data" (default) / "Schema only"; source without real endpoint + Schema+data → warning "Live capture not available".
- [ ] Step 3 Review: source details + optional name (≤255, trimmed); SCHEMA_AND_DATA no endpoint → **Start capture** disabled + warning; with endpoint → enabled + info; SCHEMA_ONLY → **Create recording**.
- [ ] Blank name → POST omits `name`; whitespace name → POST trims.
- [ ] Back preserves state; header chips navigate to completed steps; Cancel → `/recordings`.
- [ ] SCHEMA_ONLY Create → POST recording, success toast, navigate to detail.
- [ ] SCHEMA_AND_DATA Start capture → navigate to `/data-sources/{id}/recording` (no API here).

### 6.3 Recording detail (`/recordings/{id}`)
- [ ] Not found / loading / error panels; header name (or "Recording {id}") + origin badge.
- [ ] Metadata grid: source, captured at, captured by, values recorded.
- [ ] **Schema tab** (default): empty → "No schema captured."; folders collapse/expand; variables show data type; 404 treated as empty; load error → panel.
- [ ] **Values tab**: default filters all qualities; 0 values → "No values captured. This recording contains schema only.".
- [ ] Table Timestamp / Parameter / Value ("—" if none) / Quality (GOOD green, UNCERTAIN/BAD amber).
- [ ] **Load more** when cursor present; "All N values loaded." when done; error on first load → panel; error on load-more → inline, rows kept.
- [ ] Quality checkboxes (GOOD/UNCERTAIN/BAD); unchecking all → "No quality selected." with **no API call**; re-checking fetches.
- [ ] Search debounced 300 ms; From/To datetime add ISO params.

### 6.4 Recording flow — live capture (`/data-sources/{id}/recording`)
- [ ] Source with endpoint → header + metric cards (Capture state, Parameters, Duration, Captured values, Last received); missing source → error panel.
- [ ] Ready state → **Start recording** enabled, no Stop, duration 00:00, values 0; no endpoint → Start disabled + warning.
- [ ] **Start recording** → POST start; state "no-values-yet"; **Stop** appears; duration ticks (1 s).
- [ ] Values arriving → "Recording" badge; last-received updates; count matches SSE rows (no fake increment).
- [ ] Stream stale → "Disconnected" badge + "Stream went stale…".
- [ ] **Stop recording** → POST stop; valueCount>0 → "save-ready" + saved panel (author/duration/values); valueCount=0 → "No values were captured".
- [ ] Disconnect with values → "Capture ended because the source disconnected"; **Save partial result** → "Partial recording saved" + **Open replay**; **Discard capture**.
- [ ] **Open replay** → `/replay?artifactId={id}`; **Record again** resets to ready.
- [ ] **Discard** enabled when idle/no values or disconnected-with-values; disabled while actively recording with data.
- [ ] Shared read-only role → "Recording is read-only in this role"; Start/Stop disabled.

### 6.5 Replay flow (`/data-sources/{id}/replay`)
- [ ] Source + recordings → header + badges (Replay, protocol, replay state, evidence state); missing source → error; no recordings → "No recordings are available yet." + link.
- [ ] Recording dropdown pre-selected from `?artifactId=` or first; details panel (author/created/values); disabled during replay.
- [ ] Speed dropdown 0.5x/1x(default)/2x; disabled during replay.
- [ ] Compatibility: protocol match → "Ready for this source"; mismatch → "Protocol mismatch" + Start disabled.
- [ ] Deterministic settings collapsible; applied → "Repeatability: seed N, {ordering} order" text.
- [ ] **Start replay** → POST replay (`compatibilityAck:false`); returns runId+evidenceId; state "running", progress 0%, evidence "Assembling", started-at set.
- [ ] 409 schema mismatch → reverts idle + "Schema version mismatch" + **Run anyway** (retry with `compatibilityAck:true`).
- [ ] Run disappears from active-runs poll after being seen → state "completed", progress 100%, evidence "Ready".
- [ ] **Stop replay** (running) → POST run stop → state "failed", evidence "Retry needed", runId cleared.
- [ ] Runtime events log lists artifact selected / protocol match / running / progress / completed messages.
- [ ] Shared user (no replay config) → dropdowns + Start disabled + read-only panel.

### 6.6 Import / Export / Sample dialogs
- **Recording import dialog**
  - [ ] Opens modal "Import artifact — Select file"; Esc / backdrop closes.
  - [ ] Drop zone + browse; Next disabled until file; remove file re-disables; unsupported file rejected.
  - [ ] Next → "Validating…" spinner. ⚠️ mock outcomes: OK → "Ready to import" + preview → **Import** → confirm → **Confirm import** fires onImported; Incompatible → reason + Try another; Unsupported version → shows version vs max supported.
  - [ ] No import permission → read-only "Admins only" + Close only.
- **Recording export dialog**
  - [ ] Modal "Export recording"; summary card + "Credential fields always excluded".
  - [ ] Format: .iotsim (default) / Raw JSON / CSV summary; "Include schema" checkbox disabled for CSV; "Exclude credentials" always checked+disabled.
  - [ ] **Export** → "Preparing…" ~800 ms → "Export ready — download would start here" + "secrets excluded" → **Close**.
- **Sample import dialog**
  - [ ] Modal "Add sample — Select file"; unsupported type → "Unsupported file type — use .json or .iotsim.".
  - [ ] Name auto-filled from filename; empty → "Name is required."; duplicate (case-insensitive) → "A sample named … already exists.".
  - [ ] Next → validating (JSON parsed); invalid JSON → error, back to select.
  - [ ] Confirm → **Add sample** → createSample(projectId, name, full selection); success onImported; API error → inline retry message.
  - [ ] No permission → "Admins only" + Close.

---

## 7. Scenarios

### 7.1 Scenarios list (`/scenarios`)
- [ ] Empty (admin vs shared-user wording); load error → message + **Retry**.
- [ ] Search (name/description/owner); Run-state filter (All/Not running/Running/Stopped/Failed) + clearable badge; **Clear all**.
- [ ] Sortable columns (Scenario/State/Steps/Last run/Owner), one at a time.
- [ ] Row: name+desc, state badge (Running=accent, Failed=danger, Stopped=warning, Not running=neutral), step count, last-run summary, owner, "Editing: {user}" if locked.
- [ ] Admin: **New scenario** → create + open builder; **Open**; **Run** (Not running) → start + toast + run view; **Stop** (Running) → confirm → stop + toast; **Duplicate** → toast + open copy.
- [ ] Shared user: no New/Stop/Duplicate; **Open** (read-only); **Run** allowed.
- [ ] Locked by another → Run/Stop disabled + "Editing:" indicator.

### 7.2 Scenario builder (`/scenarios/{id}`)
- [ ] Unknown id → not-found panel + Back; existing → header (name/status/desc) + two-pane (steps left, detail right).
- [ ] Status: no steps→Draft; unconfigured→Invalid; all ok→"Ready to run"; locked→Locked + banner.
- [ ] Validation summary: issues list (step-specific issues are jump links) or "All steps configured…"; non-blocking warnings in orange.
- [ ] Admin add steps (+ Start / Stop / Replay / Synthetic / Inject fault / Wait / Marker) → appends, auto-selects, ordinal shown.
- [ ] Select step → detail panel; move up/down (disabled at ends); delete (✕) → confirm → removes.
- [ ] Rename via name heading (Enter commits, Esc cancels).
- [ ] **Save** → "Saving…" → "Scenario saved." / "Save failed…".
- [ ] **Run** disabled with validation issues (tooltip); enabled when all configured → start + toast + `/run`.
- [ ] Locked → banner + all edit controls hidden/disabled; Run still available.

### 7.3 Scenario step editor & validation
- [ ] Field kinds render correctly: text / number(min 0) / checkbox / select("Select…") / source picker / recording picker (filtered to source; changing source clears recording on REPLAY).
- [ ] Required fields red asterisk + "This field is required."; hints under optional fields.
- [ ] Status "Step is fully configured." vs "…still needs required fields."; server validation errors shown at bottom.
- [ ] Fault step: Source + Kind selects + fault panel (params by kind + timing + description).
- [ ] canEdit=false → all inputs disabled.
- [ ] Blocking validation: empty scenario / unconfigured step / act-on-not-started source / start-already-running / stop-not-running.
- [ ] Non-blocking warnings: sources left running at end; wait/marker only ("will not produce data").

### 7.4 Scenario run view (`/scenarios/{id}/run`)
- [ ] Unknown id → not-found + Back; no active run → "No active run." + Back; valid → full view.
- [ ] Header: Back, name, status badge (running/completed=accent, stopped/stale=warning, failed=danger, queued=neutral), run id.
- [ ] Current step section (label+type / "Starting…" / "not active"); step timeline with per-step status (pending/active/done/failed/skipped).
- [ ] SSE: step-started→active, step-completed→done, run-finished→final state, hides Stop, updates scenario runState.
- [ ] No SSE event 5 s → "Live updates paused — connection may be stale." + status→stale; next event clears.
- [ ] Evidence section: running→"Collecting…"; done+evidenceId→"Available" + **Open evidence**; failed/none→"None".
- [ ] **Stop run** (running/stale) → confirm "Stop {name}? …interrupted" → immediate "stopped" + toast; Stop hides.
- [ ] **Open in builder** → builder; Back → list.
- ⚠️ Sources section "No source data during this run." and Events section "No events captured." are placeholders (not wired).

### 7.5 Deterministic run settings
- [ ] Off (default) → info "live timing… results may vary"; no expanded settings.
- [ ] On → expands Mode + Ordering + Evidence trace; off again → collapses, emits null.
- [ ] Custom seed (default): integer 1–9,999,999; invalid → "Seed must be a number between 1 and 9,999,999.".
- [ ] Named preset: Stable-1 / Stable-2 / CI baseline.
- [ ] Ordering: Original (default) / Alphabetical; preset + alphabetical → incompatible amber warning.
- [ ] "Record seed and ordering in evidence" checkbox (default on) + repeatability-scope notice.

### 7.6 Synthetic profile step
- [ ] "Reuse schema from source" dropdown → loads schema ("Loading schema…"); no schema → "This source has no schema yet…"; no measurements → "no measurements to drive".
- [ ] Optional Seed ("Random each run") + "Fix the seed for a reproducible sequence".
- [ ] Per-measurement card: enable checkbox + name/path/type/unit; when enabled → Pattern dropdown (Sine/Random walk/Ramp/Square/Random uniform/Constant) with pattern-specific fields (Constant→Value; uniform/walk→Min/Max; sine/ramp/square→Min/Max/Period; walk→Volatility) + Update rate (ms).
- [ ] Validation: min≤max, positive period, updateRate>0 drive emitted validity.
- [ ] **Prefill from recording** dropdown (value-count>0) → **Prefill** → POST derive-synthetic; success toast (or "No measurements matched…" warning); API error toast; no recordings → dropdown disabled.
- [ ] Emits `{schemaFromSourceId, config, valid, measurementCount}`; valid only if source + ≥1 enabled + valid patterns + rate>0.

---

## 8. Evidence

### 8.1 Evidence list (`/evidence`)
- [ ] Load spinner → table sorted by Started desc; empty → "No evidence has been captured yet.".
- [ ] Search (run id / initiator / source / state) + chip; clear restores.
- [ ] Filters: State (In progress/Ready/Incomplete/Export failed), Work type (Scenario/Source), **More filters**→Initiator; chips + **Clear all**.
- [ ] Sortable columns (Evidence/Initiator/Started), default Started desc.
- [ ] **Open** → `/evidence/{id}`.
- [ ] Admin: **Export** (Ready/Incomplete) → `?export=1`; **Recover export** (Export failed) → recovery mode.
- [ ] Non-admin → "Inspect only" badge, no export; shared warning "Export actions are reserved for Admin users".
- [ ] Filters no match → "No evidence matches the current filters…"; load fail → "Failed to load evidence." + message.

### 8.2 Evidence detail (`/evidence/{id}`)
- [ ] Summary (kind+run id, completeness, status badge, export state); cards Kind/Initiator/Duration/Created by; manifest (kind, initiator, started/ended, completeness, run id, scenario/recording id if any).
- [ ] Not found / load fail → message + **Back to evidence**.
- [ ] Admin export: In progress → "Export not ready" disabled; Ready/Incomplete/Failed → **Export evidence** → "Exporting…" → success "Export queued successfully" + **Download bundle**; error alert + retry.
- [ ] Export-failed status → red "Export recovery" banner + **Retry export**.
- [ ] **Download bundle** → downloads `evidence-{id}.zip`; error → alert.
- [ ] Sources section (cards / "No sources.") + "Bundle contents: full timeline, client delivery, value counts in the download".
- [ ] Duration: running → "Still running"; ended → MM:SS; invalid → "—".

---

## 9. Settings (`/settings`)
- [ ] Project info: read-only name + id (monospace) + access-mode & role badges.
- [ ] Admin → editable name (**Save name** disabled until changed, ≤80); empty → "Project name cannot be empty"; save → "Saving…" → "Saved" badge ~2.5 s. Shared user → read-only + "Settings are read-only".
- [ ] **Import project** link (admin, local) → `/projects/import`; hidden for shared user.
- [ ] Export scope radios: Full project (default) / Configuration only.
- [ ] Admin export → **Export project** → "Exporting…" ~1 s → filename chip (e.g. `assembly-line-a-full.iotsim`) + **Export again**; non-admin → "Export is restricted to Admin".
- [ ] Always-on warning "Credentials, private keys, and secrets are never included…".
- [ ] Environment section (read-only): API endpoint, mode, app version 0.1.0; shared mode adds "Your role" row.

---

## 10. Admin — Users (`/admin`)
- [ ] Non-admin → locked "Admin access is required."; admin/local → full page.
- [ ] Table: User (name+email), Role badge, Status badge, Last active; default sort name asc.
- [ ] Search by name/email → "N shown"; Role filter (Admin/User) + chip; Status filter (Active/Inactive) + chip; **Clear all**.
- [ ] Sortable columns.
- [ ] **Make Admin** / **Make User** → confirm dialog (from→to + "immediate" + "reversible"); confirm → processing → badge updates + success toast + activity entry ("Just now", "by You"); error toast (no entry); cancel = nothing.
- [ ] **Deactivate** (active) → warning confirm dialog → success toast + status→Inactive + activity entry; error toast.
- [ ] **Activate** (inactive) → no dialog, "Saving…" → status→Active + toast.
- [ ] Last-admin guard: attempting to demote/deactivate the only active admin → warning toast, no dialog.
- [ ] Role-change activity panel: entries (user, from→to badges, by, timestamp), empty state, updates only on success.

---

## 11. Notifications (`/notifications-demo`, admin-only dev surface)
- [ ] Non-admin → locked "developer review surface reserved for shared administrators".
- [ ] Push toast per tone (success/warning/error/stale/reconnecting/shared-impact) → bottom-right.
- [ ] Auto-dismiss (success/stale/reconnecting = 4 s); persistent (warning/error/shared-impact).
- [ ] Manual dismiss; **Clear all toasts** (banners unaffected); toasts stack.
- [ ] Banners persist until dismissed individually; stack.
- [ ] Inline notifications render all tones, optional action label triggers onAction, title-only variant.

---

## 12. Cross-Cutting

### 12.1 Edge states (every major list/detail)
- [ ] **Loading** → spinner + message, data hidden.
- [ ] **Empty** → contextual explanation of why no data.
- [ ] **No results** (filters) → "adjust the filters" guidance.
- [ ] **Error** → message + retry/back where applicable.
- [ ] **Locked** (permission) → info panel + role restriction (Evidence, Settings, Admin, Notifications).

### 12.2 Responsive (per DESIGN.md)
- [ ] `≥1024px` two-column shell always visible.
- [ ] `768–1023px` single column, rail behind hamburger, tables scroll horizontally, forms stack.
- [ ] `<768px` accessible (not optimized); dense tables scroll; forms usable.
- [ ] Consistent on latest Chrome/Firefox/Safari (Linux/Windows/macOS); IE unsupported.

### 12.3 Accessibility
- [ ] Full keyboard navigation + visible focus.
- [ ] Screen-reader names for actions/dialogs/tabs/status; hamburger `aria-expanded`/`aria-controls`.
- [ ] Status meaning not by color alone; tables/editors/live panels keyboard-safe.

### 12.4 Shared usage & risk
- [ ] Authorship visible in context (run/recording/export/edit initiator).
- [ ] Concurrent editing → editable / read-only-by-role / read-only-by-other-editor / stale states distinguishable.
- [ ] Destructive actions → confirmation explaining object, shared impact, connected-device impact, reversibility.
- [ ] Exports never suggest secrets/credentials/keys are included.

---

## 13. Stub Routes & Known Gaps
- ⚠️ `/activity` → **SurfaceStubPage** "Activity" placeholder (no activity stream yet).
- ⚠️ `/projects/import` → **SurfaceStubPage** "Import Project" placeholder.
- ⚠️ `/design-system`, `/notifications-demo` → developer surfaces (notifications is admin-gated).
- ⚠️ Overview: no "Automated" or per-run evidence badge yet.
- ⚠️ Data source header parameter count may show 0 (schema node count not fetched — UI-097).
- ⚠️ Values tab: pin filter present, no pin control.
- ⚠️ Schema editor: edit-lock always "unlocked" (backend lock not exposed — UI-097).
- ⚠️ Scenario run view: source & events sections are placeholders.
- ⚠️ Create-source wizard: no "Manual schema" basis (UI-121).
- ⚠️ Recording/replay import dialogs use mock validation heuristics, not real file inspection.
