---
name: board-sync
description: >-
  Reconcile the org Project #1 board with openspec's change/spec state. Use
  when you add, rename, remove, or re-scope a task, or when the board's
  Status/fields have drifted from openspec. Invoke as `/board-sync`
  (optionally with a task ID to reconcile just one).
---

# Board ↔ openspec sync

Owning rules: `CONTRIBUTING.md` → "Task tracking" and `AGENTS.md`. **openspec is the
source of truth; the board mirrors it, never the reverse**:

- `openspec/specs/<capability>/spec.md` — what the system does today.
- `openspec/changes/<id>-<slug>/` — an active `IS-*`/`UI-*` task's proposal.
- `openspec/changes/archive/<date>-<id>-<slug>/` — a completed task.

Org **Project #1 "IoT Simulator"** is the live-status mirror: **one issue per task
ID**, identified by the leading `IS-`/`UI-` token in the change-folder name. Repo
`AI-nclisive/iot-simulator`.

## Issue convention

Title `IS-XXX name` / `UI-XXX name` (no inline `[AREA]` — area is the board's `Area`
field). Label `type:task`; `priority:P0|P1|P2` for BE/FE, **no priority label for
SDLC**. Use the **Task** issue form (`.github/ISSUE_TEMPLATE/task.yml`).

## Status mapping

| openspec state | Board `Status` |
| --- | --- |
| no `openspec/changes/` folder for the ID yet | Todo |
| `openspec/changes/<id>-<slug>/` exists, not archived (claimed, coding) | In Progress |
| PR open / in review | In review |
| `openspec/changes/archive/<date>-<id>-<slug>/` exists **and the implementing PR merged** | Done (+ close the issue) |

**Caveat:** the change is archived *inside* the implementation PR, so an archived
change on an *open* PR still maps to **In review** — move to **Done** only once
that PR **merges**. On conflict, **the board wins**.

## How to apply (idempotent by leading ID token)

1. **Status change** → update that task's board `Status` by ID. Reuse the existing
   issue for an ID; **never create a duplicate**.
2. **Task added / renamed / removed** → create / edit / close the matching issue +
   board item; propose/archive the matching openspec change.
3. **Done is two-step:** the change is archived in the PR (no archive-only PR —
   direct push to `master` is blocked), then board → Done + close issue **after merge**.

### Small reconciliation (the common case) — drive the board directly with `gh`

```bash
ISSUE=<n>
# Add an issue to the board (skip if already there)
gh project item-add 1 --owner AI-nclisive --url https://github.com/AI-nclisive/iot-simulator/issues/$ISSUE
# Resolve its board item id
ITEM_ID=$(gh project item-list 1 --owner AI-nclisive --format json --limit 500 \
  | jq -r ".items[] | select(.content.number==$ISSUE) | .id")
PROJECT=PVT_kwDOEatAic4BbjmE
# Set Status (option IDs below)
gh project item-edit --id "$ITEM_ID" --project-id $PROJECT \
  --field-id PVTSSF_lADOEatAic4BbjmEzhWTT9A --single-select-option-id <status-option>
# Close the issue when Done
gh issue close $ISSUE
```

For a **full re-mirror** of all tasks (rare), the resumable parse/sync scripts are
maintained outside the repo (in the maintainer's environment), not committed here —
keep board-sync data out of VCS. For a handful of items use the `gh` calls above.

## Board field/option IDs (re-query if the board was recreated)

- Project: `PVT_kwDOEatAic4BbjmE` (owner `AI-nclisive`, number `1`)
- `Status` `PVTSSF_lADOEatAic4BbjmEzhWTT9A`:
  Todo=`5ac385f5`, In Progress=`804ce738`, In review=`785478fd`, Done=`949e2c5c`
- `Area` `PVTSSF_lADOEatAic4BbjmEzhWTdw8`: BE=`b1bea5fe`, FE=`80dac005`, SDLC=`07562e45`
- `Task ID` text field `PVTF_lADOEatAic4BbjmEzhWTUOg`

Single-select option IDs change if the option set is rewritten — re-query with
`gh project field-list 1 --owner AI-nclisive --format json`. These are GitHub Project
IDs, not secrets.
