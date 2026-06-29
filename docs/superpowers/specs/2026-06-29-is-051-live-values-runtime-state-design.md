# IS-051 — Live values + runtime state over SSE

Status: **Approved design** (2026-06-29). Implements task `IS-051 [BE] [observ]
Live values + runtime state over SSE` (SPEC: *Observe Live Data Values*).

## Goal

Surface, over the SSE transport built in IS-046, the two live signals the Web UI's
Data Source Detail (Values tab) and runtime status need:

1. **Live values** — the conflated/throttled stream of the values a running source
   is currently serving, on a new `GET /api/v1/data-sources/{id}/stream/values`.
2. **Runtime state** — the current per-source `RuntimeState`
   (`STOPPED/STARTING/RUNNING/ERROR/STALE`) as a snapshot on connect to the
   existing `GET /api/v1/projects/{pid}/stream/runtime`.

Per `ARCHITECTURE.md`: the live path is **conflated/throttled** for the UI and is
distinct from the full-fidelity recording path. Per `SPEC.md` *Observe Live Data
Values*: "view simulated data values in real time to debug mismatches."

## Scope boundary

- **In:** the values stream (tee → conflate → throttled flush → snapshot-on-connect)
  and the runtime-state snapshot on the runtime stream.
- **Out (sibling tasks):** health/error *detail* and why a source is ERROR/STALE
  (IS-053); project overview aggregation (IS-054); runtime-event *history* GET
  (IS-055); a worker→supervisor value RPC (not needed — see Value source).

## Key facts (verified against the code)

- **No live-value stream exists from a running worker.** The worker contract has
  `Capture` (one-shot real-source → supervisor, recording) and `ApplyValues`
  (supervisor → worker, replay). For the flows that exist today the **supervisor
  is the producer**: it pushes the served values via `RuntimeController.applyValues`
  (implemented by `Supervisor.applyValues`, the single chokepoint).
- `RuntimeController` (platform) already declares `applyValues(String, List<NeutralValue>)`
  and `state(String)`.
- `NeutralValue(nodeId, sourceTime, value, quality, qualityReason)` (protocol-model)
  is the neutral value type to reuse; `platform` depends on `protocol-model`.
- IS-046 transport (`api.stream`) extends cleanly: `StreamKey(Type, scopeId)`,
  `LiveEvent(seq, type, data, at)` with `NO_SEQ`, `LiveEventPublisher.publish`,
  `LiveStreamSubscriptions.subscribe`, `LiveStreamRegistry`, `LiveEventHub`.
- A `RuntimeController`-decorator tee is rejected: it would break the
  `runtimeController instanceof SourceScanner/SourceCapturer` checks in
  `RuntimeConfig`. The tee goes **inside the Supervisor** instead.

## Constraints

- No new dependencies; servlet `SseEmitter`; Jackson 3 (`tools.jackson.*`).
- The tee runs on a supervisor/replay thread → the listener must be cheap and
  non-blocking (update a concurrent store only; no I/O, no send).
- Reuse the IS-046 transport (heartbeat, reconnect, backpressure-disconnect) — do
  not reinvent it.

## Architecture / data flow

```
replay/scenario → Supervisor.applyValues(dsId, List<NeutralValue>)   [supervisor/replay thread]
                       │  tee (cheap, non-blocking) — after a successful apply
                       ▼
   LiveValueListener (platform; NONE by default)   ← new, beside Client/RuntimeActivityListener
                       ▼  (app wiring passes the hub)
   LiveValuesHub (api, @Component implements LiveValueListener)
        └─ LiveValueStore: Map<dsId, Map<nodeId, NeutralValue>> + per-source dirty set
                       ▼  scheduled flush (~250 ms, daemon)
   for each dirty source: LiveEventPublisher.publish(StreamKey.values(dsId), "values", changed)
                       ▼
   LiveStreamRegistry (IS-046)  →  GET /api/v1/data-sources/{id}/stream/values
        on subscribe: enqueue a "values-snapshot" (current latest-per-node map) first
```

Runtime state reuses the existing `…/stream/runtime` endpoint:
- on subscribe → enqueue a `runtime-state` snapshot: for each source in the project,
  `{dataSourceId, state}` from `RuntimeController.state(id)` over
  `DataSourceService.list(projectId)`.
- state *changes* already flow as `RuntimeActivityEvent` (SOURCE_START/STOP/ERROR/…)
  from IS-046; the UI derives current state from snapshot + subsequent events.

## Components

**platform — `com.ainclusive.iotsim.platform.runtime`**
- `LiveValueListener` — `void onValues(String dataSourceId, List<NeutralValue> values, Instant at)`,
  with `LiveValueListener NONE = (id, v, at) -> {}`. Doc: called on the IPC/replay
  thread, must be cheap/non-blocking.

**runtime-supervisor — `Supervisor`**
- New `final LiveValueListener valueListener` field (defaults `NONE`), set via a new
  6-arg constructor `(WorkerLauncher, RestartPolicy, HealthPolicy,
  ClientActivityListener, RuntimeActivityListener, LiveValueListener)`; the existing
  5-arg constructor delegates with `LiveValueListener.NONE`.
- In `applyValues(dataSourceId, values)`: after the successful apply, call
  `valueListener.onValues(dataSourceId, values, Instant.now())` — `Instant.now()` is
  the served-at time for the SSE event; each value's own `sourceTime` stays in the
  payload. The call is guarded so a listener error never breaks `applyValues`.

