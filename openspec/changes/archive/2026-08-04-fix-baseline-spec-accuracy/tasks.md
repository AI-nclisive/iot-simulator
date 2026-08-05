## 1. Spec corrections

- [x] 1.1 Write ADDED limitation requirements for `protocol-model`, `worker-contract`, `db-schema`, `api-contract`
- [x] 1.2 Write MODIFIED requirements correcting `frontend-shell` nav, `frontend-screens` page inventory, and `auth-modes` role scope
- [x] 1.3 `openspec validate fix-baseline-spec-accuracy --strict` passes

## 2. Tooling and workflow fixes found in the same audit

- [x] 2.1 Declare the `@fission-ai/openspec` CLI as a devDependency and add it to `CONTRIBUTING.md` prerequisites (skills invoke bare `openspec`, which was on no contributor's PATH)
- [x] 2.2 Fix the wrong command in the `open-pr` skill: `openspec change archive` does not exist — the command is `openspec archive`
- [x] 2.3 Record in `AGENTS.md` that only `### Requirement:` blocks survive `openspec archive`
- [x] 2.4 Add an OpenSpec workflow section to `README.md`

## 3. Verify

- [x] 3.1 Archive the change, then re-read `openspec/specs/` and confirm the new text is actually present (not dropped)
- [x] 3.2 `./gradlew build` green
- [x] 3.3 `npm run typecheck && npm run build` green
