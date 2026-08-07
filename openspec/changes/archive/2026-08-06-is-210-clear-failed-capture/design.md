## Context

Capture is a server-streaming worker RPC. Its asynchronous error callback was
previously ignored by `Supervisor`, while `RecordingService` tracks liveness in
an in-memory active-capture map.

## Decisions

- Extend the protocol-neutral `SourceCapturer` port with an optional failure
  callback, preserving the existing two-argument method for implementations
  that cannot observe asynchronous stream failures.
- Have `Supervisor` close the stream, client, and launched worker before it
  invokes the callback. This prevents the domain from publishing a stopped
  state while resources are still alive.
- Remove only the active-map entry that owns the failed recording, then
  finalize its persisted counts. A concurrent later retry therefore cannot be
  removed by a stale callback.

## Risks / Trade-offs

- A callback can race a user-initiated Stop. The session and active-map removal
  are idempotent, so exactly one path finalizes the recording.
- Capturers without failure observation retain their existing behaviour; the
  production supervisor implements the callback.
