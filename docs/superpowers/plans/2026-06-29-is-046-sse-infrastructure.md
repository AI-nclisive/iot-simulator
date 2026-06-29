# IS-046 SSE Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Server-Sent Events transport layer for live updates and wire the two reference endpoints whose event sources already exist — `GET /api/v1/projects/{projectId}/stream/runtime` and `GET /api/v1/data-sources/{id}/stream/clients`.

**Architecture:** Servlet `SseEmitter` + an in-process fan-out hub. Supervisor activity events (`RuntimeActivityEvent`/`ClientActivityEvent`, delivered on the IPC thread) reach a `LiveEventHub` that resolves the routing key and publishes into a `LiveStreamRegistry`. The registry keeps one `LiveStream` per `StreamKey`, each holding a bounded ring buffer (for `Last-Event-ID` replay) and a set of `Subscriber`s; each subscriber owns a bounded queue drained serially onto a shared sender pool, and is disconnected on overflow. Thin controllers map path + `Last-Event-ID` onto `registry.subscribe(...)`.

**Tech Stack:** Java 25, Spring Boot 4.1.0 (Web MVC / servlet — NOT WebFlux), `SseEmitter` from `spring-web`, Jackson 3.x (`tools.jackson.databind.ObjectMapper`), JUnit 5 + AssertJ (every module), MockMvc + Mockito only in the `app` module.

## Global Constraints

- **No new dependencies** — `SseEmitter` ships with `spring-web` already on the `api` classpath (AGENTS.md forbids deps without approval).
- **Jackson 3:** import `tools.jackson.databind.ObjectMapper`; serialization throws unchecked `tools.jackson.core.JacksonException`.
- **IPC thread must never block:** `LiveEventHub` listener methods only hand off to an executor; all resolution/sending happens off the IPC thread.
- **Backpressure = disconnect slow clients:** per-subscriber bounded queue; on overflow `complete()` the emitter and drop the subscriber (it reconnects with `Last-Event-ID`).
- **Replay = in-memory ring buffer:** per-stream bounded buffer; on a gap or counter reset emit a `resync` event.
- **Module placement:** transport lives in `api` package `com.ainclusive.iotsim.api.stream`; the composite listener in `platform` package `com.ainclusive.iotsim.platform.runtime`; resolver impl + wiring in `app`.
- **Defaults:** ring buffer 256, per-subscriber queue 256, heartbeat 15 s, `SseEmitter` timeout 0 (no server timeout).
- **API base path:** `/api/v1` (decision D7). SSE produces `text/event-stream`.
- Run `./gradlew build` green before declaring done; add/update tests for every change.
- Commit messages end with: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: StreamKey + LiveEvent value types

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/StreamKey.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEvent.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/StreamKeyTest.java`

**Interfaces:**
- Produces:
  - `record StreamKey(StreamKey.Type type, String scopeId)` with nested `enum Type { RUNTIME, CLIENTS }` and static factories `StreamKey runtime(String projectId)`, `StreamKey clients(String dataSourceId)`.
  - `record LiveEvent(long seq, String type, Object data, java.time.Instant at)` with constant `long NO_SEQ = -1` and helper `boolean hasSeq()` returning `seq >= 0`.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StreamKeyTest {

    @Test
    void factoriesBuildTypedKeys() {
        assertThat(StreamKey.runtime("p1"))
                .isEqualTo(new StreamKey(StreamKey.Type.RUNTIME, "p1"));
        assertThat(StreamKey.clients("d9"))
                .isEqualTo(new StreamKey(StreamKey.Type.CLIENTS, "d9"));
    }

    @Test
    void keysWithSameTypeAndScopeAreEqualForMapUse() {
        assertThat(StreamKey.runtime("p1")).hasSameHashCodeAs(StreamKey.runtime("p1"));
        assertThat(StreamKey.runtime("p1")).isNotEqualTo(StreamKey.clients("p1"));
    }

    @Test
    void liveEventTracksWhetherItCarriesASeq() {
        assertThat(new LiveEvent(7L, "X", null, Instant.EPOCH).hasSeq()).isTrue();
        assertThat(new LiveEvent(LiveEvent.NO_SEQ, "heartbeat", null, Instant.EPOCH).hasSeq())
                .isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamKeyTest"`
Expected: FAIL — `StreamKey`/`LiveEvent` do not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`StreamKey.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.util.Objects;

/** Routing key for a live stream: a stream type scoped to one resource id. */
public record StreamKey(Type type, String scopeId) {

    /** Stream families exposed over SSE; each maps to one endpoint. */
    public enum Type {
        RUNTIME,
        CLIENTS
    }

    public StreamKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scopeId, "scopeId");
    }

    /** Runtime-context stream for a project: {@code /projects/{projectId}/stream/runtime}. */
    public static StreamKey runtime(String projectId) {
        return new StreamKey(Type.RUNTIME, projectId);
    }

    /** Client-activity stream for a data source: {@code /data-sources/{id}/stream/clients}. */
    public static StreamKey clients(String dataSourceId) {
        return new StreamKey(Type.CLIENTS, dataSourceId);
    }
}
```

`LiveEvent.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.Objects;

/**
 * One thing to send over a stream. {@code seq} is the per-stream monotonic id sent
 * as the SSE {@code id:} line; transport events that must not advance the client's
 * {@code Last-Event-ID} (heartbeat, resync) use {@link #NO_SEQ}. {@code data} is
 * serialized to the SSE {@code data:} line as JSON.
 */
public record LiveEvent(long seq, String type, Object data, Instant at) {

    /** Sentinel {@code seq} for events that carry no SSE id (heartbeat, resync). */
    public static final long NO_SEQ = -1L;

    public LiveEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
    }

    public boolean hasSeq() {
        return seq >= 0;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamKeyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/StreamKey.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEvent.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/StreamKeyTest.java
git commit -m "feat(api): IS-046 StreamKey + LiveEvent value types

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: EventSink + SseEmitterSink

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/EventSink.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/SseEmitterSink.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/SseEmitterSinkTest.java`

**Interfaces:**
- Consumes: `LiveEvent` (Task 1).
- Produces:
  - `interface EventSink { void send(LiveEvent event) throws java.io.IOException; void complete(); }`
  - `final class SseEmitterSink implements EventSink` with constructor `SseEmitterSink(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, tools.jackson.databind.ObjectMapper json)`. Serializes `event.data()` to JSON, writes the SSE `id:` line only when `event.hasSeq()`, sets the SSE event name to `event.type()`.

