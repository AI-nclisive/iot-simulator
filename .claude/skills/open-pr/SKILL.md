---
name: open-pr
description: >-
  Open the pull request for the current task the project's way. Use when the
  implementation is ready: runs the Definition-of-Done checks, flips the task's
  catalog checkbox in the SAME PR (the CI catalog-sync gate), creates the PR
  with `Implements: IS-XXX` and the filled template, arms squash auto-merge, and
  moves the board to In review. Invoke as `/open-pr` from the feature branch.
---

# Open the PR (project workflow)

Canonical rules: `CONTRIBUTING.md` → "Commits & PRs", "Definition of Done",
"Task tracking". This skill is the executable procedure.

Repo: `AI-nclisive/iot-simulator`. Run from the task's feature branch.

## 1. Definition of Done — verify green first

- `./gradlew build` (compile + unit + ITs + ArchUnit) — must be green
  ([[always-compile-and-test]]). Report the real result.
- If the frontend changed: `npm ci && npm run typecheck && npm run build`.
- No secrets / credentials / PKI committed; generated code (jOOQ/proto) stays
  under `build/`, never committed.
- Public behavior changes reflected in OpenAPI (and specs if needed).
- **Run the review skills locally first to cut review rounds:** `/code-review` for
  correctness/cleanup, and `/security-review` (the CI Claude reviewer runs anyway,
  but local passes shorten the [[review-loop]]). For behavior-affecting changes,
  `/verify` (or `/run`) to confirm it actually works, not just that it compiles.

## 2. Catalog checkbox — automated, just link the issue

The catalog box is ticked **automatically**: while the PR is open, the
`auto-tick-catalog` workflow flips this task's `[ ]` → `[x]` (in
`backend-specs/TASKS.md` for `IS-*`, `frontend/docs/UI_TASKS.md` for `UI-*`) and
commits it onto the branch. Do **not** flip it by hand.

Your only job is to make it findable: put **`Closes #<issue>`** in the PR body
(step 3) so the auto-tick resolves the task from the native issue link — the branch
name (`feat/IS-XXX-…`) is the fallback. The tick maps to board **Done** only after
the PR *merges* — see step 5.

## 3. Push & create the PR

```bash
git push -u origin HEAD
gh pr create --base master --title "IS-038 short name" --body "$(cat <<'EOF'
Implements: IS-038
Closes: #<issue>

<short summary>

<-- fill the .github/pull_request_template.md checklist -->
EOF
)"
```

All GitHub text in **English**. Conventional Commit style for the title.

## 4. Arm auto-merge immediately (always, never wait for a manual go)

```bash
gh pr merge <n> --auto --squash
```

Safe to arm early — branch protection only lets it fire once the Claude reviewer
APPROVEs and `build` is green. Squash only; the branch auto-deletes on merge.

## 5. Move the board to In review

```bash
ISSUE=<issue#>
ITEM_ID=$(gh project item-list 1 --owner AI-nclisive --format json --limit 500 \
  | jq -r ".items[] | select(.content.number==$ISSUE) | .id")
gh project item-edit --id "$ITEM_ID" --project-id PVT_kwDOEatAic4BbjmE \
  --field-id PVTSSF_lADOEatAic4BbjmEzhWTT9A --single-select-option-id 785478fd   # In review
```

Do **not** move to Done now — Done happens only after the PR merges (board field
IDs are listed in the `start-task` skill). Next: work the review with `/review-loop`.
