---
name: review-loop
description: >-
  Work the automated Claude PR review to completion, then close the task out on
  the board. Use after a PR is open and the reviewer has run: read the verdict and
  inline comments, fix-or-rebut each finding, push to re-trigger the review, repeat
  up to 3 rounds until the verdict is Mergeable, and once the PR has merged move
  the board to Done and close the issue. Invoke as `/review-loop` (optionally with
  the PR number).
---

# AI review loop

Canonical rules: `CONTRIBUTING.md` → "AI review loop". Reviewer workflow lives in
`.github/workflows/claude-review.yml` (IS-112). This skill drives it to done.

Repo: `AI-nclisive/iot-simulator`. Target = the current branch's PR unless a number
is given as the argument.

## How the reviewer gates merge

It posts inline comments tagged `[blocking]` / `[nit]` and one **verdict** comment,
then submits a formal GitHub review: **APPROVE** only when nothing blocks and every
thread is resolved, otherwise **REQUEST_CHANGES**. APPROVE + green `build` triggers
the armed auto-merge. **Resolving threads is the reviewer's prerogative, never the
author's** — you only respond.

## Each round

1. **Read the verdict + inline comments:**
   ```bash
   gh pr view <n> --comments
   gh api repos/AI-nclisive/iot-simulator/pulls/<n>/comments --paginate
   ```
2. **For every finding**, do one of:
   - **Fix it and reply** saying exactly what you changed; or
   - **Reply with a rationale** for leaving it as-is.
3. **Push** — a reply alone does NOT re-trigger the review; only a new push does:
   ```bash
   git push
   ```
4. Re-read the new verdict.

## Stop condition

- Verdict is `✅ Mergeable` with no unresolved comments → APPROVE lands and the PR
  auto-merges. Then close out task tracking — see "Close out after merge" below.
- Or **3 rounds** completed → summarize any still-open points in the PR description
  for a human reviewer, then stop.

## Close out after merge (board → Done)

The openspec change was already archived in the PR, so only the board and the
issue are left. **Verify the PR actually merged first** — auto-merge fires on its
own schedule, and an archived change on a still-open PR maps to `In review`, not
`Done`:

```bash
PR=<n>
gh pr view "$PR" --json state,mergedAt -q '"\(.state) \(.mergedAt)"'   # want: MERGED <timestamp>
```

Only once that reports `MERGED`:

```bash
ISSUE=$(gh pr view "$PR" --json closingIssuesReferences \
  -q '.closingIssuesReferences[0].number')
ITEM_ID=$(gh project item-list 1 --owner AI-nclisive --format json --limit 500 \
  | jq -r ".items[] | select(.content.number==$ISSUE) | .id")
gh project item-edit --id "$ITEM_ID" --project-id PVT_kwDOEatAic4BbjmE \
  --field-id PVTSSF_lADOEatAic4BbjmEzhWTT9A --single-select-option-id 949e2c5c   # Done
gh issue close "$ISSUE"
```

(`Closes: #<issue>` in the PR body is what makes GitHub close the issue on merge,
so `gh issue close` is usually a no-op — run it anyway; it is idempotent and
covers a PR whose body omitted the link.)

**This step is easy to lose.** Auto-merge lands whenever the reviewer approves and
`build` goes green, which is often minutes after you have moved on — and nothing
in CI moves the board. If a task sits in `In review` with a merged PR, that is
this step never running, not a re-implementation: fix it here or with
`/board-sync`. The durable fix is the board's own built-in workflow
("Pull request merged" → set `Status`), configured in the Project's Settings →
Workflows by the project owner; these commands stay the manual fallback.

## Waiting for the reviewer

If the review hasn't posted yet, poll instead of blocking — pair with `/loop`:
`/loop /review-loop <n>` lets it self-pace until the verdict appears.
