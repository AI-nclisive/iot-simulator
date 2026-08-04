#!/usr/bin/env bash
#
# catalog-sync-check.sh
#
# Enforces the AGENTS.md "Done is recorded in openspec" rule at CI time:
# if a PR body links a task via `Implements: IS-XXX` or `Implements: UI-XXX`,
# the PR must also archive that task's openspec change
# (`openspec/changes/archive/<date>-<id>-<slug>/`) in the same PR.
#
# This stops a merged task from leaving no trace in openspec/specs/, and
# replaces the old backend-specs/TASKS.md / frontend/docs/UI_TASKS.md
# checkbox-flip check (those catalogs are retired).
#
# Inputs (from the CI environment):
#   PR_BODY   — the pull request description
#   BASE_SHA  — merge-base commit (diff target)
#   HEAD_SHA  — PR head commit
#
# Exit codes:
#   0 — OK (no task linked, or task linked and its openspec change was archived)
#   1 — violation (task linked but no matching archived change found)

set -euo pipefail

PR_BODY="${PR_BODY:-}"
BASE_SHA="${BASE_SHA:-origin/master}"
HEAD_SHA="${HEAD_SHA:-HEAD}"

ARCHIVE_DIR="openspec/changes/archive"

# ── 1. Extract the task ID the PR claims to implement ────────────────────────
# Accept "Implements: IS-041", "Implements: UI-095", case-insensitive, optional
# backticks/brackets. We only enforce on the Implements: line (the authoritative
# task link); Closes: may point at non-task issues (bugs), so it is not required
# to have an archived change.
TASK_ID="$(printf '%s\n' "$PR_BODY" \
  | grep -ioE 'Implements:[[:space:]]*`?[\[]?(IS|UI)-[0-9]+' \
  | grep -ioE '(IS|UI)-[0-9]+' \
  | head -n1 || true)"

if [[ -z "$TASK_ID" ]]; then
  echo "catalog-sync: no 'Implements: IS-/UI-XXX' task link in PR body — skipping."
  echo "  (If this PR delivers a task, add 'Implements: IS-XXX' or 'UI-XXX' to the body.)"
  exit 0
fi

TASK_ID_LOWER="$(printf '%s' "$TASK_ID" | tr '[:upper:]' '[:lower:]')"
echo "catalog-sync: PR implements ${TASK_ID}"

# ── 2. Did this PR add an archived openspec change for that task id? ─────────
# openspec archive moves openspec/changes/<id>-<slug>/ to
# openspec/changes/archive/<date>-<id>-<slug>/ — look for an added path under
# the archive dir whose name contains the lowercased task id.
DIFF_FILES="$(git diff --name-only --diff-filter=A "${BASE_SHA}...${HEAD_SHA}" -- "$ARCHIVE_DIR" || true)"

MATCHED="$(printf '%s\n' "$DIFF_FILES" | grep -i "$TASK_ID_LOWER" || true)"

if [[ -z "$MATCHED" ]]; then
  echo "::error::catalog-sync: ${TASK_ID} is implemented by this PR, but no matching"
  echo "${ARCHIVE_DIR}/<date>-${TASK_ID_LOWER}-<slug>/ was added."
  echo "Run 'openspec change archive <change-name>' (or /opsx:archive) for ${TASK_ID}'s"
  echo "change in THIS PR. See AGENTS.md -> Task Tracking."
  exit 1
fi

echo "catalog-sync: OK — ${TASK_ID} archived change found:"
printf '%s\n' "$MATCHED" | sed 's/^/    /' | sort -u
exit 0
