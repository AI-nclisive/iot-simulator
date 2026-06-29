# IS-046 — SSE infrastructure (live endpoints)

Status: **Approved design** (2026-06-29). Implements task `IS-046 [BE] [api] SSE
infrastructure (live endpoints)`.

## Goal

Provide the reusable **Server-Sent Events transport layer** that the Wave C
observability features build on, and wire the two reference endpoints whose event
sources already exist (IS-047 client events, IS-048 runtime events):

- `GET /api/v1/projects/{projectId}/stream/runtime`
- `GET /api/v1/data-sources/{id}/stream/clients`

The rich semantics — live-value conflation (IS-051), connected-client snapshot
(IS-052), health/stale aggregation (IS-053/054), runtime-event history GET
(IS-055) — are **out of scope**; they layer onto this transport later. This task
delivers transport + two passthrough endpoints.

Per `backend-specs/05_API_CONTRACT.md` §"Live updates (SSE)" and decisions **D6**
(SSE-only at start) / **D7** (`/api/v1`).

## Constraints

- Stack is **Spring Web MVC (servlet)**, not WebFlux; Jackson 3.x
  (`tools.jackson.*`). `SseEmitter` ships with `spring-web` — **no new
  dependency** (AGENTS.md forbids deps without approval).
- Events arrive on the **IPC/gRPC delivery thread**, which must stay
  non-blocking (same rule the IS-048 persister follows).
- One-way streams (D6); each event carries `type` + payload; clients reconnect
  with `Last-Event-ID`.

## Approach

Servlet `SseEmitter` + an in-process fan-out hub. Rejected alternatives:
`ApplicationEventPublisher`/`@EventListener` (hides threading, harder to test in
isolation) and Reactor `Flux` (paradigm mismatch + new dependency).

## Architecture

```
worker → (gRPC IS-047/048) → Supervisor
                                  │  RuntimeActivityEvent / ClientActivityEvent
                                  ▼  (IPC thread — must not block)
   composite listeners (app/RuntimeConfig):
     runtime → [ PersistingRuntimeActivityListener, LiveEventHub ]
     clients → [ LiveEventHub ]   (currently NONE — now wired)
                                  ▼  (hub dispatch executor — off the IPC thread)
                            LiveEventHub
                  ├─ resolve dataSourceId → projectId (port + cache)
                  └─ registry.publish(StreamKey, type, data)
                                  ▼
                       LiveStreamRegistry  (keyed by StreamKey)
                  per StreamKey:
                    - ring buffer (last N, seq = SSE id)
                    - set<Subscriber>: SseEmitter + bounded queue
                      + per-subscriber sender thread; overflow → complete()
                                  ▼  text/event-stream
   RuntimeStreamController  GET /api/v1/projects/{pid}/stream/runtime   key = RUNTIME:pid
   ClientStreamController   GET /api/v1/data-sources/{id}/stream/clients key = CLIENTS:dsId
   + scheduled heartbeat (~15s) to all subscribers
```

### Components — `com.ainclusive.iotsim.api.stream`

- **`StreamKey(StreamType type, String scopeId)`** — `RUNTIME` (scope =
  projectId) / `CLIENTS` (scope = dataSourceId). Value type; equals/hashCode for
  map keying.
- **`LiveEvent(long seq, String type, Object data, Instant at)`** — buffered and
  serialized to SSE: `id` = `seq`, `event` = `type`, `data` = JSON of `data`.
- **`LiveStream`** — one per `StreamKey`. Holds the ring buffer + subscriber set.
  `publish(type, data)` assigns the next `seq`, appends to the buffer, fans to
  subscribers. `subscribe(lastEventId)` registers a subscriber and replays per
  §Reconnect.
- **`Subscriber`** — wraps one `SseEmitter` + a bounded queue + a serialized
  drain. On queue overflow → `emitter.complete()` and removal (disconnect the
  slow client).
- **`LiveStreamRegistry`** (`@Component`) — map `StreamKey → LiveStream`; owns the
  sender executor and the heartbeat scheduler. API:
  `SseEmitter subscribe(StreamKey, String lastEventId)`,
  `void publish(StreamKey, String type, Object data)`. Empty streams are pruned
  when their last subscriber leaves.
- **`LiveEventHub`** (`@Component`) — implements `ClientActivityListener` and
  `RuntimeActivityListener` (from `platform.runtime`). On each event it submits to
  its own dispatch executor a task that (for runtime) resolves the project and
  then calls `registry.publish(...)`. **Never runs on the IPC thread.**
- **`DataSourceProjectResolver`** — port `Optional<String> projectOf(String
  dataSourceId)`; default impl over `DataSourceService` (domain) with a
  `dataSourceId → projectId` cache.

### Components — `com.ainclusive.iotsim.platform.runtime`

- **`CompositeRuntimeActivityListener`** — fans one event to N delegates (kept
  next to the interface, reusable). `RuntimeConfig` composes
  `[persister, hub]` for the runtime listener.

