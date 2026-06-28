---
name: start-task
description: >-
  Claim and start a board task BEFORE writing any code. Use at the very
  beginning of work on an IS-XXX (backend/SDLC) or UI-XXX (frontend) task:
  verifies the task is free, flips the board Status to In Progress, and creates
  the linked feature branch. Invoke as `/start-task IS-038` (or UI-012).
---

# Start a task (claim-first)

Canonical rules live in `CONTRIBUTING.md` → "Branching" / "Task tracking" and
`AGENTS.md` → "Contributions and task tracking". This skill is the **executable
procedure** for those rules — keep the rules in the docs, the commands here.

**Input:** the task ID from the argument (e.g. `IS-038`, `UI-012`). If none was
given, ask which task before doing anything.

Repo: `AI-nclisive/iot-simulator`. Board: org Project **#1** "IoT Simulator".

## 0. Resolve the task

- Catalog line: `IS-*` → `backend-specs/TASKS.md`; `UI-*` → `frontend/docs/UI_TASKS.md`.
- Find the issue (titled `IS-XXX name` / `UI-XXX name`):
  ```bash
  gh issue list --search "IS-038 in:title" --state all --json number,title,state -L 5
  ```

## 1. Verify it is FREE (in this order — on conflict the board wins)

Stop and report if any check fails; do not claim a taken task.

1. **Board Status == `Todo`** (see field IDs below).
2. **No open or merged PR references the ID:**
   ```bash
   gh pr list --search "IS-038" --state all
   ```
3. **Issue is open and unclaimed.**

The catalog checkbox alone is NOT proof of freedom — it can lag the board.

## 2. Claim it — flip board Status → In Progress (FIRST, before any code)

This is the opening action, never a backfill. A late flip leaves the board on
`Todo` and misleads others into taking a claimed task.

```bash
ISSUE=<issue#>
ITEM_ID=$(gh project item-list 1 --owner AI-nclisive --format json --limit 500 \
  | jq -r ".items[] | select(.content.number==$ISSUE) | .id")
gh project item-edit --id "$ITEM_ID" --project-id PVT_kwDOEatAic4BbjmE \
  --field-id PVTSSF_lADOEatAic4BbjmEzhWTT9A --single-select-option-id 804ce738   # In Progress
```

## 3. Create + link the feature branch

```bash
gh issue develop "$ISSUE" --name feat/IS-038-short-slug --base master
git fetch origin && git checkout feat/IS-038-short-slug
git branch --set-upstream-to=origin/feat/IS-038-short-slug
gh issue develop --list "$ISSUE"   # verify the issue↔branch link shows on the board
```

Branch prefix matches the change type: `feat/` `fix/` `docs/` `chore/` `test/`.

## 4. Only now implement

After this, write code. When done, build & test ([[always-compile-and-test]]) and
open the PR with `/open-pr`.

---

## Board field/option IDs (re-query if the board was recreated)

- Project: `PVT_kwDOEatAic4BbjmE` (owner `AI-nclisive`, number `1`)
- `Status` field `PVTSSF_lADOEatAic4BbjmEzhWTT9A`:
  Todo=`5ac385f5`, In Progress=`804ce738`, In review=`785478fd`, Done=`949e2c5c`
- `Area` field `PVTSSF_lADOEatAic4BbjmEzhWTdw8`: BE=`b1bea5fe`, FE=`80dac005`, SDLC=`07562e45`
- `Task ID` text field `PVTF_lADOEatAic4BbjmEzhWTUOg`

These are GitHub Project IDs (not secrets). If the option set was rewritten the
single-select option IDs change — re-query with
`gh project field-list 1 --owner AI-nclisive --format json`.