**api — `com.ainclusive.iotsim.api.stream`**
- `StreamKey.Type.VALUES` + factory `StreamKey values(String dataSourceId)`.
- `LiveValueStore` — thread-safe latest-per-node store:
  `void record(String dataSourceId, List<NeutralValue> values)`,
  `List<NeutralValue> snapshot(String dataSourceId)`,
  `List<NeutralValue> drainChanged(String dataSourceId)`,
  `Set<String> dirtySources()`. Latest wins per `nodeId`; `record` marks the source
  and changed nodes dirty.
- `LiveValuesHub` (`@Component implements LiveValueListener`) — `onValues` delegates
  to `store.record(...)`; owns a single-thread daemon `ScheduledExecutorService`
  that every ~250 ms drains each dirty source and
  `publisher.publish(StreamKey.values(dsId), "values", changed, at)`. Package-visible
  `flushTick()` for deterministic tests; `AutoCloseable` to stop the scheduler.
- `ValuesStreamController` — `GET /api/v1/data-sources/{id}/stream/values`,
  `produces=text/event-stream`; builds the `values-snapshot` initial event from
  `store.snapshot(id)` and calls the registry subscribe-with-initial seam.
- `LiveStreamRegistry` / `LiveStream` seam: add
  `SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial)`
  (the existing 2-arg delegates with `List.of()`). `LiveStream.addSubscriber(sub,
  lastEventId, initial)` enqueues `initial` to the new subscriber **before** the
  Last-Event-ID backlog/live registration, inside the same lock (so ordering holds).
  See "Reconnect / snapshot rule" for how each controller uses it.
- Runtime-state snapshot: a small helper (e.g. `RuntimeStateSnapshot`) injected into
  `RuntimeStreamController`, using `RuntimeController` (platform) + `DataSourceService`
  (domain) to build `[{dataSourceId, state}]`; passed as the initial event to
  `subscribe`.

**app — `RuntimeConfig`**
- Pass the `LiveValuesHub` bean as the new `valueListener` argument when building the
  `Supervisor` (supervisor-mode branch only; in-memory mode does not serve real
  values).

## Reconnect / snapshot rule (per stream type)

- **VALUES:** always send the `values-snapshot` on connect and **ignore**
  `Last-Event-ID` (the controller passes `lastEventId=null`). The conflated snapshot
  is the authoritative current state and supersedes any buffered deltas, so deltas
  are never replayed (avoids snapshot-then-stale-delta ordering). Live `values`
  deltas follow.
- **RUNTIME:** unchanged IS-046 behavior plus a snapshot on a **fresh** connect. No
  `Last-Event-ID` → enqueue the `runtime-state` snapshot, then live. With
  `Last-Event-ID` → no snapshot; the normal IS-046 backlog replay applies (the client
  already holds a baseline and only needs missed events).

## Endpoint contract

| Endpoint | Event `type` | `data` |
|---|---|---|
| `…/data-sources/{id}/stream/values` | `values-snapshot` | all current `[{nodeId, value, quality, qualityReason, sourceTime}]` |
| `…/data-sources/{id}/stream/values` | `values` | changed-since-last-flush, same shape |
| `…/projects/{pid}/stream/runtime` | `runtime-state` (snapshot on connect) | `[{dataSourceId, state}]` |
| (both, inherited) | `heartbeat` / `resync` | from IS-046 |

`value` is serialized as-is from `NeutralValue.value` (already decoded per the node's
`DataType`). Unknown/not-running source → empty values stream (no 404). Auth inherits
`SecurityConfig` (local permitAll / shared authenticated).

## Defaults (tunable)

- Flush interval: **250 ms**.
- Per-source latest-value map: unbounded by node count (a schema's node set is
  bounded); no separate cap needed.
- Reuses IS-046 defaults (ring buffer 256, per-subscriber queue 256, heartbeat 15 s,
  `SseEmitter` timeout 0).

## Threading

- Tee (`onValues`) runs on the supervisor/replay thread → only a concurrent-map
  update + dirty-mark; never blocks.
- Flush runs on the hub's scheduled executor; publish enqueues to subscriber queues;
  the actual SSE send stays on the IS-046 sender pool (off the flush thread).
- `LiveValueStore` uses concurrent structures; `drainChanged` atomically snapshots
  and clears the per-source changed set.

## Testing

- `LiveValueStore` (unit): latest-wins per node; `record` marks dirty;
  `drainChanged` returns only changed and clears; `snapshot` returns full current.
- `LiveValuesHub` (unit): `onValues` → store; `flushTick()` publishes one `values`
  event per dirty source with the changed values (fake `LiveEventPublisher`); no
  publish when nothing dirty.
- `Supervisor` tee (unit): `applyValues` invokes the `LiveValueListener` with the
  applied values; a throwing listener does not break `applyValues`.
- Registry initial-snapshot seam (unit): a subscriber receives `initial` before live
  events; the 2-arg overload still behaves as before.
- `ValuesStreamController` (unit): delegates to `subscribe(values(id), …, snapshot)`
  with the snapshot from the store.
- Runtime-state snapshot (unit): builds `[{dataSourceId, state}]` from a fake
  `RuntimeController` + `DataSourceService`.
- e2e (app, MockMvc async): subscribe to `…/stream/values`; apply values via the
  runtime path; assert a `values-snapshot` then a `values` delta arrive as framed
  SSE with JSON payloads.

## Out of scope (explicit)

Capture-in-progress live view (recording screen); worker-side value streaming RPC;
health/error detail (IS-053); project overview aggregation (IS-054); runtime-event
history GET (IS-055); synthetic generation (IS-062).
