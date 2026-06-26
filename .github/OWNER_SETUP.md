# Repo Configuration (admin-applied)

Record of the one-time **admin** configuration applied to `AI-nclisive/iot-simulator`,
with the `gh` commands to re-apply it (after a settings reset, or when recreating /
forking the repo). Run them as an admin (`gh auth status` shows admin).

Status: **all applied.** The trunk is established on `master` (IS-097 [SDLC]); CI,
PR/issue templates, `CONTRIBUTING.md`, `AGENTS.md` rules, and repo labels are in place.

## Branch protection — `master` (IS-099 [SDLC])
Required status check `build` (= the CI job name), 1 approving review, linear history,
no force-push/deletion, `enforce_admins=false` (admins can merge without a second
approval; raise to `true` once a second reviewer exists).
```bash
gh api -X PUT repos/AI-nclisive/iot-simulator/branches/master/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 1 },
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "restrictions": null
}
JSON
```

## Merge settings
Squash-only, with auto-delete of merged branches, and **auto-merge enabled** so a PR
merges itself once branch protection is satisfied (the Claude review's APPROVE + green
`build`). Auto-merge is armed per PR with `gh pr merge <n> --auto --squash` (see
`CONTRIBUTING.md`); it stays queued until the required approval and check pass.
```bash
gh api -X PATCH repos/AI-nclisive/iot-simulator \
  -F allow_squash_merge=true -F allow_merge_commit=false \
  -F allow_rebase_merge=false -F delete_branch_on_merge=true \
  -F allow_auto_merge=true
```

Test reports are surfaced by CI as a clickable "Tests" check (dorny/test-reporter)
plus a downloadable `test-reports` artifact — no GitHub Pages (dropped in #6).

## Project board (IS-103 [SDLC])
Org project **IoT Simulator** — <https://github.com/orgs/AI-nclisive/projects/1>,
linked to the repo, with fields: single-select `Status` (Todo / In Progress /
In review / Done), text `Task ID` (`IS-XXX` or `UI-XXX`), single-select `Area`
(BE / FE / SDLC). Live status lives here; `backend-specs/TASKS.md` and
`frontend/docs/UI_TASKS.md` stay the catalogs. Created (needs the `project`
token scope to re-create):
```bash
gh auth refresh -s project,read:project
gh project create --owner AI-nclisive --title "IoT Simulator"
```

## Claude PR review setup (IS-112 [SDLC])
The `.github/workflows/claude-review.yml` workflow runs a Claude review on
every PR (see the workflow header). It posts inline + verdict comments and submits a
**formal review** (APPROVE / REQUEST_CHANGES) that gates merge — the Claude GitHub
App's APPROVE counts toward branch protection's 1-approval rule. It needs **two**
things — both are required; the token alone is not enough:

**1. The Claude GitHub App** — provides the GitHub identity/permissions the action
uses to read the PR and post review comments. Install it on this repo (org-owned, so
an **org owner must approve** the install):
```bash
# Easiest, from the repo dir — installs the App and sets the secret:
claude   # then run: /install-github-app
```
Or install manually at <https://github.com/apps/claude> → choose the `AI-nclisive`
org → **Only select repositories** → `iot-simulator`. Symptom when missing: the job
fails with "Claude Code is not installed on this repository."

**2. The Claude API token** — a Claude Pro/Max **subscription OAuth token** (not an
Anthropic API key), stored as the repo secret `CLAUDE_CODE_OAUTH_TOKEN`:
```bash
claude setup-token   # prints the OAuth token (Pro/Max subscription)
gh secret set CLAUDE_CODE_OAUTH_TOKEN --repo AI-nclisive/iot-simulator
```
The token is long-lived; rotate by re-running both commands. The workflow uses the
`pull_request` event, so the secret is withheld from fork PRs (the job no-ops there).
The review **gates merge**: its APPROVE satisfies branch protection's 1-approval rule
and its REQUEST_CHANGES blocks merge; the required status check stays `build`. The
APPROVE counts only because it is submitted by the **Claude GitHub App** identity (not
`github-actions[bot]`, whose approvals GitHub ignores) and that identity is **not the
PR author** — a PR opened under the same app identity could not be self-approved.

## Labels
9 repo labels are defined in `.github/labels.yml`; (re)create with `gh label create ... --force`.

## Notes
- Issue/PR templates and CI config are read from the **default branch** (`master`).
- Grant collaborators write access (and reviewers) so the 1-approval rule has approvers
  other than the PR author; then `enforce_admins` can be raised to `true`.
