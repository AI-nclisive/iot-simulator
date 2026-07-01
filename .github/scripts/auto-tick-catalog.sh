#!/usr/bin/env bash
#
# auto-tick-catalog.sh
#
# Automates the "Done is recorded in the catalog" step: when a PR is open, flip
# its task's checkbox `[ ]` -> `[x]` in the owning catalog and commit the change
# onto the PR's own branch, so the tick merges in with the rest of the work.
#
# This replaces the old `catalog-sync` guard (which only *nagged* that a human
# edited the line, was never a required check, and let misses through). Here a
# bot does the edit, so there is nothing left to forget.
#
# Task ID resolution (most reliable first):
#   1. The PR's native closing-issue link (`closingIssuesReferences`, from
#      `Closes #N`) -> that issue's title -> the IS-/UI-XXX it starts with.
#   2. Fallback: the head branch name (e.g. feat/IS-122-slug), which /start-task
#      always stamps with the ID.
# Using the native link means no fragile regex over free-text PR bodies.
#
# The commit lands on the *feature* branch (unprotected), so the default
# GITHUB_TOKEN can push it — no PAT / app token / master-bypass needed.
#
# Inputs (from the CI environment):
#   GH_TOKEN   — token with repo read + contents:write (the workflow GITHUB_TOKEN)
#   REPO       — owner/repo (github.repository)
#   PR_NUMBER  — the pull request number
#   HEAD_REF   — the PR head branch name (github.head_ref)
#
# Exit codes:
#   0 — nothing to do (already ticked, or no task id) OR tick committed & pushed
#   1 — a real error (could not resolve/parse/edit)

set -euo pipefail

REPO="${REPO:?REPO is required}"
PR_NUMBER="${PR_NUMBER:?PR_NUMBER is required}"
HEAD_REF="${HEAD_REF:-}"

BACKEND_CATALOG="backend-specs/TASKS.md"
FRONTEND_CATALOG="frontend/docs/UI_TASKS.md"

owner="${REPO%%/*}"
name="${REPO##*/}"

# ── 1. Resolve the task ID ───────────────────────────────────────────────────
# 1a. Native closing-issue link -> issue title -> leading IS-/UI-XXX.
issue_titles="$(gh api graphql -f query='
  query($owner:String!, $name:String!, $pr:Int!) {
    repository(owner:$owner, name:$name) {
      pullRequest(number:$pr) {
        closingIssuesReferences(first:20) { nodes { title } }
      }
    }
  }' -F owner="$owner" -F name="$name" -F pr="$PR_NUMBER" \
  --jq '.data.repository.pullRequest.closingIssuesReferences.nodes[].title' 2>/dev/null || true)"

TASK_ID="$(printf '%s\n' "$issue_titles" \
  | grep -ioE '(IS|UI)-[0-9]+' | head -n1 || true)"

# 1b. Fallback: the head branch name.
if [[ -z "$TASK_ID" ]]; then
  TASK_ID="$(printf '%s' "$HEAD_REF" | grep -ioE '(IS|UI)-[0-9]+' | head -n1 || true)"
fi

if [[ -z "$TASK_ID" ]]; then
  echo "auto-tick: no IS-/UI-XXX found via closing issue or branch name — nothing to do."
  exit 0
fi
TASK_ID="$(printf '%s' "$TASK_ID" | tr '[:lower:]' '[:upper:]')"
echo "auto-tick: resolved task ${TASK_ID}"

# ── 2. Pick the owning catalog ───────────────────────────────────────────────
case "$TASK_ID" in
  IS-*) CATALOG="$BACKEND_CATALOG" ;;
  UI-*) CATALOG="$FRONTEND_CATALOG" ;;
  *) echo "auto-tick: unrecognised task prefix in ${TASK_ID}" >&2; exit 1 ;;
esac

if [[ ! -f "$CATALOG" ]]; then
  echo "auto-tick: catalog ${CATALOG} not found" >&2
  exit 1
fi

# ── 3. Locate the task's checkbox line ───────────────────────────────────────
# Match a Markdown task line "- [ ] IS-122 ..." / "- [x] IS-122 ..." for THIS id.
# The id is bounded by a non-word char so IS-12 never matches IS-122.
line_no="$(grep -nE "^[[:space:]]*-[[:space:]]*\[[ xX]\][[:space:]]*${TASK_ID}([^0-9]|$)" "$CATALOG" \
  | head -n1 | cut -d: -f1 || true)"

if [[ -z "$line_no" ]]; then
  echo "::warning::auto-tick: no checkbox line for ${TASK_ID} in ${CATALOG} — leaving it alone."
  exit 0
fi

line="$(sed -n "${line_no}p" "$CATALOG")"
if printf '%s' "$line" | grep -qE '^\s*-\s*\[[xX]\]'; then
  echo "auto-tick: ${TASK_ID} already ticked — nothing to do."
  exit 0
fi

# ── 4. Flip [ ] -> [x] on just that line ─────────────────────────────────────
# Only the first "[ ]" on the matched line; keep everything else byte-for-byte.
sed -i "${line_no}s/\[ \]/[x]/" "$CATALOG"
echo "auto-tick: flipped ${TASK_ID} in ${CATALOG}:"
sed -n "${line_no}p" "$CATALOG" | sed 's/^/    /'

# ── 5. Commit onto the PR's own (unprotected) branch ─────────────────────────
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add "$CATALOG"
git commit -m "chore(catalog): auto-tick ${TASK_ID} checkbox [skip ci]"
git push origin "HEAD:${HEAD_REF}"
echo "auto-tick: pushed the tick for ${TASK_ID} to ${HEAD_REF}."
