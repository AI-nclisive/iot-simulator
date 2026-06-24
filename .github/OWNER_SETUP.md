# Repo Owner / Admin Setup (one-time)

Actions that require **admin** on `darqsatyr1c0n/iot-simulator`. They could not be
done from a WRITE-only account; do them once admin access is available. Run the
`gh` commands as an admin account (`gh auth status` should show admin).

Done already (no admin needed): CI workflow, PR template, CONTRIBUTING, AGENTS.md
code rules, issue forms, and 9 repo labels.

## 0. Prerequisites
- Admin on the repo (or perform these as owner `darqsatyr1c0n`).
- For the Project board, add the project scope to the token:
  ```bash
  gh auth refresh -s project,read:project
  ```

## 1. SDLC-1 — Establish the trunk (merge the foundation into `master`)
The whole backend currently lives on `feature/backend-foundation` (already pushed).
Get it onto `master` so others branch from a stable baseline. Do this **before**
turning on branch protection (or keep `enforce_admins=false`, which lets admins
merge it):
```bash
gh pr create --base master --head feature/backend-foundation \
  --title "Backend foundation: specs, scaffold, core slices, runtime, SDLC" \
  --body "Establishes the trunk. See backend-specs/ and CONTRIBUTING.md."
# review, then:
gh pr merge --squash --delete-branch
```

## 2. SDLC-3 — Branch protection on `master`
Requires admin. Status check `build` = the CI job name.
```bash
gh api -X PUT repos/darqsatyr1c0n/iot-simulator/branches/master/protection \
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
Flip `enforce_admins` to `true` later for a stricter policy once a second approver
exists.

## 3. Repo merge settings
```bash
gh api -X PATCH repos/darqsatyr1c0n/iot-simulator \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true
```

## 4. SDLC-8 — Project board (live status by BE-ID)
Needs the `project` token scope (step 0). Track tasks by BE-ID separately from
`backend-specs/TASKS.md` (which stays the catalog).
```bash
gh project create --owner darqsatyr1c0n --title "IoT Simulator Backend"
```
Then in the board: add a single-select `Status` field (Todo / In progress / In
review / Done) and a text `BE-ID` field; link issues created via the Backend task
form. (UI: Projects → New project → Board.)

## 5. GitHub Pages — browsable HTML reports
CI publishes the full Gradle + JaCoCo HTML reports (with navigation) to the
`gh-pages` branch, per PR under `pr-<n>/`. Enable Pages to serve them:
- Settings → Pages → Build and deployment → Source: **Deploy from a branch** →
  Branch: `gh-pages` `/ (root)` → Save.

The `gh-pages` branch is created automatically by the first CI run. Reports then
open at `https://darqsatyr1c0n.github.io/iot-simulator/pr-<n>/` (the link is also
printed in each run's job summary). Publishing is non-fatal, so it never blocks
the build.

## 6. Verify
- `master` now contains the backend; the **Actions** tab shows CI on PRs.
- Issue forms appear under **New issue** (they load from the default branch).
- Open a throwaway PR and confirm it cannot merge without a green `build` + 1
  approval.

## Notes
- Issue templates and CI config are read from the **default branch** (`master`),
  so they become fully active only after step 1.
- Grant collaborators write access (and reviewers) so the 1-approval rule has
  approvers other than the PR author.
