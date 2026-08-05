## Context

Evidence completeness previously collapsed a deliberately stopped run and an
incomplete artifact into `PARTIAL`. The domain already records the terminal
run state needed to preserve that distinction.

## Goals / Non-Goals

**Goals:**

- Represent an intentional stop without implying lost data.
- Preserve the existing evidence status API and export flow.

**Non-Goals:**

- Change what data a run captures before it is stopped.
- Treat failed runs as successful stops.

## Decisions

- Add `STOPPED` to manifest completeness rather than overloading `COMPLETE` or
  relabelling `PARTIAL`; consumers can accurately distinguish all outcomes.
- Map stopped exports to `READY` because the artifact is available, while the
  manifest retains the terminal outcome.

## Risks / Trade-offs

- Consumers must tolerate the additive completeness value. The manifest is
  already versioned and the UI is updated alongside the backend.
