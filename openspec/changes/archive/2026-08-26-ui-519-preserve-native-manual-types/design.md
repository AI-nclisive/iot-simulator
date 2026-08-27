## Context

See [proposal.md](proposal.md). `SyntheticVariableConfig` already has a `dataTypeNodeId` field and the domain model validates native types separately, but the frontend profile emits only `dataType`. Its fallback turns a native variable into `FLOAT64` before the request reaches the backend.

## Goals / Non-Goals

**Goals:**

- Carry a selected manual schema variable's native-type reference into the synthetic configuration.
- Keep primitive profile generation unchanged.
- Make native-variable configuration valid only when it can use the existing native-type execution path.

**Non-Goals:**

- Adding a new worker type encoding or changing normalized schema storage.
- Adding dynamic generators for structures, unions, enums, or option sets.

## Decisions

### Preserve the type reference in the profile payload

For a variable with `dataTypeNodeId`, emit `dataType: null` and its node ID rather than defaulting to `FLOAT64`. This matches the domain `SyntheticVariable` invariant and lets its existing constant-only validation decide whether the configured value is executable.

Alternative: reject all native variables in the frontend. Rejected because it would remove the native-type path delivered by IS-200 and make a valid structured constant impossible.

### Validate in both UI and domain

The UI will avoid presenting unsupported native generation choices and produce an actionable invalid profile. The domain remains the authoritative validation boundary for direct API callers.

Alternative: only fix the frontend fallback. Rejected because future UI regressions or API clients could reintroduce a coerced request.

## Risks / Trade-offs

- [A local custom type may lack an executable encoding] → preserve it only through the existing native execution strategy; otherwise surface its variable/type as invalid before source creation.
- [The manual schema editor can contain types that normalized source schemas cannot store] → rely on the existing backend rejection path instead of silently changing the declaration.