**Note on testing the sink:** `SseEmitter.send(SseEventBuilder)` performs the SSE framing internally and writes to the servlet response, which a plain unit test has no access to. The end-to-end framing (`id:`/`event:`/`data:` bytes) is asserted in Task 11 via MockMvc. This task's test only pins the contract that the sink (a) serializes `data` through the injected `ObjectMapper` and (b) forwards `complete()`, using a tiny `SseEmitter` subclass that captures sent objects.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class SseEmitterSinkTest {

    /** Captures objects handed to send(...) without a servlet response. */
    static final class CapturingEmitter extends SseEmitter {
        final List<Object> sent = new CopyOnWriteArrayList<>();
        volatile boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            // Materialize the builder's data items so the test can inspect payloads.
            builder.build().forEach(item -> sent.add(item.getData()));
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    @Test
    void serializesDataAsJsonAndForwardsComplete() {
        CapturingEmitter emitter = new CapturingEmitter();
        SseEmitterSink sink = new SseEmitterSink(emitter, new ObjectMapper());

        sink.send(new LiveEvent(3L, "SOURCE_START", Map.of("dataSourceId", "d1"), Instant.EPOCH));
        sink.complete();

        assertThat(emitter.sent).anySatisfy(d ->
                assertThat(d.toString()).contains("\"dataSourceId\":\"d1\""));
        assertThat(emitter.completed).isTrue();
    }
}
```

Note: `SseEmitterSink.send` declares `throws IOException`; the test body may need `throws Exception` on the method or a try/catch — add `throws Exception` to `serializesDataAsJsonAndForwardsComplete`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.SseEmitterSinkTest"`
Expected: FAIL — `EventSink`/`SseEmitterSink` do not exist.

- [ ] **Step 3: Write minimal implementation**

`EventSink.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.io.IOException;

/**
 * Where a {@link Subscriber} writes events. Production is {@link SseEmitterSink}
 * over a servlet {@code SseEmitter}; tests use a recording fake so stream logic is
 * verifiable without a servlet.
 */
public interface EventSink {

    /** Writes one event; throws if the underlying connection is gone. */
    void send(LiveEvent event) throws IOException;

    /** Ends the stream (graceful close or backpressure disconnect). */
    void complete();
}
```

`SseEmitterSink.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** {@link EventSink} backed by a servlet {@code SseEmitter}, with JSON data lines. */
public final class SseEmitterSink implements EventSink {

    private final SseEmitter emitter;
    private final ObjectMapper json;

    public SseEmitterSink(SseEmitter emitter, ObjectMapper json) {
        this.emitter = emitter;
        this.json = json;
    }

    @Override
    public void send(LiveEvent event) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(event.type())
                .data(json.writeValueAsString(event.data()), MediaType.APPLICATION_JSON);
        if (event.hasSeq()) {
            builder.id(Long.toString(event.seq()));
        }
        emitter.send(builder);
    }

    @Override
    public void complete() {
        emitter.complete();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.SseEmitterSinkTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/EventSink.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/SseEmitterSink.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/SseEmitterSinkTest.java
git commit -m "feat(api): IS-046 EventSink + SseEmitterSink (JSON SSE writer)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Subscriber (bounded queue, serial drain, overflow disconnect)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/Subscriber.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/SubscriberTest.java`
- Test util: `api/src/test/java/com/ainclusive/iotsim/api/stream/RecordingSink.java`

**Interfaces:**
- Consumes: `EventSink` (Task 2), `LiveEvent` (Task 1).
- Produces: `final class Subscriber` with constructor `Subscriber(EventSink sink, int queueCapacity, java.util.concurrent.Executor sender)`; methods `void enqueue(LiveEvent event)` (offer to the bounded queue; on overflow `close()` and disconnect), `boolean isOpen()`, `void close()`. Sends are serialized: a drain task is scheduled on `sender` only when not already running.

- [ ] **Step 1: Write the failing test**

`RecordingSink.java` (test util):
```java
package com.ainclusive.iotsim.api.stream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** EventSink that records sent events; can simulate a write failure. */
final class RecordingSink implements EventSink {
    final List<LiveEvent> sent = new CopyOnWriteArrayList<>();
    volatile boolean completed;
    volatile java.io.IOException failWith;

    @Override
    public void send(LiveEvent event) throws java.io.IOException {
        if (failWith != null) {
            throw failWith;
        }
        sent.add(event);
    }

    @Override
    public void complete() {
        completed = true;
    }
}
```

`SubscriberTest.java`:
```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class SubscriberTest {

    /** Runs tasks inline so drains are synchronous and assertions deterministic. */
    private static final Executor INLINE = Runnable::run;

    private static LiveEvent ev(long seq) {
        return new LiveEvent(seq, "X", "p" + seq, Instant.EPOCH);
    }

    @Test
    void deliversEnqueuedEventsInOrder() {
        RecordingSink sink = new RecordingSink();
        Subscriber sub = new Subscriber(sink, 8, INLINE);

        sub.enqueue(ev(0));
        sub.enqueue(ev(1));

        assertThat(sink.sent).extracting(LiveEvent::seq).containsExactly(0L, 1L);
        assertThat(sub.isOpen()).isTrue();
    }

    @Test
    void overflowDisconnectsTheSubscriber() {
        RecordingSink sink = new RecordingSink();
        // Capacity 1, and a sender that never drains, so the queue fills then overflows.
        Executor noDrain = task -> { /* drop the drain task */ };
        Subscriber sub = new Subscriber(sink, 1, noDrain);

        sub.enqueue(ev(0)); // fills the queue
        sub.enqueue(ev(1)); // overflow -> disconnect

        assertThat(sub.isOpen()).isFalse();
        assertThat(sink.completed).isTrue();
    }

    @Test
    void sendFailureClosesTheSubscriber() {
        RecordingSink sink = new RecordingSink();
        sink.failWith = new java.io.IOException("client gone");
        Subscriber sub = new Subscriber(sink, 8, INLINE);

        sub.enqueue(ev(0));

        assertThat(sub.isOpen()).isFalse();
        assertThat(sink.completed).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.SubscriberTest"`
Expected: FAIL — `Subscriber` does not exist.

- [ ] **Step 3: Write minimal implementation**

