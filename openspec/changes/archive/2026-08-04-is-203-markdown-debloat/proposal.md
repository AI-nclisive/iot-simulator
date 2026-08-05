## Why

Tracked markdown is 74 files / 7961 lines. The count is not the problem;
duplication and the drift it causes are.

`AGENTS.md` opens with "Keep each fact in one place — don't restate content that
lives in another file", and the docs violate it: the module map exists in three
files, the build commands in four, governance in four, the board status flow in
three, and the branch-protection `gh api` JSON byte-identical in
`CONTRIBUTING.md` and `.github/OWNER_SETUP.md`. Every copy is a place the truth
can rot, and some already have:

- `ARCHITECTURE_DIAGRAM.md` labels the UI "TanStack Query · Zustand · Radix ·
  Tailwind"; `STACK.md` explicitly bans TanStack Query and Radix, and
  `package.json` has neither.
- `STACK.md` claims Java "21 also supported"; the toolchain pins 25 and CI sets
  up 25 only.
- `README.md` says to leave `VITE_API_BASE_URL` unset in dev; `.env.example`
  ships it set to `http://localhost:8080`.
- `frontend/docs/UI_PLAN.md` still links four times to `UI_TASKS.md`, retired in
  `3e52cbb`.

On top of that, 2106 lines (26% of tracked markdown) is openspec CLI output
committed twice — `.claude/commands/opsx/*.md` and
`.claude/skills/openspec-*/SKILL.md` have byte-identical bodies, differing only
in frontmatter — and `frontend/docs/QA_CHECKLIST.md` (414 lines) is referenced by
nothing while already contradicting the shipped routes.

## What Changes

- Delete the six `.claude/skills/openspec-*/` directories. The whole repo
  (`README.md`, `CONTRIBUTING.md`, `AGENTS.md`, the `start-task` and `open-pr`
  skills, `catalog-sync-check.sh`) invokes the `/opsx:*` command half; nothing
  outside the skills referenced the skills. Record in `CONTRIBUTING.md` that the
  delivery mode is commands-only.
- Delete `frontend/docs/QA_CHECKLIST.md` — an orphan manual checklist that has
  drifted from the routes it checks. Frontend behavior is specified in
  `openspec/specs/frontend-{shell,screens}/spec.md`.
- Fold `ARCHITECTURE_DIAGRAM.md` (an orphan — nothing linked to it) into
  `ARCHITECTURE.md`, correcting the frontend dependency label on the way.
- Replace duplicated blocks in `README.md` and `CONTRIBUTING.md` with links to
  the file that owns the fact.
- Correct the stale statements listed above, plus the `.github/OWNER_SETUP.md`
  "all applied" status line (an item is still open) and the `Ids.java` javadoc
  still citing the deleted `backend-specs/`.
- Leave `openspec/changes/archive/` alone: it is immutable change history, the
  `catalog-sync` gate keys off new directories there, and nothing reads archived
  file contents.

No behavior change and no spec delta — docs plus one javadoc line, hence
`skip_specs: true`.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none — documentation and repo hygiene only)
