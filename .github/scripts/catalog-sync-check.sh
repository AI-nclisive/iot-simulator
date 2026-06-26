#!/usr/bin/env bash
#
# catalog-sync-check.sh
#
# Enforces the AGENTS.md "Done is recorded in two places" rule at CI time:
# if a PR body links a task (Implements: IS-XXX / UI-XXX, or Closes: #issue that
# maps to a task), the PR must also edit that task's checkbox line in the owning
# catalog (backend-specs/TASKS.md for IS-*, frontend/docs/UI_TASKS.md for UI-*).
#
# This stops a merged task from leaving a stale "[ ]" in the catalog, which is
# what let a completed task (IS-041) look "free" and get picked up twice.
#
# Inputs (from the CI environment):
#   PR_BODY   — the pull request description
#   BASE_SHA  — merge-base commit (diff target)
#   HEAD_SHA  — PR head commit
#
# Exit codes:
#   0 — OK (no task linked, or task linked and its catalog line was edited)
#   1 — violation (task linked but its catalog line was not edited)

set -euo pipefail

PR_BODY="${PR_BODY:-}"
BASE_SHA="${BASE_SHA:-origin/master}"
HEAD_SHA="${HEAD_SHA:-HEAD}"

BACKEND_CATALOG="backend-specs/TASKS.md"
FRONTEND_CATALOG="frontend/docs/UI_TASKS.md"

# ── 1. Extract the task ID the PR claims to implement ────────────────────────
# Accept "Implements: IS-041", "Implements: UI-095", case-insensitive, optional
# backticks/brackets. We only enforce on the Implements: line (the authoritative
# task link); Closes: may point at non-task issues (bugs), so it is not required
# to touch a catalog.
TASK_ID="$(printf '%s\n' "$PR_BODY" \
  | grep -ioE 'Implements:[[:space:]]*`?[\[]?(IS|UI)-[0-9]+' \
  | grep -ioE '(IS|UI)-[0-9]+' \
  | head -n1 || true)"

if [[ -z "$TASK_ID" ]]; then
  echo "catalog-sync: no 'Implements: IS-/UI-XXX' task link in PR body — skipping."
  echo "  (If this PR delivers a task, add 'Implements: IS-XXX' or 'UI-XXX' to the body.)"
  exit 0
fi

echo "catalog-sync: PR implements ${TASK_ID}"

# ── 2. Pick the catalog that owns this task ──────────────────────────────────
case "$TASK_ID" in
  IS-*) CATALOG="$BACKEND_CATALOG" ;;
  UI-*) CATALOG="$FRONTEND_CATALOG" ;;
  *)
    echo "catalog-sync: unrecognised task prefix in ${TASK_ID}" >&2
    exit 1
    ;;
esac

# ── 3. Did this PR change that task's line in the owning catalog? ────────────
# We look at the added/removed lines in the catalog diff and require that at
# least one of them mentions the task ID — i.e. the checkbox line was touched.
DIFF="$(git diff "${BASE_SHA}...${HEAD_SHA}" -- "$CATALOG" || true)"

if [[ -z "$DIFF" ]]; then
  echo "::error::catalog-sync: ${TASK_ID} is implemented by this PR, but ${CATALOG} was not modified."
  echo "Add the catalog checkbox flip for ${TASK_ID} (mark it '[x]') in THIS PR."
  echo "See AGENTS.md → Task Tracking ('Done is recorded in two places')."
  exit 1
fi

# Only consider changed (+/-) lines, ignore diff context, and require the task ID.
CHANGED_TASK_LINES="$(printf '%s\n' "$DIFF" \
  | grep -E '^[+-]' \
  | grep -vE '^(\+\+\+|---)' \
  | grep -F "$TASK_ID" || true)"

if [[ -z "$CHANGED_TASK_LINES" ]]; then
  echo "::error::catalog-sync: ${CATALOG} was changed, but not the ${TASK_ID} line."
  echo "This PR implements ${TASK_ID} — flip ITS checkbox to '[x]' in ${CATALOG} in this PR."
  echo "See AGENTS.md → Task Tracking ('Done is recorded in two places')."
  exit 1
fi

echo "catalog-sync: OK — ${TASK_ID} line edited in ${CATALOG}:"
printf '%s\n' "$CHANGED_TASK_LINES" | sed 's/^/    /'
exit 0