`Subscriber.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One live connection. Events are offered to a bounded queue and drained serially
 * onto a shared sender executor (at most one drain task per subscriber at a time,
 * so ordering holds without a thread per connection). A full queue means the
 * client cannot keep up: we close it (backpressure = disconnect); it will
 * reconnect with {@code Last-Event-ID}. A failed {@code send} closes it too.
 */
final class Subscriber {

    private final EventSink sink;
    private final BlockingQueue<LiveEvent> queue;
    private final Executor sender;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(true);

    Subscriber(EventSink sink, int queueCapacity, Executor sender) {
        this.sink = sink;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.sender = sender;
    }

    void enqueue(LiveEvent event) {
        if (!open.get()) {
            return;
        }
        if (!queue.offer(event)) {
            close(); // overflow: slow client, disconnect
            return;
        }
        scheduleDrain();
    }

    boolean isOpen() {
        return open.get();
    }

    void close() {
        if (open.compareAndSet(true, false)) {
            sink.complete();
        }
    }

    private void scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            sender.execute(this::drain);
        }
    }

    private void drain() {
        try {
            LiveEvent event;
            while (open.get() && (event = queue.poll()) != null) {
                sink.send(event);
            }
        } catch (Exception e) {
            close(); // connection gone or serialization failure
        } finally {
            draining.set(false);
            // An event enqueued between the last poll and clearing the flag must
            // still be drained.
            if (open.get() && !queue.isEmpty()) {
                scheduleDrain();
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.SubscriberTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/Subscriber.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/SubscriberTest.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/RecordingSink.java
git commit -m "feat(api): IS-046 Subscriber with bounded queue + overflow disconnect

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: LiveStream (ring buffer, fan-out, Last-Event-ID replay + resync)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStream.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamTest.java`

**Interfaces:**
- Consumes: `Subscriber` (Task 3), `LiveEvent` (Task 1), `RecordingSink` (Task 3 test util).
- Produces: `final class LiveStream` with constructor `LiveStream(int bufferCapacity)`; methods:
  - `LiveEvent publish(String type, Object data, java.time.Instant at)` — assigns the next `seq`, appends to the buffer (evicting oldest past capacity), fans out to subscribers, returns the event.
  - `void addSubscriber(Subscriber sub, String lastEventId)` — atomically (under the stream lock) replays the buffered tail after `lastEventId` (or enqueues a single `resync` event on a gap / parse failure / counter-ahead) and registers the subscriber for future events.
  - `void removeSubscriber(Subscriber sub)`.
  - `int subscriberCount()`.
  - Constant `String RESYNC = "resync"`.

