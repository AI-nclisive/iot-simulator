## Context

See proposal.md — Why. Every requirement here was written from the actual
controller: the mapping inventory came from grepping `@RequestMapping` /
`@*Mapping` across `api/src/main/java`, and each behavioral claim was checked in
the controller or the service behind it.

Two claims were corrected during that check rather than shipped as written:
`/meta` is **not** on the unauthenticated allowlist (only Actuator health/info
and the OpenAPI/Swagger docs are), and the synthetic run's duration cap really
does terminate the run — the pacer's tick finalizes it as `COMPLETED` once
elapsed time reaches the cap, so it is a genuine end condition, not just a
reported field.

## Goals / Non-Goals

- Goal: every endpoint group in `api/src/main/java` is covered by at least one
  requirement, so no shipped endpoint is invisible to the spec.
- Non-goal: per-endpoint documentation. Requirements are grouped by the promise
  they make (artifact export/download/import is one requirement across
  recordings and samples, not two), because that is the unit a future change
  breaks. Exact paths, DTO shapes, and query parameters stay in OpenAPI, which
  is generated and cannot drift.
- Non-goal: changing or judging any of these endpoints. Where behavior looks
  odd but is deliberate (observability queries answering `200` for an unknown
  id), the requirement states it and says why.

## Decisions

- **Group by promise, not by path.** The three-step
  export → download → import flow is identical for recordings and samples, so
  it is one requirement with the symmetry stated. Splitting per resource would
  duplicate the `404`-before-first-export rule and let the two copies drift.
- **State the deliberate leniency explicitly.** `GET /clients` and
  `GET /health` answer `200` with an empty/stopped result for an unknown source
  id. That reads like a bug unless the spec says it is intentional and why
  (dashboards want an unambiguous "nothing running", and it mirrors the sibling
  SSE endpoints), so the requirement carries the rationale.
- **Dashboard reads are called out as concurrency-free.** `active-runs` and
  `projects/overview` are derived aggregations with no `ETag`. Saying so stops
  someone "fixing" the missing `ETag` that the rest of the API has.
- **The public allowlist became its own requirement.** Verifying the `/meta`
  claim surfaced that the allowlist itself — what is reachable without a token —
  was specified nowhere. It is a security-relevant contract, so it is now
  explicit rather than implied.

## Risks / Trade-offs

- [Risk] Grouping means one requirement can cover several endpoints, so a change
  touching only one of them still has to re-read the whole requirement →
  Mitigation: each requirement's scenarios name the specific behaviors, so the
  relevant one is easy to find.
- [Risk] Coverage is measured against today's controller inventory; a new
  controller added later is invisible again → Mitigation: adding an endpoint is
  a behavior change, so it already requires a spec delta under the normal flow.

## Migration Plan

Archive the change, then confirm the added requirements are present in
`openspec/specs/api-contract/spec.md` (the prose-drop failure fixed in
`fix-baseline-spec-accuracy` makes re-reading the live spec after archive
mandatory, not optional).
