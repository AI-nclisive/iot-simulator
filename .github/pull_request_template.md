<!-- One task per PR. Keep changes focused and reviewable. -->

## What & why
<!-- Short description. Link the task: Closes #<issue> / implements IS-XXX. -->

Implements: IS-___
Closes: #___

## Checklist
- [ ] `./gradlew build` is green locally (compile + tests)
- [ ] Tests added/updated for the change
- [ ] Conventional Commit title (`type(scope): subject`)
- [ ] No secrets/credentials/PKI committed; secrets come from env
- [ ] Generated code (jOOQ/proto) is **not** committed (stays under `build/`)
- [ ] Governance docs (`SPEC.md`, `ARCHITECTURE.md`, `STACK.md`, `backend-specs/`)
      changed only with prior owner approval
- [ ] New Flyway migration uses a unique version (no collision with open PRs)

## Notes for reviewers
<!-- Anything reviewers should focus on, trade-offs, follow-ups. -->
