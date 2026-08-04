## 1. Remove duplicated and orphan files

- [x] 1.1 Delete `.claude/skills/openspec-{explore,propose,apply-change,archive-change,update-change,sync-specs}/` (byte-identical bodies to `.claude/commands/opsx/*.md`, referenced by nothing)
- [x] 1.2 Record the commands-only delivery mode in `CONTRIBUTING.md` (`openspec config set delivery commands`, machine-global — `openspec update` with the default recreates the skill dirs)
- [x] 1.3 Delete `frontend/docs/QA_CHECKLIST.md` (no inbound references; drifted from the shipped routes)

## 2. Single-source the root docs

- [x] 2.1 Fold both Mermaid diagrams from `ARCHITECTURE_DIAGRAM.md` into `ARCHITECTURE.md` and delete the file
- [x] 2.2 Replace the duplicated module map, prerequisites, build commands, openspec rules, and board/tracking blocks in `README.md` with links to `ARCHITECTURE.md` / `CONTRIBUTING.md` / `AGENTS.md`
- [x] 2.3 Drop the duplicated branch-protection JSON and merge settings from `CONTRIBUTING.md`, linking `.github/OWNER_SETUP.md` instead

## 3. Fix statements the code contradicts

- [x] 3.1 `STACK.md`: drop "21 also supported" (toolchain and CI are JDK 25 only)
- [x] 3.2 `ARCHITECTURE.md` (folded diagram): frontend label lists the real deps — no TanStack Query, no Radix; drop the unused 🟡 legend entry
- [x] 3.3 `README.md`: `VITE_API_BASE_URL` row states the real default (unset → empty; no `.env` is committed) and that `.env.example` is a template showing the production form; "two directories" over a three-row table
- [x] 3.4 `.github/OWNER_SETUP.md`: status line names the still-open board-automation item
- [x] 3.5 `frontend/docs/UI_PLAN.md`: repoint the four dead `UI_TASKS.md` links at the board / `openspec/changes/ui-*`
- [x] 3.6 `platform/.../Ids.java`: javadoc drops the deleted `backend-specs/` reference — no spec requires a sortable id, so it states UUIDv4 today and swappable later
- [x] 3.7 Left alone on purpose: `V20260723_1121__run_value_timeline.sql` mentions `backend-specs/TASKS.md` in a comment. Editing an applied migration changes its Flyway checksum and breaks validation on existing databases

## 4. Verify

- [x] 4.1 `./gradlew build` green
- [x] 4.2 No dangling references to the deleted files and no dead markdown links anywhere outside `openspec/changes/archive/`
- [x] 4.3 `npx openspec list`, `npx openspec list --specs`, and `npx openspec validate --strict` succeed
- [x] 4.4 `catalog-sync-check.sh` passes for `Implements: IS-203` once the change is archived
