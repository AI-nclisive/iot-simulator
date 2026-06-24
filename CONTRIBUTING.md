# Contributing

This repo is built in parallel by multiple people and agents via feature branches
and pull requests. These rules keep that smooth.

## Prerequisites
- JDK 25 (toolchain target). Gradle runs via the committed wrapper (`./gradlew`).
- Docker (Docker Desktop / colima) — required for Testcontainers integration
  tests. Without Docker those ITs skip locally; CI always runs them.

## Build & test
```bash
./gradlew build        # compile + all tests (unit + ITs + ArchUnit)
```
Always run this and confirm it is green before opening a PR.

## Task IDs
- Every task has a project-wide ID and area marker, named **`IS-XXX [AREA] short name`** —
  `[AREA]` is `[BE]` (backend), `[FE]` (frontend) or `[SDLC]` (repo/process).
- Catalog: `backend-specs/TASKS.md` (with a legacy `BE-*`/`SDLC-*` → `IS-XXX` crosswalk).
- Reuse the ID everywhere: branch `feat/IS-038-...`, issue `IS-038 short name` (area via the form's **Area** field), PR `Implements: IS-038`.

## Branching
- Branch off `master`. One task per branch/PR.
- Name: `feat/IS-123-short-slug`, `fix/...`, `docs/...`, `chore/...`, `test/...`.

## Commits & PRs
- Conventional Commits: `type(scope): subject` (e.g. `feat(schema): ...`).
- Reference the task in the PR: `Implements: IS-123`, `Closes: #<issue>`.
- Keep PRs small and focused; fill in the PR template checklist.
- Merge strategy: **squash merge**, linear history. CI must be green and at least
  one approving review is required.

## Definition of Done
- `./gradlew build` green (tests added/updated for the change).
- No secrets/credentials/PKI committed; secrets come from env/secret store.
- Generated code (jOOQ/proto) stays under `build/` — never committed.
- Public behavior changes reflected in OpenAPI and, if needed, the specs.

## Task tracking
- `backend-specs/TASKS.md` is the **catalog** of tasks (IS-IDs) and a periodic
  status snapshot. **Live status/assignment lives in GitHub Issues/Project**
  keyed by IS-ID — do not flip `TASKS.md` checkboxes inside feature PRs (it is a
  merge-conflict hotspot). Maintainers sync the snapshot periodically.
- File tasks with the **Backend task** issue form; labels are defined in
  `.github/labels.yml`. The Project board (live status by IS-ID) is an admin
  one-time setup: `gh project create` (needs the `project` token scope) or create
  it in the GitHub UI with a `Task ID` field, an `Area` (BE/FE/SDLC) field, and a status column.

## Parallel-work conventions
- **Flyway migrations**: never reuse a version number. Two open PRs adding
  `V7__...` will both merge and break ordering. Use the next free `Vn` only if you
  are sure it is unique, otherwise use a timestamped version
  (`V20260623_1530__name.sql`). Migrations are append-only.
- **Modules**: prefer changes scoped to one Gradle module to reduce conflicts.
- **Versions**: add/bump dependencies only in `gradle/libs.versions.toml`.

## Governance
- `SPEC.md`, `ARCHITECTURE.md`, `STACK.md`, `backend-specs/` change only with
  prior owner approval — propose first (see `AGENTS.md`). No new dependency
  without approval.

## Branch protection (repo admin)
Applied configuration record: [`.github/OWNER_SETUP.md`](.github/OWNER_SETUP.md). Requires
**admin** on the repo. `master` is protected (status check `build` = the CI job name):
```bash
gh api -X PUT repos/AI-nclisive/iot-simulator/branches/master/protection \
  --input - <<'JSON'
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
Also, in repo Settings: allow **squash merging only** and enable
"Automatically delete head branches". (Set `enforce_admins` to `true` later for a
stricter policy once more than one approver is available.)
