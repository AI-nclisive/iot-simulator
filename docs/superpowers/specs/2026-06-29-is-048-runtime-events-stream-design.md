# IS-048 — RuntimeEvents stream (worker → supervisor)

**Task:** IS-048 [BE] · owning spec `backend-specs/02_WORKER_CONTRACT_AND_IPC.md` · Wave C (observability & evidence, P0)
**Date:** 2026-06-29

## Summary

Implement the `RuntimeEvents` worker→supervisor gRPC stream and persist its events
to `runtime_events`. The proto already declares the `RuntimeEvents` RPC and the
`RuntimeEvent` message; this task is the implementation across worker, supervisor,
platform, and app, mirroring the IS-047 `ClientEvents` vertical. It activates the
IS-049 `RuntimeEventRepository` (currently dead code — nothing writes to it).

Scope decisions (confirmed with the user):

- **Persist now.** Wire the supervisor listener → `RuntimeEventRepository` in
  `app/config` so runtime events land durably end-to-end. IS-055 later adds the
  read API/SSE on top.
- **Worker emits `SOURCE_START`, `SOURCE_STOP`, `ERROR`** — the events the worker
  can genuinely observe today.

## Out of scope (deferred)

- `FAULT_STATE_CHANGE` — depends on faults (IS-087 / IS-088), not built.
- Replay / scenario runtime events — not built.
- Supervisor-origin events (worker crash, restart-with-backoff IS-040, health-stale
  IS-041).
- Runtime-event read API / SSE surface — IS-055.

## Existing pattern being mirrored (IS-047 ClientEvents)

- **Proto** (`worker-contract/.../protocol_data_source.proto`): already has
  `rpc RuntimeEvents (StreamRequest) returns (stream RuntimeEvent);` and
  `message RuntimeEvent { string type = 1; int64 at_micros = 2; string detail = 3; }`.
  The `type` field is deliberately a free string (open-ended, comment lists example
  types) so new event types land without proto changes.
- **Worker** (`worker-opcua`): `ClientEventHub` (CopyOnWriteArrayList of subscribers,
  per-subscriber lock, fan-out, **no buffer** — point-in-time, dropped if no stream
  open); `OpcUaProtocolService.clientEvents` registers the observer; events sourced
  from Milo session callbacks via a `clientEventSink` injected at Configure.
- **Supervisor**: `WorkerClient.clientEvents(onEvent, onError)` returns a
  `StreamHandle`; `Supervisor` maps proto `ClientEvent` → domain `ClientActivityEvent`
  (tagged with `dataSourceId`) and forwards to a `ClientActivityListener`.
- **Platform**: `ClientActivityEvent` record + `ClientActivityListener` functional
  interface with a `NONE` default. The listener is called on the IPC delivery thread
  (must be cheap / non-blocking).
- **Persistence** (IS-049): `RuntimeEventRepository.append(projectId, dataSourceId,
  runId, type, at, payloadJson)` → `runtime_events` (columns: `id`, `project_id`
  NOT NULL, `data_source_id` nullable, `run_id` nullable, `type` NOT NULL, `at`
  TIMESTAMPTZ default now(), `payload` JSONB NOT NULL default `{}`).

Key facts that shape the design:

- `runtime-supervisor` depends only on `worker-contract` + `platform` (NOT
  `persistence`), so persistence wiring lives in `app/config`.
- `RuntimeStartSpec` carries **no projectId**, so the supervisor tags events with
  `dataSourceId` only; the app layer resolves `projectId`.
- In production today, `ClientEvents` go to `ClientActivityListener.NONE` (discarded);
  only tests observe them.

## Components

### 1. `worker-opcua` — produce events

- `RuntimeEventHub` — copy of `ClientEventHub` (subscribers list, per-subscriber lock,
  `emit`, `openStreamCount`, no buffering).
- `OpcUaProtocolService.runtimeEvents(StreamRequest, observer)` — registers the
  observer with `runtimeEventHub` (mirror of `clientEvents`).
- `runtimeEventSink` injected at Configure (alongside `clientEventSink`).
- `OpcUaServerRuntime` emits:
  - `SOURCE_START` — after `server.startup()` completes (server actually listening).
  - `SOURCE_STOP` — synchronously at the start of graceful shutdown, before the
    server is torn down.
  - `ERROR` — on a runtime failure while serving. Target hook: failures in the
    value-application (ApplyValues) path; `detail` = exception message. Exact hook
    pinned down during planning.

### 2. `runtime-supervisor` — consume + tag