Replay decision (let `latest` = last assigned seq, `-1` if none; `oldest` = first buffered seq):
- `lastEventId == null` → live only.
- non-numeric → resync.
- `lid == latest` → live only (up to date).
- `lid > latest` → resync (client is ahead → process restarted, counter reset).
- `lid >= oldest - 1` → replay buffered events with `seq > lid`.
- otherwise (`lid < oldest - 1`) → resync (buffer evicted the gap).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class LiveStreamTest {

    private static final Executor INLINE = Runnable::run;

    private Subscriber sub(RecordingSink sink) {
        return new Subscriber(sink, 64, INLINE);
    }

    @Test
    void fansPublishedEventsToSubscribers() {
        LiveStream stream = new LiveStream(8);
        RecordingSink sink = new RecordingSink();
        stream.addSubscriber(sub(sink), null);

        stream.publish("SOURCE_START", "x", Instant.EPOCH);

        assertThat(sink.sent).hasSize(1);
        assertThat(sink.sent.get(0).seq()).isEqualTo(0L);
        assertThat(sink.sent.get(0).type()).isEqualTo("SOURCE_START");
    }

    @Test
    void assignsMonotonicSeqAndCapsBuffer() {
        LiveStream stream = new LiveStream(2);
        for (int i = 0; i < 5; i++) {
            assertThat(stream.publish("X", i, Instant.EPOCH).seq()).isEqualTo((long) i);
        }
        // Buffer holds only the last 2 (seq 3,4); a fresh subscriber from seq 2
        // therefore cannot be served contiguously -> resync.
        RecordingSink late = new RecordingSink();
        stream.addSubscriber(sub(late), "2");
        assertThat(late.sent).extracting(LiveEvent::type).containsExactly(LiveStream.RESYNC);
    }

    @Test
    void replaysBufferedTailAfterLastEventId() {
        LiveStream stream = new LiveStream(8);
        for (int i = 0; i < 4; i++) {
            stream.publish("X", i, Instant.EPOCH); // seq 0..3
        }
        RecordingSink resumed = new RecordingSink();
        stream.addSubscriber(sub(resumed), "1"); // expect replay of seq 2,3

        assertThat(resumed.sent).extracting(LiveEvent::seq).containsExactly(2L, 3L);
    }

    @Test
    void liveOnlyWhenNoLastEventId() {
        LiveStream stream = new LiveStream(8);
        stream.publish("X", 0, Instant.EPOCH);
        RecordingSink fresh = new RecordingSink();
        stream.addSubscriber(sub(fresh), null);

        assertThat(fresh.sent).isEmpty(); // no backlog, only future events
        stream.publish("X", 1, Instant.EPOCH);
        assertThat(fresh.sent).extracting(LiveEvent::seq).containsExactly(1L);
    }

    @Test
    void emitsResyncOnNonNumericLastEventId() {
        LiveStream stream = new LiveStream(8);
        stream.publish("X", 0, Instant.EPOCH);
        RecordingSink bad = new RecordingSink();
        stream.addSubscriber(sub(bad), "not-a-number");

        assertThat(bad.sent).extracting(LiveEvent::type).containsExactly(LiveStream.RESYNC);
        assertThat(bad.sent.get(0).hasSeq()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamTest"`
Expected: FAIL — `LiveStream` does not exist.

- [ ] **Step 3: Write minimal implementation**

`LiveStream.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * One stream (one {@link StreamKey}). Holds a bounded ring buffer of recent events
 * for {@code Last-Event-ID} replay and a set of subscribers. {@code publish} and
 * {@code addSubscriber} mutate the buffer under one lock so a joining subscriber's
 * replay and its registration for live events are atomic — no event is duplicated
 * or lost across the seam.
 */
final class LiveStream {

    /** Event type telling the client to refetch history then resume live. */
    static final String RESYNC = "resync";

    private final int bufferCapacity;
    private final Object lock = new Object();
    private final Deque<LiveEvent> buffer = new ArrayDeque<>();
    private final Set<Subscriber> subscribers = new CopyOnWriteArraySet<>();
    private long nextSeq = 0;

    LiveStream(int bufferCapacity) {
        this.bufferCapacity = bufferCapacity;
    }

    LiveEvent publish(String type, Object data, Instant at) {
        synchronized (lock) {
            LiveEvent event = new LiveEvent(nextSeq++, type, data, at);
            buffer.addLast(event);
            if (buffer.size() > bufferCapacity) {
                buffer.removeFirst();
            }
            for (Subscriber sub : subscribers) {
                sub.enqueue(event);
            }
            return event;
        }
    }

    void addSubscriber(Subscriber sub, String lastEventId) {
        synchronized (lock) {
            for (LiveEvent replay : backlogFor(lastEventId)) {
                sub.enqueue(replay);
            }
            subscribers.add(sub);
        }
    }

    void removeSubscriber(Subscriber sub) {
        subscribers.remove(sub);
    }

    int subscriberCount() {
        return subscribers.size();
    }

    /** Events to send before going live; called under {@code lock}. */
    private List<LiveEvent> backlogFor(String lastEventId) {
        if (lastEventId == null) {
            return List.of();
        }
        long lid;
        try {
            lid = Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return List.of(resync());
        }
        long latest = nextSeq - 1;
        if (lid == latest) {
            return List.of();
        }
        long oldest = buffer.isEmpty() ? latest + 1 : buffer.peekFirst().seq();
        if (lid > latest || lid < oldest - 1) {
            return List.of(resync());
        }
        List<LiveEvent> tail = new ArrayList<>();
        for (LiveEvent e : buffer) {
            if (e.seq() > lid) {
                tail.add(e);
            }
        }
        return tail;
    }

    private static LiveEvent resync() {
        return new LiveEvent(LiveEvent.NO_SEQ, RESYNC, Map.of("reason", "gap"), Instant.now());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStream.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamTest.java
git commit -m "feat(api): IS-046 LiveStream ring buffer + Last-Event-ID replay/resync

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: LiveStreamRegistry (+ LiveEventPublisher / LiveStreamSubscriptions seams, heartbeat, lifecycle)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventPublisher.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamSubscriptions.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistry.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistryTest.java`

**Interfaces:**
- Consumes: `StreamKey` (Task 1), `LiveStream` (Task 4), `Subscriber` (Task 3), `SseEmitterSink` (Task 2), `LiveEvent` (Task 1).
- Produces:
  - `interface LiveEventPublisher { void publish(StreamKey key, String type, Object data, java.time.Instant at); }`
  - `interface LiveStreamSubscriptions { org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(StreamKey key, String lastEventId); }`
  - `@org.springframework.stereotype.Component final class LiveStreamRegistry implements LiveEventPublisher, LiveStreamSubscriptions, AutoCloseable`:
    - Production constructor `LiveStreamRegistry(tools.jackson.databind.ObjectMapper json)` → defaults (buffer 256, queue 256, heartbeat 15 s), real sender pool + heartbeat scheduler.
    - Package-visible test constructor `LiveStreamRegistry(ObjectMapper json, int bufferCapacity, int queueCapacity, java.util.concurrent.Executor sender)` → no auto heartbeat thread.
    - `void heartbeatTick()` — enqueues a `heartbeat` `LiveEvent` (NO_SEQ) to every subscriber; invoked by the scheduler and directly by tests.
    - `int subscriberCount(StreamKey key)` — test helper.
    - `close()` — shuts down executors and completes all emitters.
    - Constant `String HEARTBEAT = "heartbeat"`.
  - `subscribe(...)` creates `new SseEmitter(0L)`, wires `onCompletion`/`onTimeout`/`onError` to remove the subscriber, builds an `SseEmitterSink` + `Subscriber`, calls `stream.addSubscriber(sub, lastEventId)`, returns the emitter.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class LiveStreamRegistryTest {

    private static final Executor INLINE = Runnable::run;

    private LiveStreamRegistry registry() {
        return new LiveStreamRegistry(new ObjectMapper(), 256, 256, INLINE);
    }

    @Test
    void subscribeRegistersASubscriberForTheKey() {
        LiveStreamRegistry registry = registry();
        SseEmitter emitter = registry.subscribe(StreamKey.runtime("p1"), null);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isEqualTo(1);
        assertThat(registry.subscriberCount(StreamKey.clients("p1"))).isZero();
    }

    @Test
    void publishRoutesOnlyToTheMatchingKey() {
        LiveStreamRegistry registry = registry();
        registry.subscribe(StreamKey.runtime("p1"), null);
        // Different key, no subscriber: must not throw.
        registry.publish(StreamKey.clients("d1"), "CONNECTED", java.util.Map.of(), Instant.EPOCH);
        registry.publish(StreamKey.runtime("p1"), "SOURCE_START", java.util.Map.of(), Instant.EPOCH);
        // No assertion on bytes here (covered in Task 11); this pins routing/no-throw.
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isEqualTo(1);
    }

    @Test
    void closeCompletesEmittersAndStops() {
        LiveStreamRegistry registry = registry();
        registry.subscribe(StreamKey.runtime("p1"), null);
        registry.close();
        // After close a new subscribe still returns an emitter that is immediately done.
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamRegistryTest"`
Expected: FAIL — registry/interfaces do not exist.

- [ ] **Step 3: Write minimal implementation**

`LiveEventPublisher.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.time.Instant;

/** Publish side of the registry — what {@code LiveEventHub} depends on. */
public interface LiveEventPublisher {
    void publish(StreamKey key, String type, Object data, Instant at);
}
```

`LiveStreamSubscriptions.java`:
```java
package com.ainclusive.iotsim.api.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Subscribe side of the registry — what the SSE controllers depend on. */
public interface LiveStreamSubscriptions {
    SseEmitter subscribe(StreamKey key, String lastEventId);
}
```

`LiveStreamRegistry.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns the live streams keyed by {@link StreamKey}: routes publishes, creates SSE
 * subscriptions, runs the heartbeat, and shuts everything down. Sends never run on
 * the caller's thread — each subscriber drains onto the shared {@code sender} pool.
 */
@Component
public final class LiveStreamRegistry
        implements LiveEventPublisher, LiveStreamSubscriptions, AutoCloseable {

    static final String HEARTBEAT = "heartbeat";

    private static final int DEFAULT_BUFFER = 256;
    private static final int DEFAULT_QUEUE = 256;
    private static final int HEARTBEAT_SECONDS = 15;

    private final ObjectMapper json;
    private final int bufferCapacity;
    private final int queueCapacity;
    private final Executor sender;
    private final ExecutorService ownedSender;          // null in test ctor
    private final ScheduledExecutorService heartbeat;   // null in test ctor
    private final Map<StreamKey, LiveStream> streams = new ConcurrentHashMap<>();

    public LiveStreamRegistry(ObjectMapper json) {
        this.json = json;
        this.bufferCapacity = DEFAULT_BUFFER;
        this.queueCapacity = DEFAULT_QUEUE;
        AtomicInteger n = new AtomicInteger();
        this.ownedSender = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sse-sender-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.sender = ownedSender;
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeat.scheduleAtFixedRate(
                this::heartbeatTick, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Test constructor: explicit sizes, caller-supplied executor, no heartbeat thread. */
    LiveStreamRegistry(ObjectMapper json, int bufferCapacity, int queueCapacity, Executor sender) {
        this.json = json;
        this.bufferCapacity = bufferCapacity;
        this.queueCapacity = queueCapacity;
        this.sender = sender;
        this.ownedSender = null;
        this.heartbeat = null;
    }

    @Override
    public void publish(StreamKey key, String type, Object data, Instant at) {
        LiveStream stream = streams.get(key);
        if (stream != null) {
            stream.publish(type, data, at);
        }
    }

    @Override
    public SseEmitter subscribe(StreamKey key, String lastEventId) {
        LiveStream stream = streams.computeIfAbsent(key, k -> new LiveStream(bufferCapacity));
        SseEmitter emitter = new SseEmitter(0L); // no server-side timeout
        Subscriber sub = new Subscriber(new SseEmitterSink(emitter, json), queueCapacity, sender);
        Runnable remove = () -> {
            stream.removeSubscriber(sub);
            sub.close();
            streams.computeIfPresent(key, (k, s) -> s.subscriberCount() == 0 ? null : s);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        stream.addSubscriber(sub, lastEventId);
        return emitter;
    }

    void heartbeatTick() {
        LiveEvent beat = new LiveEvent(LiveEvent.NO_SEQ, HEARTBEAT, Map.of(), Instant.now());
        for (LiveStream stream : streams.values()) {
            stream.broadcast(beat);
        }
    }

    int subscriberCount(StreamKey key) {
        LiveStream stream = streams.get(key);
        return stream == null ? 0 : stream.subscriberCount();
    }

    @Override
    public void close() {
        streams.values().forEach(LiveStream::closeAll);
        streams.clear();
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        if (ownedSender != null) {
            ownedSender.shutdown();
        }
    }
}
```

This task adds two small methods to `LiveStream` (Task 4) used by heartbeat/close — add them now:
```java
    /** Sends an unbuffered event (heartbeat/resync) to current subscribers. */
    void broadcast(LiveEvent event) {
        synchronized (lock) {
            for (Subscriber sub : subscribers) {
                sub.enqueue(event);
            }
        }
    }

    /** Closes and drops every subscriber (registry shutdown). */
    void closeAll() {
        synchronized (lock) {
            for (Subscriber sub : subscribers) {
                sub.close();
            }
            subscribers.clear();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamRegistryTest"`
Expected: PASS. Also re-run `LiveStreamTest` to confirm the two new methods didn't break it:
`./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventPublisher.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamSubscriptions.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistry.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStream.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistryTest.java
git commit -m "feat(api): IS-046 LiveStreamRegistry with heartbeat + lifecycle

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: DataSourceProjectResolver port + LiveEventHub

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/DataSourceProjectResolver.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventHub.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveEventHubTest.java`

**Interfaces:**
- Consumes: `LiveEventPublisher` (Task 5), `StreamKey` (Task 1), `ClientActivityListener`/`ClientActivityEvent`/`RuntimeActivityListener`/`RuntimeActivityEvent` (package `com.ainclusive.iotsim.platform.runtime`).
- Produces:
  - `@FunctionalInterface interface DataSourceProjectResolver { java.util.Optional<String> projectOf(String dataSourceId); }`
  - `@org.springframework.stereotype.Component final class LiveEventHub implements ClientActivityListener, RuntimeActivityListener`:
    - Production constructor `LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver)` → own single-thread daemon dispatch executor.
    - Package-visible test constructor `LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver, java.util.concurrent.Executor dispatch)`.
    - `onRuntimeActivity` → dispatch a task that resolves the project (cached, positive only) and publishes `StreamKey.runtime(projectId)`; unknown source → no publish.
    - `onClientActivity` → dispatch a task that publishes `StreamKey.clients(dataSourceId)` (no resolution).
    - Payloads are `LinkedHashMap`s mirroring the event fields.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveEventHubTest {

    record Published(StreamKey key, String type, Object data) {}

    static final class RecordingPublisher implements LiveEventPublisher {
        final List<Published> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(StreamKey key, String type, Object data, Instant at) {
            events.add(new Published(key, type, data));
        }
    }

    private static final Executor INLINE = Runnable::run;

    @Test
    void runtimeEventRoutesToProjectStream() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("proj-" + ds), INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "SOURCE_START", Instant.EPOCH, "ok"));

        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.runtime("proj-d1"));
            assertThat(p.type()).isEqualTo("SOURCE_START");
            assertThat(((Map<?, ?>) p.data())).containsEntry("dataSourceId", "d1")
                    .containsEntry("detail", "ok");
        });
    }

    @Test
    void runtimeEventForUnknownSourceIsDropped() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.empty(), INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "ERROR", Instant.EPOCH, null));

        assertThat(pub.events).isEmpty();
    }

    @Test
    void clientEventRoutesToDataSourceStreamWithoutResolution() {
        RecordingPublisher pub = new RecordingPublisher();
        AtomicInteger resolverCalls = new AtomicInteger();
        LiveEventHub hub = new LiveEventHub(pub, ds -> {
            resolverCalls.incrementAndGet();
            return Optional.of("p");
        }, INLINE);

        hub.onClientActivity(new ClientActivityEvent(
                "d1", ClientActivityEvent.Kind.CONNECTED, "c7", Instant.EPOCH));

        assertThat(resolverCalls.get()).isZero();
        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.clients("d1"));
            assertThat(p.type()).isEqualTo("CONNECTED");
            assertThat(((Map<?, ?>) p.data())).containsEntry("clientId", "c7");
        });
    }

    @Test
    void projectResolutionIsCachedPerDataSource() {
        RecordingPublisher pub = new RecordingPublisher();
        AtomicInteger resolverCalls = new AtomicInteger();
        LiveEventHub hub = new LiveEventHub(pub, ds -> {
            resolverCalls.incrementAndGet();
            return Optional.of("p");
        }, INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "A", Instant.EPOCH, null));
        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "B", Instant.EPOCH, null));

        assertThat(resolverCalls.get()).isEqualTo(1);
        assertThat(pub.events).hasSize(2);
    }

    @Test
    void listenerHandsOffToDispatchExecutorNeverInline() {
        RecordingPublisher pub = new RecordingPublisher();
        Deque<Runnable> deferred = new ArrayDeque<>();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("p"), deferred::add);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "A", Instant.EPOCH, null));

        assertThat(pub.events).isEmpty(); // nothing published on the calling (IPC) thread
        deferred.poll().run();
        assertThat(pub.events).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveEventHubTest"`
