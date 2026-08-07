## Context

The live SSE value stream is intentionally conflated by node id, while a
recording stores every observation in `value_timeline`. Persisted count and
size were only finalized when capture stopped.

## Decisions

- While a recording is active, derive its metadata count and size from the
  timeline; after completion, retain the stored finalized statistics.
- Poll recording metadata from the recording page every two seconds while
  capture is active. SSE continues to provide live value and connection
  observation, but is not used as a full-fidelity count.

## Risks / Trade-offs

- Polling adds a small read load only during active capture.
- Timeline aggregation during active capture trades a database aggregate for
  truthful operator-visible progress.
