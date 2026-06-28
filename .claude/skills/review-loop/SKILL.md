---
name: review-loop
description: >-
  Work the automated Claude PR review to completion. Use after a PR is open and
  the reviewer has run: read the verdict and inline comments, fix-or-rebut each
  finding, push to re-trigger the review, and repeat up to 3 rounds until the
  verdict is Mergeable. Invoke as `/review-loop` (optionally with the PR number).
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
  auto-merges. After it merges, finish task tracking (board → Done, close issue,
  catalog `[x]` already flipped in the PR — see `start-task` for board IDs).
- Or **3 rounds** completed → summarize any still-open points in the PR description
  for a human reviewer, then stop.

## Waiting for the reviewer

If the review hasn't posted yet, poll instead of blocking — pair with `/loop`:
`/loop /review-loop <n>` lets it self-pace until the verdict appears.