### Controllers — `com.ainclusive.iotsim.api.stream`

Thin; map path + `Last-Event-ID` header onto `registry.subscribe(...)`,
`produces = text/event-stream`:

- **`RuntimeStreamController`** — `GET /api/v1/projects/{projectId}/stream/runtime`
  → `subscribe(StreamKey(RUNTIME, projectId), lastEventId)`.
- **`ClientStreamController`** — `GET /api/v1/data-sources/{id}/stream/clients` →
  `subscribe(StreamKey(CLIENTS, id), lastEventId)`.

### Wiring — `app/config/RuntimeConfig`

`runtimeController` bean composes the runtime listener as
`CompositeRuntimeActivityListener(persister, hub)` and passes `hub` as the
`ClientActivityListener` (today `NONE`) via the existing 4-arg `Supervisor`
constructor. `LiveEventHub`, `LiveStreamRegistry`, `DataSourceProjectResolver`
are `@Component`/beans from the `api` module (already component-scanned under
`com.ainclusive.iotsim`).

## Threading & backpressure

- **IPC thread stays free:** the listener only enqueues to the hub's dispatch
  executor (non-blocking), mirroring the IS-048 persister.
- **Project resolution** (runtime only — endpoint is per-project but the event
  carries `dataSourceId`) runs on the dispatch thread with a cache. Clients stream
  needs no resolution (key = `dataSourceId`).
- **Sending** (`SseEmitter.send()` may block on a slow socket) runs on the sender
  executor, **serialized per subscriber**, so one slow client never stalls others.
- **Backpressure:** per-subscriber bounded queue; overflow → `complete()` +
  removal. The client reconnects with `Last-Event-ID` and replays what it missed.
- **Heartbeat (~15s):** an SSE comment / `event: heartbeat` to all subscribers,
  to detect dead connections (`send` throws → remove) and defeat proxy idle
  timeouts. `SseEmitter` timeout is large / `0L` (no server-side timeout).

## Reconnect / Last-Event-ID

- Every event in a stream gets a monotonic `seq` (long) emitted as the SSE `id`.
- On `subscribe`:
  - no `Last-Event-ID` → live from now (a current-state snapshot is IS-051/052
    semantics; the transport does not synthesize one);
  - `Last-Event-ID` present and the buffer holds `seq > lastId` → replay the tail;
  - `lastId` older than the buffer's oldest entry (evicted) **or** `seq` reset by a
    restart → emit `event: resync` (the client refetches history via REST — IS-055
    — and continues live).
- The ring buffer is **in-memory only**; on restart `seq` resets to 0, so any
  unknown / too-new id ⇒ `resync`. For the clients stream (events are not
  persisted), the ring buffer is the only replay source.

## Defaults (tunable)

- Ring buffer: **256** events per stream key.
- Per-subscriber queue capacity: **256** events (overflow → disconnect).
- Heartbeat interval: **15 s**.
- `SseEmitter` timeout: **0** (no server-side timeout; heartbeat + client
  disconnect drive cleanup).

## Endpoint contract

| Endpoint | Event `type` | `data` payload |
|---|---|---|
| `…/projects/{pid}/stream/runtime` | `RuntimeActivityEvent.type` (e.g. SOURCE_START, SOURCE_STOP, ERROR) | `{ dataSourceId, type, at, detail }` |
| `…/data-sources/{id}/stream/clients` | kind (CONNECTED / DISCONNECTED / SUBSCRIPTION) | `{ dataSourceId, clientId, kind, at }` |
| (both) | `heartbeat` | empty / timestamp |
| (both) | `resync` | hint to refetch history then resume |

- **Auth:** inherited from `SecurityConfig` — `local` mode permitAll (default),
  `shared` mode authenticated; SSE paths under `/api/v1` are covered without
  change. Role-aware authorization (user/admin) is **not** added here (IS-077);
  both roles observe.
- **Unknown project/data-source:** the stream is simply empty (no 404, no
  per-connect lookup); a source may appear later. (Revisit if validation is
  wanted.)

## Testing

- `LiveStreamRegistry` / `LiveStream` (unit): publish → delivery; replay by
  `Last-Event-ID`; gap → `resync`; slow-subscriber overflow → disconnect;
  heartbeat fan-out.
- `LiveEventHub` (unit): routing runtime → project (via a mock resolver) and
  clients → dataSourceId; work happens off the IPC thread.
- `CompositeRuntimeActivityListener` (unit): every delegate invoked.
- Controllers (MockMvc async): `content-type: text/event-stream`, an event is
  delivered, `Last-Event-ID` is propagated.

## Out of scope (sibling tasks)

Live-value streaming + runtime-state aggregation (IS-051), connected-client
snapshot/history (IS-052), health/stale + project overview (IS-053/054),
runtime-event history GET (IS-055), runs stream (needs the Runs resource,
IS-089), live-values endpoint (no conflated value source yet).