- `WorkerClient.runtimeEvents(onEvent, onError)` → `StreamHandle` (copy of
  `clientEvents`).
- `Supervisor` gains a `runtimeActivityListener` (constructor-injected, defaults to
  `RuntimeActivityListener.NONE`); maps proto `RuntimeEvent` → `RuntimeActivityEvent`
  tagged with `dataSourceId`, forwards to the listener.
- Stream lifecycle: see "SOURCE_START capture" below. Generalize `cancelClientEvents`
  to also cancel the runtime stream on stop / exit / restart.

### 3. `platform` — domain boundary

- `RuntimeActivityEvent` record: `dataSourceId` (String), `type` (**free String**,
  not an enum — honors the proto's open `type` so faults/replay/scenario types land
  later without supervisor changes), `at` (Instant), `detail` (String, nullable).
- `RuntimeActivityListener` functional interface + `NONE` default (copy of
  `ClientActivityListener`, same threading contract).

### 4. `app/config` — persist

- A `RuntimeActivityListener` bean (supervisor mode only) that:
  - resolves `projectId` via
    `DataSourceRepository.findById(dataSourceId).map(DataSourceRow::projectId)`,
  - calls `RuntimeEventRepository.append(projectId, dataSourceId, null /*runId*/,
    type, at, payload)`, where `payload` = `{"detail":"…"}` when detail present, else
    `{}`.
  - Because the listener runs on the IPC delivery thread, it **hands off to a bounded
    single-thread executor** that performs the lookup + insert (honors the
    cheap/non-blocking contract).
  - Wired into the `Supervisor` bean in `RuntimeConfig` (new constructor overload that
    accepts the runtime listener; existing overloads keep working).

## The one deviation — capturing `SOURCE_START`

`Supervisor.ManagedWorker.connect()` calls `newClient.start()` (the worker's OPC UA
server starts → `SOURCE_START` fires) **before** `install()` subscribes to streams.
Because the hub doesn't buffer, a naive mirror would drop `SOURCE_START`.

**Fix:** open the RuntimeEvents stream inside `connect()` **after `configure()`,
before `start()`**; carry the `StreamHandle` on the `Connection`; `install()` adopts
it. `ClientEvents` stays opened in `install()` (a client can only connect after the
worker is RUNNING). On the failure path in `connect()`, cancel the runtime stream
alongside the existing cleanup.

## Data flow

```
Milo / server lifecycle
  → runtimeEventSink
  → RuntimeEventHub.emit
  → gRPC RuntimeEvents stream
  → WorkerClient.runtimeEvents
  → Supervisor (map + tag dataSourceId)
  → RuntimeActivityListener
  → executor: resolve projectId via DataSourceRepository
  → RuntimeEventRepository.append
  → runtime_events
```

## Error handling / semantics

- Stream end (CANCELLED on stop, UNIMPLEMENTED on an older worker) is non-fatal — the
  worker keeps running (same as IS-047).
- `SOURCE_START` and `ERROR`: reliably delivered (stream open while serving).
- `SOURCE_STOP`: emitted synchronously before shutdown; the supervisor cancels the
  stream only after the Stop RPC returns — **ordered but best-effort** on teardown
  (documented limitation, matches the existing teardown philosophy).
- `projectId` unresolvable (source row gone) → skip + log; never throw on the IPC
  thread.

## Testing (mirror IS-047's three layers)

- **Worker IT** (`OpcUaRuntimeEventsIT`): real gRPC worker server; subscribe to the
  stream; assert `SOURCE_START` on start, `SOURCE_STOP` on stop, `ERROR` on a forced
  value-apply failure (blocking-queue + poll, as `OpcUaClientEventsIT` does).
- **Supervisor test** (`SupervisorRuntimeEventsTest`): events forwarded tagged with
  `dataSourceId`; stop cancels the stream; default `NONE` listener stays silent.
- **App listener test**: resolves `projectId` from `dataSourceId` and appends a row
  with the right `type`, `payload`, and null `runId`. (The repo itself is already
  covered by `RuntimeEventRepositoryIT`.)
- `./gradlew build` green (Definition of Done).

## Definition of Done

- All four module changes implemented and tested.
- `./gradlew build` green (includes Spotless, Checkstyle, ArchUnit boundaries,
  Testcontainers ITs).
- IS-048 catalog checkbox flipped in `backend-specs/TASKS.md` in the same PR
  (catalog-sync CI gate).
- PR opened with `Implements: IS-048`, board → In review, squash auto-merge armed.