Expected: FAIL — `DataSourceProjectResolver`/`LiveEventHub` do not exist.

- [ ] **Step 3: Write minimal implementation**

`DataSourceProjectResolver.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.util.Optional;

/**
 * Resolves the owning project of a data source so runtime events (which carry only
 * a {@code dataSourceId}) can be routed to the per-project runtime stream. Impl is
 * wired in the {@code app} module over the data-source repository.
 */
@FunctionalInterface
public interface DataSourceProjectResolver {
    Optional<String> projectOf(String dataSourceId);
}
```

`LiveEventHub.java`:
```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/**
 * Bridges supervisor activity listeners (IS-047/IS-048) to the live streams.
 * Called on the IPC delivery thread, so each method only hands off to a dispatch
 * executor; project resolution and publishing happen off that thread. Positive
 * {@code dataSourceId -> projectId} results are cached (a source may be unknown
 * now but appear later, so misses are not cached).
 */
@Component
public final class LiveEventHub implements ClientActivityListener, RuntimeActivityListener {

    private final LiveEventPublisher publisher;
    private final DataSourceProjectResolver resolver;
    private final Executor dispatch;
    private final Map<String, String> projectByDataSource = new ConcurrentHashMap<>();

    public LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver) {
        this(publisher, resolver, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-hub-dispatch");
            t.setDaemon(true);
            return t;
        }));
    }

    LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver,
            Executor dispatch) {
        this.publisher = publisher;
        this.resolver = resolver;
        this.dispatch = dispatch;
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        dispatch.execute(() -> {
            String projectId = projectByDataSource.computeIfAbsent(
                    event.dataSourceId(), ds -> resolver.projectOf(ds).orElse(null));
            if (projectId == null) {
                return; // unknown source: drop (do not cache the miss)
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dataSourceId", event.dataSourceId());
            data.put("type", event.type());
            data.put("at", event.at().toString());
            data.put("detail", event.detail() == null ? "" : event.detail());
            publisher.publish(StreamKey.runtime(projectId), event.type(), data, event.at());
        });
    }

    @Override
    public void onClientActivity(ClientActivityEvent event) {
        dispatch.execute(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dataSourceId", event.dataSourceId());
            data.put("clientId", event.clientId());
            data.put("kind", event.kind().name());
            data.put("at", event.at().toString());
            publisher.publish(
                    StreamKey.clients(event.dataSourceId()), event.kind().name(), data, event.at());
        });
    }
}
```

