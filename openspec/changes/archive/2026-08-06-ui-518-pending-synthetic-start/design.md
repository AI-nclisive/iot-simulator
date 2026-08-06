## Context

The asynchronous request does not update the row until it completes.

## Goals / Non-Goals

**Goals:** prevent duplicate starts and give immediate feedback.

**Non-Goals:** change runtime lifecycle semantics.

## Decisions

Track pending source ids in the frontend store and clear them in `finally`.

## Risks / Trade-offs

- [Request fails] → the `finally` cleanup restores the Run control.
