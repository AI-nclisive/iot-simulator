## Context

See proposal.md and the worker-contract delta. The existing client-mode capture
session only forwards monitored-item notifications; an OPC UA server is allowed
not to send a meaningful change while a value remains static.

## Goals / Non-Goals

**Goals:**

- Deliver a bounded initial snapshot promptly for a large scanned schema.
- Preserve the existing monitored-item path for ongoing capture.
- Isolate failures to the affected node or read batch.

**Non-Goals:**

- Rework recording storage, protocol messages, or subscription sampling.
- Guarantee a globally atomic snapshot across all nodes.

## Decisions

- Read values in bounded multi-node OPC UA requests after the subscription has
  been established. Batching avoids one round trip per variable for a scan with
  hundreds of nodes, while creating the subscription first minimizes the gap in
  which a change could be missed. Duplicate values are acceptable recording
  observations; subscribers still receive future changes.
- Convert and publish each successful read independently. A malformed native
  value or one unavailable node is skipped so it cannot abort capture of the
  remainder. This follows the existing best-effort per-node monitoring policy.
- Cover both initial static values and later changes in the embedded-server
  integration test. A unit-only test would not demonstrate the client read and
  subscription lifecycle together.

## Risks / Trade-offs

- [A value changes while the initial reads are in progress] → a duplicate or
  close-timestamped observation can be stored; the subscription retains the
  authoritative stream of later changes.
- [A remote server limits the number of nodes per Read request] → bounded read
  batches keep individual requests below common server limits.
- [A read batch fails] → continue with the remaining batches and live
  subscription rather than fail the whole recording.

## Migration Plan

The change is backward-compatible and has no persisted state. Deploy the
worker update normally; starting a new recording immediately uses the snapshot
path. Rollback restores notification-only capture.