Note on `computeIfAbsent` returning null: when the resolver yields `null`, `computeIfAbsent` does **not** store a mapping and returns `null` — exactly the "don't cache misses" behavior wanted.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveEventHubTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/DataSourceProjectResolver.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveEventHub.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveEventHubTest.java
git commit -m "feat(api): IS-046 LiveEventHub bridges supervisor events to streams

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: SSE controllers (runtime + clients)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStreamController.java`
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/ClientStreamController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/StreamControllersTest.java`

**Interfaces:**
- Consumes: `LiveStreamSubscriptions` (Task 5), `StreamKey` (Task 1).
- Produces:
  - `@RestController class RuntimeStreamController` — `GET /api/v1/projects/{projectId}/stream/runtime`, `produces = text/event-stream`, method `SseEmitter streamRuntime(String projectId, String lastEventId)` delegating `subscriptions.subscribe(StreamKey.runtime(projectId), lastEventId)`.
  - `@RestController class ClientStreamController` — `GET /api/v1/data-sources/{id}/stream/clients`, method `SseEmitter streamClients(String id, String lastEventId)` delegating `subscriptions.subscribe(StreamKey.clients(id), lastEventId)`.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamControllersTest {

    record Sub(StreamKey key, String lastEventId) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();

        @Override
        public SseEmitter subscribe(StreamKey key, String lastEventId) {
            calls.add(new Sub(key, lastEventId));
            return new SseEmitter(0L);
        }
    }

    @Test
    void runtimeControllerSubscribesToProjectRuntimeStream() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        SseEmitter emitter = new RuntimeStreamController(subs).streamRuntime("p1", "42");

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement()
                .isEqualTo(new Sub(StreamKey.runtime("p1"), "42"));
    }

    @Test
    void clientControllerSubscribesToDataSourceClientsStream() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        SseEmitter emitter = new ClientStreamController(subs).streamClients("d9", null);

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement()
                .isEqualTo(new Sub(StreamKey.clients("d9"), null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamControllersTest"`
Expected: FAIL — controllers do not exist.

- [ ] **Step 3: Write minimal implementation**

`RuntimeStreamController.java`:
```java
package com.ainclusive.iotsim.api.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live runtime-context stream for a project (SSE). Carries runtime activity events
 * plus {@code heartbeat}/{@code resync}; richer aggregation (active runs, health)
 * lands in IS-051/IS-053/IS-054. See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class RuntimeStreamController {

    private final LiveStreamSubscriptions subscriptions;

    public RuntimeStreamController(LiveStreamSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping(value = "/api/v1/projects/{projectId}/stream/runtime",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRuntime(@PathVariable String projectId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return subscriptions.subscribe(StreamKey.runtime(projectId), lastEventId);
    }
}
```

`ClientStreamController.java`:
```java
package com.ainclusive.iotsim.api.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live client connect/disconnect stream for a data source (SSE). Connected-client
 * snapshot/history is IS-052. See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class ClientStreamController {

    private final LiveStreamSubscriptions subscriptions;

    public ClientStreamController(LiveStreamSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping(value = "/api/v1/data-sources/{id}/stream/clients",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClients(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return subscriptions.subscribe(StreamKey.clients(id), lastEventId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamControllersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStreamController.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/ClientStreamController.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/StreamControllersTest.java
git commit -m "feat(api): IS-046 SSE controllers for runtime + clients streams

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: CompositeRuntimeActivityListener (platform)

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/CompositeRuntimeActivityListener.java`
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/CompositeRuntimeActivityListenerTest.java`

**Interfaces:**
- Consumes: `RuntimeActivityListener`, `RuntimeActivityEvent` (same package).
- Produces: `final class CompositeRuntimeActivityListener implements RuntimeActivityListener` with constructor `CompositeRuntimeActivityListener(RuntimeActivityListener... delegates)`; `onRuntimeActivity` forwards to every delegate in order. This lets `RuntimeConfig` fan one runtime listener to both the persister (IS-049) and the SSE hub.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeRuntimeActivityListenerTest {

    @Test
    void forwardsEachEventToEveryDelegateInOrder() {
        List<String> log = new ArrayList<>();
        RuntimeActivityListener a = e -> log.add("a:" + e.type());
        RuntimeActivityListener b = e -> log.add("b:" + e.type());
        CompositeRuntimeActivityListener composite = new CompositeRuntimeActivityListener(a, b);

        composite.onRuntimeActivity(new RuntimeActivityEvent("d1", "SOURCE_START", Instant.EPOCH, null));

        assertThat(log).containsExactly("a:SOURCE_START", "b:SOURCE_START");
    }

    @Test
    void toleratesNoDelegates() {
        CompositeRuntimeActivityListener composite = new CompositeRuntimeActivityListener();
        composite.onRuntimeActivity(new RuntimeActivityEvent("d1", "X", Instant.EPOCH, null));
        // no exception
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.CompositeRuntimeActivityListenerTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.platform.runtime;

import java.util.List;

/**
 * Fans one {@link RuntimeActivityEvent} to several listeners in order, so a single
 * supervisor runtime listener can drive both persistence (IS-049) and live SSE
 * (IS-046). Delegates must stay cheap/non-blocking — this runs on the IPC thread.
 */
public final class CompositeRuntimeActivityListener implements RuntimeActivityListener {

    private final List<RuntimeActivityListener> delegates;

    public CompositeRuntimeActivityListener(RuntimeActivityListener... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        for (RuntimeActivityListener delegate : delegates) {
            delegate.onRuntimeActivity(event);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :platform:test --tests "com.ainclusive.iotsim.platform.runtime.CompositeRuntimeActivityListenerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/CompositeRuntimeActivityListener.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/CompositeRuntimeActivityListenerTest.java
git commit -m "feat(platform): IS-046 CompositeRuntimeActivityListener fan-out

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: RepositoryDataSourceProjectResolver (app)

**Files:**
- Create: `app/src/main/java/com/ainclusive/iotsim/app/runtime/RepositoryDataSourceProjectResolver.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/runtime/RepositoryDataSourceProjectResolverTest.java`

**Interfaces:**
- Consumes: `DataSourceProjectResolver` (Task 6), `com.ainclusive.iotsim.persistence.datasource.DataSourceRepository`, `DataSourceRow`.
- Produces: `@Component final class RepositoryDataSourceProjectResolver implements DataSourceProjectResolver` with constructor `(DataSourceRepository dataSources)`; `projectOf(id)` = `dataSources.findById(id).map(DataSourceRow::projectId)`.

- [ ] **Step 1: Write the failing test**

This test fakes `DataSourceRepository` (an interface) with a minimal in-test implementation — no Mockito needed.

```java
package com.ainclusive.iotsim.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryDataSourceProjectResolverTest {

    /** Minimal fake: only findById is exercised. */
    private static DataSourceRepository repoReturning(DataSourceRow row) {
        return new DataSourceRepository() {
            @Override
            public DataSourceRow insert(String p, String n, String pr, String b, String e,
                    String r, String c) {
                throw new UnsupportedOperationException();
            }
            @Override
            public List<DataSourceRow> findByProject(String projectId) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Optional<DataSourceRow> findById(String id) {
                return Optional.ofNullable(row);
            }
            @Override
            public Optional<DataSourceRow> update(String id, String n, String e, String r,
                    boolean en, long v) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean deleteById(String id) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void resolvesProjectOfAKnownDataSource() {
        DataSourceRow row = sampleRow("d1", "proj-1");
        var resolver = new RepositoryDataSourceProjectResolver(repoReturning(row));
        assertThat(resolver.projectOf("d1")).contains("proj-1");
    }

    @Test
    void emptyForUnknownDataSource() {
        var resolver = new RepositoryDataSourceProjectResolver(repoReturning(null));
        assertThat(resolver.projectOf("nope")).isEmpty();
    }

    // DataSourceRow components (verified):
    // (id, projectId, name, protocol, basis, schemaId, schemaVersion, endpoint,
    //  runtimeConfig, enabled, createdAt, updatedAt, createdBy, version).
    // Only id + projectId are asserted; the rest are placeholders.
    private static DataSourceRow sampleRow(String id, String projectId) {
        java.time.OffsetDateTime t = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z");
        return new DataSourceRow(id, projectId, "name", "opcua", "PROVIDED",
                null, null, "{}", "{}", true, t, t, "tester", 1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

First confirm the row shape, then run:
Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.runtime.RepositoryDataSourceProjectResolverTest"`
Expected: FAIL — resolver class does not exist (after `sampleRow` compiles against the real `DataSourceRow`).

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.app.runtime;

import com.ainclusive.iotsim.api.stream.DataSourceProjectResolver;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves a data source's project via the repository, for SSE runtime routing (IS-046). */
@Component
public final class RepositoryDataSourceProjectResolver implements DataSourceProjectResolver {

    private final DataSourceRepository dataSources;

    public RepositoryDataSourceProjectResolver(DataSourceRepository dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public Optional<String> projectOf(String dataSourceId) {
        return dataSources.findById(dataSourceId).map(DataSourceRow::projectId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.runtime.RepositoryDataSourceProjectResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/runtime/RepositoryDataSourceProjectResolver.java \
        app/src/test/java/com/ainclusive/iotsim/app/runtime/RepositoryDataSourceProjectResolverTest.java
git commit -m "feat(app): IS-046 repository-backed data-source project resolver

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Wire the SSE hub into the supervisor (RuntimeConfig)

**Files:**
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java`

**Interfaces:**
- Consumes: `LiveEventHub` (Task 6, an `api` `@Component` → autowired), `CompositeRuntimeActivityListener` (Task 8), `com.ainclusive.iotsim.supervisor.HealthPolicy`, the existing `PersistingRuntimeActivityListener`, the 5-arg `Supervisor(WorkerLauncher, RestartPolicy, HealthPolicy, ClientActivityListener, RuntimeActivityListener)` constructor.
- Produces: the `runtimeController` bean now feeds the supervisor a runtime listener that fans to **both** the persister and the hub, and uses the hub as the client-activity listener (previously `NONE`).

This is `@Bean` wiring glue. The codebase does not unit-test `@Bean` methods; the behavior it relies on is covered by Task 6 (hub routing), Task 8 (composite fan-out), and Task 11 (end-to-end). Verification here is a green `./gradlew build`.

- [ ] **Step 1: Edit `runtimeController` to compose listeners**

Change the method signature to accept the hub, and the supervisor-mode branch to compose listeners. Final method:

```java
    @Bean
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents, ObjectMapper json,
            ExecutorService runtimeEventExecutor, LiveEventHub liveEventHub) {
        if (props.isSupervisorMode()) {
            // Persist runtime events off the IPC delivery thread (IS-048), and fan the
            // same events to the live SSE hub (IS-046). Client-activity events feed the
            // hub directly (previously dropped via ClientActivityListener.NONE).
            RuntimeActivityListener persister = new PersistingRuntimeActivityListener(
                    dataSources, runtimeEvents, json, runtimeEventExecutor);
            RuntimeActivityListener runtimeListener =
                    new CompositeRuntimeActivityListener(persister, liveEventHub);
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, liveEventHub, runtimeListener);
        }
        return new InMemoryRuntimeController();
    }
```

Add imports:
```java
import com.ainclusive.iotsim.api.stream.LiveEventHub;
import com.ainclusive.iotsim.platform.runtime.CompositeRuntimeActivityListener;
import com.ainclusive.iotsim.supervisor.HealthPolicy;
```

- [ ] **Step 2: Compile + full module build**

Run: `./gradlew :app:compileJava`
Expected: BUILD SUCCESSFUL (types line up with the 5-arg `Supervisor` ctor).

- [ ] **Step 3: Run the app module test suite**

Run: `./gradlew :app:test`
Expected: PASS (no regressions; existing supervisor/runtime tests still green).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java
git commit -m "feat(app): IS-046 fan supervisor events to SSE hub (runtime + clients)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: End-to-end SSE wire test (app, MockMvc async)

**Files:**
- Test: `app/src/test/java/com/ainclusive/iotsim/app/stream/RuntimeStreamEndToEndTest.java`

**Interfaces:**
- Consumes: `RuntimeStreamController` (Task 7), `LiveStreamRegistry` (Task 5), `StreamKey` (Task 1), MockMvc (`spring-boot-starter-webmvc-test`, present in `app`).

This test proves the real servlet path: a `GET` opens an SSE stream, a publish reaches the client, and the bytes are framed (`id:`/`event:`/`data:` JSON). It uses `@WebMvcTest` for just the controller, supplies a real `LiveStreamRegistry`, and flushes the async response by closing the registry (which completes emitters — the same path as graceful shutdown).

- [ ] **Step 1: Write the test**

```java
package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.stream.LiveStreamRegistry;
import com.ainclusive.iotsim.api.stream.RuntimeStreamController;
import com.ainclusive.iotsim.api.stream.StreamKey;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = RuntimeStreamController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(RuntimeStreamEndToEndTest.TestBeans.class)
class RuntimeStreamEndToEndTest {

    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
        @Bean
        LiveStreamRegistry liveStreamRegistry(ObjectMapper json) {
            return new LiveStreamRegistry(json);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired LiveStreamRegistry registry;

    @Test
    void streamsAPublishedRuntimeEventAsFramedSse() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/projects/p1/stream/runtime"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        registry.publish(StreamKey.runtime("p1"), "SOURCE_START",
                Map.of("dataSourceId", "d1"), Instant.EPOCH);

        // Complete the emitter so the async response body is finalized, then dispatch.
        registry.close();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .asyncDispatch(result));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:SOURCE_START");
        assertThat(body).contains("id:0");
        assertThat(body).contains("\"dataSourceId\":\"d1\"");
    }
}
```

Note: if `request().asyncStarted()` proves flaky because the publish must occur before the container reads the body, an alternative is to `registry.subscribe(...)`-then-publish ordering — but with `SseEmitter(0L)` the subscription is open immediately after the request returns, so publishing right after `andReturn()` is safe. If the assertion on `id:0` fails due to SSE line formatting (`id: 0` with a space), relax to `body.contains("id:")` and assert the seq via the data — adjust to the actual framing emitted by this Spring version.

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.stream.RuntimeStreamEndToEndTest"`
Expected: PASS — body contains the framed event with JSON data.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/ainclusive/iotsim/app/stream/RuntimeStreamEndToEndTest.java
git commit -m "test(app): IS-046 end-to-end SSE runtime stream wire test

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Full build, docs checkbox, definition of done

**Files:**
- Modify: `backend-specs/TASKS.md:164` (flip the IS-046 checkbox — done in the PR per the catalog-sync gate; the `/open-pr` skill handles this).

- [ ] **Step 1: Full green build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all modules compile, Spotless/Checkstyle clean, every test (api/platform/app) passes.

- [ ] **Step 2: Fix any Spotless/Checkstyle issues**

If Spotless fails: `./gradlew spotlessApply` then re-run `./gradlew build`.
If Checkstyle flags the new files, fix to satisfy `config/checkstyle/checkstyle.xml` (import order, Javadoc, line length) and re-run.

- [ ] **Step 3: Open the PR via the project skill**

Do NOT hand-edit the checkbox or craft the PR manually. Invoke the `/open-pr` skill from the feature branch: it runs the Definition-of-Done checks, flips the `TASKS.md` IS-046 checkbox in the same PR (CI catalog-sync gate), creates the PR with `Implements: IS-046`, arms squash auto-merge, and moves the board to In review.

---

## Self-Review

**1. Spec coverage:**
- Transport layer (emitter registry, fan-out hub) → Tasks 3–6. ✔
- `GET /projects/{pid}/stream/runtime` → Task 7 (controller) + Task 10 (wiring) + Task 11 (e2e). ✔
- `GET /data-sources/{id}/stream/clients` → Task 7 + Task 10. ✔
- Event = type + payload → `LiveEvent` + hub payload maps (Tasks 1, 6). ✔
- `Last-Event-ID` reconnect + in-memory ring buffer + resync → Task 4. ✔
- Backpressure = disconnect slow client → Task 3. ✔
- IPC thread non-blocking (dispatch executor) → Task 6. ✔
- Heartbeat 15 s → Task 5. ✔
- Composite listener so persistence keeps working + client listener wired → Tasks 8, 10. ✔
- `dataSourceId → projectId` resolution via domain-backed port → Tasks 6 (port) + 9 (impl). ✔
- Auth inherited from `SecurityConfig`, no change → no task needed (verified in design). ✔
- No new dependencies → all classes use `spring-web`/Jackson already present. ✔
- Defaults (buffer/queue/heartbeat/timeout) → Task 5 constants. ✔

**2. Placeholder scan:** No "TBD/TODO/implement later". Two explicit "confirm the exact shape" notes (DataSourceRow constructor in Task 9; SSE `id:`/`id: ` framing in Task 11) are real verification instructions with fallbacks, not deferred work.

**3. Type consistency:** `StreamKey.runtime/clients`, `LiveEvent(seq,type,data,at)` + `NO_SEQ`/`hasSeq`, `EventSink.send/complete`, `Subscriber(sink,capacity,sender)`/`enqueue`/`isOpen`/`close`, `LiveStream(bufferCapacity)`/`publish(type,data,at)`/`addSubscriber(sub,lastEventId)`/`removeSubscriber`/`subscriberCount`/`broadcast`/`closeAll`/`RESYNC`, `LiveEventPublisher.publish(key,type,data,at)`, `LiveStreamSubscriptions.subscribe(key,lastEventId)`, `LiveStreamRegistry` ctors + `heartbeatTick`/`subscriberCount`/`close`/`HEARTBEAT`, `DataSourceProjectResolver.projectOf`, `LiveEventHub` ctors + listener methods, controller method names, `CompositeRuntimeActivityListener(varargs)`. Names are consistent across producing and consuming tasks. ✔
