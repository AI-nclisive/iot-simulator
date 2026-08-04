## Context

See proposal.md — Why. This is the second pass of the coverage work: the first
pass (`cover-remaining-api-endpoints`) closed the auxiliary surfaces, and
re-running the inventory diff against the live spec afterwards exposed four core
groups that were still uncovered because incidental mentions ("scenario builder",
"scenario runs") made them *look* covered by grep.

## Goals / Non-Goals

- Goal: every endpoint group in `api/src/main/java` is referenced by at least one
  requirement, verified by diffing the mapping inventory against the live spec
  rather than by eyeballing it.
- Non-goal: specifying scenario step semantics. Step types and their params are
  the `artifact-formats` capability's contract; this only covers the HTTP surface
  that authors, validates, and runs them.

## Decisions

- **Validate-before-run gets its own requirement.** It is the one scenario
  endpoint with a promise worth stating: it diagnoses without side effects.
  Folding it into generic CRUD would lose that.
- **Async start and step streaming are one requirement.** They are two halves of
  the same promise (the call returns immediately, so progress must be observable
  elsewhere); splitting them would let one be satisfied without the other.
- **Schema save is specified as whole-set replace, explicitly not a patch.** The
  endpoint is a `PUT` taking the complete node set, and a missing node list is
  rejected rather than read as "delete everything" — worth stating because that
  is exactly the destructive misreading a future change might introduce.
- **Runs are specified as one resource across kinds.** The value in the contract
  is that a scenario run and a replay run are the same resource; that is the
  property a future change could break by adding a kind-specific runs endpoint.

## Risks / Trade-offs

- [Risk] "Every endpoint group is covered" is true against today's inventory and
  silently stops being true when a controller is added → Mitigation: adding an
  endpoint is a behavior change and already needs a spec delta under the normal
  flow; the inventory diff (`grep` the mappings, compare to requirements) is
  cheap to re-run.

## Migration Plan

Archive, then re-read `openspec/specs/api-contract/spec.md` to confirm the
requirements landed, and re-run the inventory diff to confirm nothing is left
uncovered.
