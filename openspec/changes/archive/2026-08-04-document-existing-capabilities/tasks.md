## 1. Delete superseded docs

- [x] 1.1 Delete `backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md` through `backend-specs/08_AUTH_AND_MODES.md`
- [x] 1.2 Delete `SPEC.md`
- [x] 1.3 Delete `frontend/docs/DESIGN.md` and `frontend/docs/UI_SCREEN_SPECS.md`
- [x] 1.4 Delete `docs/FRONTEND_BACKEND_CONTRACT_MAP.md`

## 2. Fix dangling references

- [x] 2.1 Grep the repo for references to the deleted files (`backend-specs/`, `SPEC.md`, `DESIGN.md`, `UI_SCREEN_SPECS.md`, `FRONTEND_BACKEND_CONTRACT_MAP.md`) outside `.claude/skills/` and `AGENTS.md`/`CONTRIBUTING.md` (those are handled by separate follow-up changes) and update or remove any that remain

## 3. Verify

- [x] 3.1 `openspec validate document-existing-capabilities --strict` passes
- [x] 3.2 `./gradlew build` stays green (no code depends on the deleted docs)
- [x] 3.3 `npm run typecheck && npm run build` stays green
