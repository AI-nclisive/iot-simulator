# IS-051 Live Values + Runtime State over SSE — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stream conflated/throttled live values on a new `GET /api/v1/data-sources/{id}/stream/values`, and add a current per-source `RuntimeState` snapshot-on-connect to the existing `…/stream/runtime`, both over the IS-046 SSE transport.

**Architecture:** Tee the values the supervisor already serves (inside `Supervisor.applyValues`) to a new `LiveValueListener`; an api-side `LiveValuesHub` records them into a `LiveValueStore` (latest-per-node + dirty set) and a ~250 ms scheduled flush publishes the changed values as SSE deltas via the IS-046 `LiveEventPublisher`. A new registry seam enqueues a per-subscriber initial snapshot (the conflated values map, or the runtime-state list) before live events.

**Tech Stack:** Java 25, Spring Boot 4.1 (Web MVC servlet, NOT WebFlux), `SseEmitter`, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ; MockMvc/Mockito only in `app`.

## Global Constraints

- **No new dependencies** (everything is on the existing classpath; `SseEmitter` is in `spring-web`).
- **Jackson 3:** `tools.jackson.databind.ObjectMapper`; serialization throws unchecked `tools.jackson.core.JacksonException`.
- **Tee must not block the supervisor/replay thread:** the listener only updates a concurrent store; no I/O, no SSE send on that thread.
- **Conflate/throttle:** latest value per `nodeId`; a single ~250 ms flush emits only the nodes changed since the last flush; snapshot-on-connect carries the full current map.
- **Reuse IS-046 transport** (heartbeat, reconnect, backpressure-disconnect). Do not reinvent it.
- **Reconnect rule:** VALUES always sends `values-snapshot` on connect and ignores `Last-Event-ID` (no delta replay — snapshot supersedes). RUNTIME sends a `runtime-state` snapshot only on a fresh connect (no `Last-Event-ID`); with `Last-Event-ID` it keeps the IS-046 backlog replay and sends no snapshot.
- **Multi-constructor `@Component` rule:** any `@Component` with more than one constructor MUST annotate the production ctor `@org.springframework.beans.factory.annotation.Autowired` (else the app context fails to boot — Spring can't choose a ctor). Validate context-affecting changes with `./gradlew build --rerun-tasks` (a plain build can serve `:app:test` from cache and mask `ApplicationSmokeIT`).
- **API base path** `/api/v1`; SSE `produces=text/event-stream`.
- Run `./gradlew build` green before done; add/update tests for every change.
- Commit messages end with: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

## File structure

New:
- `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/LiveValueListener.java` — tee sink (platform port).
- `api/src/main/java/com/ainclusive/iotsim/api/stream/StreamValue.java` — SSE value payload record.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValueStore.java` — conflation store.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValuesHub.java` — `@Component` listener + scheduled flush.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/ValuesStreamController.java` — `/stream/values` endpoint.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/ProjectSources.java` — port: project → data-source ids.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/DomainProjectSources.java` — `ProjectSources` impl over `DataSourceService`.
- `api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshot.java` — runtime-state snapshot builder.

Modified:
- `StreamKey.java` (add `VALUES` + factory), `LiveStreamSubscriptions.java` (3-arg overload), `LiveStream.java` (3-arg `addSubscriber`), `LiveStreamRegistry.java` (3-arg `subscribe`), `RuntimeStreamController.java` (runtime-state snapshot), `Supervisor.java` (tee), `RuntimeConfig.java` (wiring).

---

### Task 1: StreamKey.VALUES + LiveValueListener port

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/StreamKey.java`
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/LiveValueListener.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/StreamKeyTest.java` (add a case)
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/LiveValueListenerTest.java`

**Interfaces:**
- Produces:
  - `StreamKey.Type.VALUES` and `static StreamKey values(String dataSourceId)`.
  - `interface LiveValueListener { LiveValueListener NONE = (id, values, at) -> {}; void onValues(String dataSourceId, java.util.List<NeutralValue> values, java.time.Instant at); }` in package `com.ainclusive.iotsim.platform.runtime` (`NeutralValue` = `com.ainclusive.iotsim.protocolmodel.NeutralValue`).

- [ ] **Step 1: Write the failing tests**

Add to `StreamKeyTest.java`:
```java
    @Test
    void valuesFactoryBuildsTypedKey() {
        assertThat(StreamKey.values("d1"))
                .isEqualTo(new StreamKey(StreamKey.Type.VALUES, "d1"));
        assertThat(StreamKey.values("d1")).isNotEqualTo(StreamKey.clients("d1"));
    }
```

Create `LiveValueListenerTest.java`:
```java
package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveValueListenerTest {

    @Test
    void noneIgnoresEventsWithoutThrowing() {
        assertThatCode(() -> LiveValueListener.NONE.onValues(
                "d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 1)), Instant.EPOCH))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamKeyTest" :platform:test --tests "com.ainclusive.iotsim.platform.runtime.LiveValueListenerTest"`
Expected: FAIL — `StreamKey.values` and `LiveValueListener` do not exist.

- [ ] **Step 3: Write minimal implementation**

In `StreamKey.java`, add `VALUES` to the enum and the factory:
```java
    public enum Type {
        RUNTIME,
        CLIENTS,
        VALUES
    }
```
```java
    /** Live-value stream for a data source: {@code /data-sources/{id}/stream/values}. */
    public static StreamKey values(String dataSourceId) {
        return new StreamKey(Type.VALUES, dataSourceId);
    }
```

Create `LiveValueListener.java`:
```java
package com.ainclusive.iotsim.platform.runtime;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;

/**
 * Sink for the live (conflated) values a running data source is currently serving
 * (IS-051). The supervisor calls it from {@code applyValues} as values are pushed to
 * the worker; the api layer supplies an implementation that conflates and fans them
 * out over SSE.
 *
 * <p>Called on the supervisor/replay thread, so implementations must be cheap and
 * non-blocking — update a store only. {@link #NONE} is the default when nothing
 * observes values.
 */
@FunctionalInterface
public interface LiveValueListener {

    /** Discards every batch; the default when nothing observes live values. */
    LiveValueListener NONE = (dataSourceId, values, at) -> {};

    void onValues(String dataSourceId, List<NeutralValue> values, Instant at);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamKeyTest" :platform:test --tests "com.ainclusive.iotsim.platform.runtime.LiveValueListenerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/StreamKey.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/StreamKeyTest.java \
        platform/src/main/java/com/ainclusive/iotsim/platform/runtime/LiveValueListener.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/LiveValueListenerTest.java
git commit -m "feat(api): IS-051 StreamKey.VALUES + LiveValueListener port

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: StreamValue payload record

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/StreamValue.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/StreamValueTest.java`

**Interfaces:**
- Consumes: `NeutralValue(nodeId, sourceTime, value, quality, qualityReason)`.
- Produces: `record StreamValue(String nodeId, Object value, String quality, String qualityReason, String sourceTime)` with `static StreamValue from(NeutralValue v)` (`quality` = `v.quality().name()`; `sourceTime` = `v.sourceTime().toString()`; `value`/`qualityReason` passed through, may be null).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StreamValueTest {

    @Test
    void mapsAllFieldsFromNeutralValue() {
        StreamValue sv = StreamValue.from(
                new NeutralValue("n1", Instant.EPOCH, 42, Quality.GOOD, null));
        assertThat(sv.nodeId()).isEqualTo("n1");
        assertThat(sv.value()).isEqualTo(42);
        assertThat(sv.quality()).isEqualTo("GOOD");
        assertThat(sv.qualityReason()).isNull();
        assertThat(sv.sourceTime()).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    void allowsNullValueForMissing() {
        StreamValue sv = StreamValue.from(
                new NeutralValue("n2", Instant.EPOCH, null, Quality.BAD, "COMM_FAILURE"));
        assertThat(sv.value()).isNull();
        assertThat(sv.quality()).isEqualTo("BAD");
        assertThat(sv.qualityReason()).isEqualTo("COMM_FAILURE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamValueTest"`
Expected: FAIL — `StreamValue` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;

/**
 * SSE payload for one live value. Mirrors {@link NeutralValue} but renders enum and
 * instant as strings for a stable JSON shape. {@code value} and {@code qualityReason}
 * may be null (missing value / no reason).
 */
public record StreamValue(
        String nodeId, Object value, String quality, String qualityReason, String sourceTime) {

    public static StreamValue from(NeutralValue v) {
        return new StreamValue(
                v.nodeId(), v.value(), v.quality().name(), v.qualityReason(),
                v.sourceTime().toString());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.StreamValueTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/StreamValue.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/StreamValueTest.java
git commit -m "feat(api): IS-051 StreamValue SSE payload record

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: LiveValueStore (conflation)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValueStore.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveValueStoreTest.java`

**Interfaces:**
- Consumes: `NeutralValue`.
- Produces: `final class LiveValueStore` with:
  - `void record(String dataSourceId, List<NeutralValue> values)` — latest wins per `nodeId`; marks the source + changed nodes dirty.
  - `List<NeutralValue> snapshot(String dataSourceId)` — all current latest values (empty if none).
  - `List<NeutralValue> drainChanged(String dataSourceId)` — values changed since last drain, then clears that source's dirty set.
  - `Set<String> dirtySources()` — sources with pending changes.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveValueStoreTest {

    private static NeutralValue v(String node, int val) {
        return NeutralValue.good(node, Instant.EPOCH, val);
    }

    @Test
    void latestWinsPerNodeAndSnapshotReturnsAll() {
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(v("n1", 1), v("n2", 2)));
        store.record("d1", List.of(v("n1", 9))); // n1 updated

        assertThat(store.snapshot("d1"))
                .extracting(NeutralValue::nodeId, NeutralValue::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("n1", 9),
                        org.assertj.core.groups.Tuple.tuple("n2", 2));
    }

    @Test
    void drainChangedReturnsOnlyChangedThenClears() {
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(v("n1", 1), v("n2", 2)));

        assertThat(store.drainChanged("d1"))
                .extracting(NeutralValue::nodeId).containsExactlyInAnyOrder("n1", "n2");
        // nothing changed since the drain
        assertThat(store.drainChanged("d1")).isEmpty();
        assertThat(store.dirtySources()).doesNotContain("d1");

        store.record("d1", List.of(v("n2", 5)));
        assertThat(store.dirtySources()).contains("d1");
        assertThat(store.drainChanged("d1")).extracting(NeutralValue::nodeId).containsExactly("n2");
    }

    @Test
    void snapshotEmptyForUnknownSource() {
        assertThat(new LiveValueStore().snapshot("nope")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveValueStoreTest"`
Expected: FAIL — `LiveValueStore` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conflated live-value state: the latest value per (dataSourceId, nodeId), plus the
 * set of nodes changed since the last flush. Lock-free; a benign race may re-emit a
 * value on the next flush (idempotent for a latest-value view).
 */
final class LiveValueStore {

    private final Map<String, Map<String, NeutralValue>> latest = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> dirty = new ConcurrentHashMap<>();

    void record(String dataSourceId, List<NeutralValue> values) {
        Map<String, NeutralValue> bySource =
                latest.computeIfAbsent(dataSourceId, k -> new ConcurrentHashMap<>());
        Set<String> dirtyNodes =
                dirty.computeIfAbsent(dataSourceId, k -> ConcurrentHashMap.newKeySet());
        for (NeutralValue v : values) {
            bySource.put(v.nodeId(), v);
            dirtyNodes.add(v.nodeId());
        }
    }

    List<NeutralValue> snapshot(String dataSourceId) {
        Map<String, NeutralValue> bySource = latest.get(dataSourceId);
        return bySource == null ? List.of() : new ArrayList<>(bySource.values());
    }

    List<NeutralValue> drainChanged(String dataSourceId) {
        Set<String> dirtyNodes = dirty.get(dataSourceId);
        Map<String, NeutralValue> bySource = latest.get(dataSourceId);
        if (dirtyNodes == null || bySource == null || dirtyNodes.isEmpty()) {
            return List.of();
        }
        List<NeutralValue> changed = new ArrayList<>();
        for (String nodeId : List.copyOf(dirtyNodes)) {
            dirtyNodes.remove(nodeId);
            NeutralValue v = bySource.get(nodeId);
            if (v != null) {
                changed.add(v);
            }
        }
        return changed;
    }

    Set<String> dirtySources() {
        Set<String> result = ConcurrentHashMap.newKeySet();
        dirty.forEach((source, nodes) -> {
            if (!nodes.isEmpty()) {
                result.add(source);
            }
        });
        return result;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveValueStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValueStore.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveValueStoreTest.java
git commit -m "feat(api): IS-051 LiveValueStore conflation (latest-per-node + dirty)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: LiveValuesHub (listener + scheduled flush)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValuesHub.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveValuesHubTest.java`

**Interfaces:**
- Consumes: `LiveValueListener` (Task 1), `LiveValueStore` (Task 3), `StreamValue` (Task 2), `StreamKey.values` (Task 1), `LiveEventPublisher` (`publish(StreamKey, String type, Object data, Instant at)`).
- Produces: `@Component final class LiveValuesHub implements LiveValueListener, AutoCloseable`:
  - production ctor `@Autowired LiveValuesHub(LiveEventPublisher publisher)` (creates its own `LiveValueStore`, a daemon single-thread scheduler flushing every 250 ms).
  - package-visible test ctor `LiveValuesHub(LiveEventPublisher publisher, LiveValueStore store)` (no scheduler).
  - `LiveValueStore store()` accessor (so the controller bean reads the same store).
  - `void flushTick()` (package-visible) — publishes one `values` event per dirty source.
  - `onValues(...)` → `store.record(...)`. `close()` → shutdown scheduler.

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class LiveValuesHubTest {

    record Published(StreamKey key, String type, Object data) {}

    static final class RecordingPublisher implements LiveEventPublisher {
        final List<Published> events = new CopyOnWriteArrayList<>();
        @Override
        public void publish(StreamKey key, String type, Object data, Instant at) {
            events.add(new Published(key, type, data));
        }
    }

    private static NeutralValue v(String node, int val) {
        return NeutralValue.good(node, Instant.EPOCH, val);
    }

    @Test
    void flushPublishesChangedValuesPerSource() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveValueStore store = new LiveValueStore();
        LiveValuesHub hub = new LiveValuesHub(pub, store);

        hub.onValues("d1", List.of(v("n1", 1)), Instant.EPOCH);
        hub.flushTick();

        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.values("d1"));
            assertThat(p.type()).isEqualTo("values");
            assertThat((List<?>) p.data()).hasSize(1);
            assertThat(((StreamValue) ((List<?>) p.data()).get(0)).nodeId()).isEqualTo("n1");
        });
    }

    @Test
    void flushPublishesNothingWhenNoChanges() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveValuesHub hub = new LiveValuesHub(pub, new LiveValueStore());
        hub.flushTick();
        assertThat(pub.events).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveValuesHubTest"`
Expected: FAIL — `LiveValuesHub` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.LiveValueListener;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bridges the supervisor's value tee (IS-051) to the live SSE streams: records values
 * into a {@link LiveValueStore} (conflation) and, on a fixed cadence, publishes the
 * changed values per source as {@code values} deltas. {@code onValues} runs on the
 * supervisor/replay thread, so it only touches the store; sending happens on the
 * registry's pool via {@link LiveEventPublisher}.
 */
@Component
public final class LiveValuesHub implements LiveValueListener, AutoCloseable {

    private static final int FLUSH_MILLIS = 250;

    private final LiveEventPublisher publisher;
    private final LiveValueStore store;
    private final ScheduledExecutorService flusher; // null in test ctor

    @Autowired
    public LiveValuesHub(LiveEventPublisher publisher) {
        this.publisher = publisher;
        this.store = new LiveValueStore();
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-values-flush");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(
                this::flushTick, FLUSH_MILLIS, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Test ctor: caller-driven {@link #flushTick()}, no scheduler thread. */
    LiveValuesHub(LiveEventPublisher publisher, LiveValueStore store) {
        this.publisher = publisher;
        this.store = store;
        this.flusher = null;
    }

    LiveValueStore store() {
        return store;
    }

    @Override
    public void onValues(String dataSourceId, List<NeutralValue> values, Instant at) {
        store.record(dataSourceId, values);
    }

    void flushTick() {
        for (String dataSourceId : store.dirtySources()) {
            List<NeutralValue> changed = store.drainChanged(dataSourceId);
            if (!changed.isEmpty()) {
                List<StreamValue> payload = changed.stream().map(StreamValue::from).toList();
                publisher.publish(StreamKey.values(dataSourceId), "values", payload, Instant.now());
            }
        }
    }

    @Override
    public void close() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveValuesHubTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveValuesHub.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveValuesHubTest.java
git commit -m "feat(api): IS-051 LiveValuesHub conflated value flush over SSE

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Registry initial-snapshot seam

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamSubscriptions.java`
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStream.java`
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistry.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamTest.java` (add a case)

**Interfaces:**
- Produces: a 3-arg subscribe that enqueues a per-subscriber initial snapshot before live events.
  - `LiveStreamSubscriptions`: add `SseEmitter subscribe(StreamKey key, String lastEventId, java.util.List<LiveEvent> initial);` (the existing 2-arg stays).
  - `LiveStream`: `void addSubscriber(Subscriber sub, String lastEventId, List<LiveEvent> initial)`; the existing 2-arg delegates with `List.of()`.
  - `LiveStreamRegistry`: implement the 3-arg; the existing 2-arg delegates with `List.of()`.

- [ ] **Step 1: Write the failing test**

Test the ordering at the `LiveStream` level with the existing `RecordingSink` test util (from IS-046, in `api/src/test/.../stream/RecordingSink.java`) — deterministic, no servlet. Add to `LiveStreamTest.java`:
```java
    @Test
    void initialSnapshotIsEnqueuedBeforeLiveEvents() {
        LiveStream stream = new LiveStream(8);
        RecordingSink sink = new RecordingSink();
        Subscriber sub = new Subscriber(sink, 64, Runnable::run);
        LiveEvent snap = new LiveEvent(LiveEvent.NO_SEQ, "values-snapshot", "S", Instant.EPOCH);

        stream.addSubscriber(sub, null, java.util.List.of(snap)); // 3-arg with initial
        stream.publish("values", "L", Instant.EPOCH);             // a live event after

        assertThat(sink.sent).extracting(LiveEvent::type)
                .containsExactly("values-snapshot", "values"); // snapshot first, then live
    }
```

(This exercises the new `LiveStream.addSubscriber(sub, lastEventId, initial)`. The 3-arg `LiveStreamRegistry.subscribe`/`LiveStreamSubscriptions.subscribe` are thin delegations to it; the existing `LiveStreamRegistryTest` smoke cases plus this ordering test cover the seam.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamTest"`
Expected: FAIL — 3-arg `addSubscriber` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

`LiveStreamSubscriptions.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Subscribe side of the registry — what the SSE controllers depend on. */
public interface LiveStreamSubscriptions {

    SseEmitter subscribe(StreamKey key, String lastEventId);

    /** Subscribe and deliver {@code initial} events to this subscriber before live events. */
    SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial);
}
```

`LiveStream.java` — replace `addSubscriber(sub, lastEventId)` with a 3-arg plus a 2-arg delegate:
```java
    void addSubscriber(Subscriber sub, String lastEventId) {
        addSubscriber(sub, lastEventId, List.of());
    }

    void addSubscriber(Subscriber sub, String lastEventId, List<LiveEvent> initial) {
        synchronized (lock) {
            for (LiveEvent e : initial) {
                sub.enqueue(e);
            }
            for (LiveEvent replay : backlogFor(lastEventId)) {
                sub.enqueue(replay);
            }
            subscribers.add(sub);
        }
    }
```

`LiveStreamRegistry.java` — make the existing 2-arg `subscribe` delegate, and add the 3-arg (move the body into it, calling `addSubscriber(sub, lastEventId, initial)`):
```java
    @Override
    public SseEmitter subscribe(StreamKey key, String lastEventId) {
        return subscribe(key, lastEventId, java.util.List.of());
    }

    @Override
    public SseEmitter subscribe(StreamKey key, String lastEventId, java.util.List<LiveEvent> initial) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber sub = new Subscriber(new SseEmitterSink(emitter, json), queueCapacity, sender);
        streams.compute(key, (k, existing) -> {
            LiveStream stream = (existing != null) ? existing : new LiveStream(bufferCapacity);
            stream.addSubscriber(sub, lastEventId, initial);
            return stream;
        });
        Runnable remove = () -> streams.computeIfPresent(key, (k, s) -> {
            s.removeSubscriber(sub);
            sub.close();
            return s.subscriberCount() == 0 ? null : s;
        });
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.LiveStreamRegistryTest" "com.ainclusive.iotsim.api.stream.LiveStreamTest"`
Expected: PASS (both the new case and the unchanged IS-046 cases).

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamSubscriptions.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStream.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/LiveStreamRegistry.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/LiveStreamTest.java
git commit -m "feat(api): IS-051 per-subscriber initial-snapshot subscribe seam

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: ValuesStreamController

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/ValuesStreamController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/ValuesStreamControllerTest.java`

**Interfaces:**
- Consumes: `LiveStreamSubscriptions` (3-arg, Task 5), `LiveValueStore` (Task 3), `StreamValue` (Task 2), `StreamKey.values` (Task 1), `LiveValuesHub.store()` (Task 4 — the controller reads the hub's store so snapshot and deltas share state).
- Produces: `@RestController class ValuesStreamController` — `GET /api/v1/data-sources/{id}/stream/values`, `produces=text/event-stream`. Builds a `values-snapshot` `LiveEvent` (NO_SEQ) from `store.snapshot(id)` mapped via `StreamValue.from`, and calls `subscriptions.subscribe(StreamKey.values(id), null, List.of(snapshot))` — `lastEventId` is intentionally not used (the snapshot is the resync).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ValuesStreamControllerTest {

    record Sub(StreamKey key, String lastEventId, List<LiveEvent> initial) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId) {
            return subscribe(key, lastEventId, List.of());
        }
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial) {
            calls.add(new Sub(key, lastEventId, initial));
            return new SseEmitter(0L);
        }
    }

    @Test
    void subscribesToValuesStreamWithSnapshotAndNoLastEventId() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 7)));

        SseEmitter emitter = new ValuesStreamController(subs, store).streamValues("d1", "42");

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.key()).isEqualTo(StreamKey.values("d1"));
            assertThat(c.lastEventId()).isNull(); // snapshot supersedes Last-Event-ID
            assertThat(c.initial()).singleElement().satisfies(ev -> {
                assertThat(ev.type()).isEqualTo("values-snapshot");
                assertThat(ev.hasSeq()).isFalse();
                assertThat((List<?>) ev.data()).hasSize(1);
            });
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.ValuesStreamControllerTest"`
Expected: FAIL — `ValuesStreamController` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live (conflated) values for a data source's Values tab (SSE, IS-051). On connect the
 * client gets a {@code values-snapshot} (current latest-per-node) and then {@code values}
 * deltas. {@code Last-Event-ID} is intentionally ignored — the snapshot is the resync.
 * See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class ValuesStreamController {

    private final LiveStreamSubscriptions subscriptions;
    private final LiveValueStore store;

    public ValuesStreamController(LiveStreamSubscriptions subscriptions, LiveValuesHub valuesHub) {
        this(subscriptions, valuesHub.store());
    }

    ValuesStreamController(LiveStreamSubscriptions subscriptions, LiveValueStore store) {
        this.subscriptions = subscriptions;
        this.store = store;
    }

    @GetMapping(value = "/api/v1/data-sources/{id}/stream/values",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamValues(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        List<StreamValue> snapshot = store.snapshot(id).stream().map(StreamValue::from).toList();
        LiveEvent snapshotEvent =
                new LiveEvent(LiveEvent.NO_SEQ, "values-snapshot", snapshot, Instant.now());
        return subscriptions.subscribe(StreamKey.values(id), null, List.of(snapshotEvent));
    }
}
```

Note: the production ctor takes `LiveValuesHub` (so Spring wires the same store the hub flushes into); the package-visible ctor takes a `LiveValueStore` for unit tests. Two constructors on a `@RestController` are fine — Spring uses the single `public` one. Do NOT make the test ctor public.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.ValuesStreamControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/ValuesStreamController.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/ValuesStreamControllerTest.java
git commit -m "feat(api): IS-051 values stream endpoint with snapshot-on-connect

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Runtime-state snapshot on the runtime stream

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/ProjectSources.java` (functional port)
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/DomainProjectSources.java` (`@Component` impl)
- Create: `api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshot.java`
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStreamController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshotTest.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStreamControllerTest.java`

**Interfaces:**
- Consumes: `RuntimeController.state(String)` (`com.ainclusive.iotsim.platform.runtime`); `DataSourceService.list(String)` → `List<DataSource>` with `id()` (`com.ainclusive.iotsim.domain.datasource`), used only inside `DomainProjectSources`; `LiveStreamSubscriptions` 3-arg; `StreamKey.runtime`.
- Produces:
  - `@FunctionalInterface interface ProjectSources { List<String> idsOf(String projectId); }` — narrow seam so tests need no Mockito (the `api` test classpath has only JUnit + AssertJ).
  - `@Component class DomainProjectSources implements ProjectSources` — `idsOf(pid)` = `dataSources.list(pid).stream().map(DataSource::id).toList()`.
  - `@Component class RuntimeStateSnapshot(RuntimeController runtimeController, ProjectSources sources)` with `List<LiveEvent> initialFor(String projectId)` returning a single `runtime-state` `LiveEvent` (NO_SEQ) carrying `List<SourceRuntimeState>` (nested `record SourceRuntimeState(String dataSourceId, String state)`).
  - `RuntimeStreamController` now injects `RuntimeStateSnapshot`; on a fresh connect (no `Last-Event-ID`) passes `snapshot.initialFor(projectId)`, otherwise `List.of()`.

- [ ] **Step 1: Write the failing tests**

`RuntimeStateSnapshotTest.java` (Mockito-free — fake the `RuntimeController` interface by hand and the `ProjectSources` port with a lambda):
```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class RuntimeStateSnapshotTest {

    private static RuntimeController controllerWhere(String runningId) {
        return new RuntimeController() {
            public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
            public String stop(String id) { return "STOPPED"; }
            public String state(String id) { return id.equals(runningId) ? "RUNNING" : "STOPPED"; }
            public long applyValues(String id, List<NeutralValue> v) { return 0; }
        };
    }

    @Test
    void buildsStatePerSourceInProject() {
        ProjectSources sources = pid -> List.of("d1", "d2");
        RuntimeStateSnapshot snapshot = new RuntimeStateSnapshot(controllerWhere("d1"), sources);

        List<LiveEvent> initial = snapshot.initialFor("p1");

        assertThat(initial).singleElement().satisfies(ev -> {
            assertThat(ev.type()).isEqualTo("runtime-state");
            assertThat(ev.hasSeq()).isFalse();
            assertThat((List<?>) ev.data())
                    .extracting("dataSourceId", "state")
                    .containsExactlyInAnyOrder(
                            Tuple.tuple("d1", "RUNNING"),
                            Tuple.tuple("d2", "STOPPED"));
        });
    }
}
```

NOTE: confirm `RuntimeStartSpec`'s exact package/name from `RuntimeController.java`'s `start` signature; adjust the import if it differs.

`RuntimeStreamControllerTest.java` (new; mirrors the IS-046 controller-test style with a `RecordingSubscriptions`):
```java
package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RuntimeStreamControllerTest {

    record Sub(StreamKey key, String lastEventId, List<LiveEvent> initial) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId) {
            return subscribe(key, lastEventId, List.of());
        }
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial) {
            calls.add(new Sub(key, lastEventId, initial));
            return new SseEmitter(0L);
        }
    }

    // Real RuntimeStateSnapshot with hand fakes (RuntimeController iface + ProjectSources lambda).
    private static RuntimeStreamController controller(RecordingSubscriptions subs) {
        com.ainclusive.iotsim.platform.runtime.RuntimeController rc =
                new com.ainclusive.iotsim.platform.runtime.RuntimeController() {
                    public String start(String id, com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec s) { return "RUNNING"; }
                    public String stop(String id) { return "STOPPED"; }
                    public String state(String id) { return "RUNNING"; }
                    public long applyValues(String id, List<com.ainclusive.iotsim.protocolmodel.NeutralValue> v) { return 0; }
                };
        ProjectSources sources = pid -> List.of("d1");
        return new RuntimeStreamController(subs, new RuntimeStateSnapshot(rc, sources));
    }

    @Test
    void freshConnectGetsRuntimeStateSnapshot() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        controller(subs).streamRuntime("p1", null);
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.key()).isEqualTo(StreamKey.runtime("p1"));
            assertThat(c.lastEventId()).isNull();
            assertThat(c.initial()).singleElement().satisfies(ev ->
                    assertThat(ev.type()).isEqualTo("runtime-state"));
        });
    }

    @Test
    void reconnectSkipsSnapshotAndKeepsLastEventId() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        controller(subs).streamRuntime("p1", "42");
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.lastEventId()).isEqualTo("42");
            assertThat(c.initial()).isEmpty();
        });
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.RuntimeStateSnapshotTest" "com.ainclusive.iotsim.api.stream.RuntimeStreamControllerTest"`
Expected: FAIL — `ProjectSources` / `RuntimeStateSnapshot` / the new controller ctor do not exist.

- [ ] **Step 3: Write minimal implementation**

`ProjectSources.java`:
```java
package com.ainclusive.iotsim.api.stream;

import java.util.List;

/** Lists the data-source ids of a project — narrow seam for the runtime-state snapshot. */
@FunctionalInterface
public interface ProjectSources {
    List<String> idsOf(String projectId);
}
```

`DomainProjectSources.java`:
```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link ProjectSources} over the domain {@code DataSourceService}. */
@Component
public final class DomainProjectSources implements ProjectSources {

    private final DataSourceService dataSources;

    public DomainProjectSources(DataSourceService dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public List<String> idsOf(String projectId) {
        return dataSources.list(projectId).stream().map(DataSource::id).toList();
    }
}
```

`RuntimeStateSnapshot.java`:
```java
package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the current per-source {@code runtime-state} snapshot sent on connect to the
 * runtime stream (IS-051): for each data source in the project, its
 * {@link RuntimeController#state} (RUNNING/STOPPED/STARTING/ERROR/STALE).
 */
@Component
public class RuntimeStateSnapshot {

    /** One source's current runtime state. */
    public record SourceRuntimeState(String dataSourceId, String state) {}

    private final RuntimeController runtimeController;
    private final ProjectSources sources;

    public RuntimeStateSnapshot(RuntimeController runtimeController, ProjectSources sources) {
        this.runtimeController = runtimeController;
        this.sources = sources;
    }

    List<LiveEvent> initialFor(String projectId) {
        List<SourceRuntimeState> states = sources.idsOf(projectId).stream()
                .map(id -> new SourceRuntimeState(id, runtimeController.state(id)))
                .toList();
        return List.of(new LiveEvent(LiveEvent.NO_SEQ, "runtime-state", states, Instant.now()));
    }
}
```

`RuntimeStreamController.java` — inject the snapshot and use it on fresh connect:
```java
    private final LiveStreamSubscriptions subscriptions;
    private final RuntimeStateSnapshot runtimeState;

    public RuntimeStreamController(LiveStreamSubscriptions subscriptions, RuntimeStateSnapshot runtimeState) {
        this.subscriptions = subscriptions;
        this.runtimeState = runtimeState;
    }

    @GetMapping(value = "/api/v1/projects/{projectId}/stream/runtime",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRuntime(@PathVariable String projectId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        java.util.List<LiveEvent> initial =
                lastEventId == null ? runtimeState.initialFor(projectId) : java.util.List.of();
        return subscriptions.subscribe(StreamKey.runtime(projectId), lastEventId, initial);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :api:test --tests "com.ainclusive.iotsim.api.stream.RuntimeStateSnapshotTest" "com.ainclusive.iotsim.api.stream.RuntimeStreamControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/stream/ProjectSources.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/DomainProjectSources.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshot.java \
        api/src/main/java/com/ainclusive/iotsim/api/stream/RuntimeStreamController.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStateSnapshotTest.java \
        api/src/test/java/com/ainclusive/iotsim/api/stream/RuntimeStreamControllerTest.java
git commit -m "feat(api): IS-051 runtime-state snapshot on the runtime stream

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Supervisor value tee

**Files:**
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorValueTeeTest.java`

**Interfaces:**
- Consumes: `LiveValueListener` (Task 1), existing `applyValues(String, List<NeutralValue>)`.
- Produces: a 6-arg `Supervisor` constructor adding `LiveValueListener valueListener` (the existing 5-arg delegates with `LiveValueListener.NONE`); `applyValues` calls `valueListener.onValues(dataSourceId, values, Instant.now())` after the worker apply succeeds, guarded so a listener error never breaks apply.

- [ ] **Step 1: Write the failing test**

The tee must be observable without spawning a worker. Use the existing supervisor test harness pattern: a fake `WorkerLauncher` whose worker accepts `applyValues`. If that harness is heavy, prefer a focused test that drives `applyValues` against a started fake worker and asserts the listener saw the values. Reference the existing supervisor tests for the fake-launcher pattern before writing.

```java
package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.LiveValueListener;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class SupervisorValueTeeTest {

    @Test
    void applyValuesTeesToTheListener() {
        List<List<NeutralValue>> seen = new CopyOnWriteArrayList<>();
        LiveValueListener listener = (id, values, at) -> seen.add(values);
        // Build a Supervisor with the 6-arg ctor and a fake launcher/worker, start a
        // data source, then applyValues. Use the SAME fake-launcher harness the other
        // Supervisor tests use (see e.g. SupervisorTest / a FakeWorker in this package).
        // ... harness setup ...
        // supervisor.start("d1", spec); supervisor.applyValues("d1", List.of(
        //         NeutralValue.good("n1", Instant.EPOCH, 1)));
        // assertThat(seen).singleElement().extracting(v -> v.get(0).nodeId()).isEqualTo("n1");
    }

    @Test
    void listenerErrorDoesNotBreakApply() {
        // listener throws; applyValues must still return the worker's applied count.
    }
}
```

NOTE: this task REQUIRES reading the existing supervisor tests (`runtime-supervisor/src/test/.../supervisor/*Test.java`) to reuse their fake `WorkerLauncher`/worker harness for starting a source and applying values. Fill the two test bodies using that harness — do not invent a new spawning mechanism. The asserted behaviors: (1) `applyValues` invokes the `LiveValueListener` with the applied `NeutralValue`s; (2) a throwing listener does not propagate out of `applyValues` (the worker's count is still returned). If you cannot drive `applyValues` without a real worker, report BLOCKED with what the harness needs.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorValueTeeTest"`
Expected: FAIL — 6-arg ctor / tee do not exist.

- [ ] **Step 3: Write minimal implementation**

In `Supervisor.java`:
1. Add the field beside the other listeners:
```java
    private final LiveValueListener valueListener;
```
2. Add `LiveValueListener valueListener` as the 6th parameter of the existing 5-arg "primary" constructor (the one at the `(launcher, restartPolicy, healthPolicy, clientActivityListener, runtimeActivityListener)` signature that assigns the fields and builds the scheduler), and assign it:
```java
        this.valueListener = valueListener == null ? LiveValueListener.NONE : valueListener;
```
   Then add a 5-arg overload that delegates:
```java
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener) {
        this(launcher, restartPolicy, healthPolicy, clientActivityListener,
                runtimeActivityListener, LiveValueListener.NONE);
    }
```
   (All existing shorter constructors keep calling the 5-arg, which now delegates to the 6-arg.)
3. In `applyValues`, tee after the apply:
```java
    @Override
    public long applyValues(String dataSourceId, List<NeutralValue> values) {
        ManagedWorker worker = running.get(dataSourceId);
        if (worker == null) {
            throw new IllegalStateException("data source is not running: " + dataSourceId);
        }
        List<Value> protoValues = values.stream().map(Supervisor::toProto).toList();
        long applied = worker.applyValues(protoValues);
        try {
            valueListener.onValues(dataSourceId, values, Instant.now());
        } catch (RuntimeException e) {
            // Best-effort live observation must never break value application.
        }
        return applied;
    }
```
   Add `import com.ainclusive.iotsim.platform.runtime.LiveValueListener;` (`Instant` is already imported).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :runtime-supervisor:test --tests "com.ainclusive.iotsim.supervisor.SupervisorValueTeeTest"`
Expected: PASS. Also run the whole supervisor suite to confirm the ctor change broke nothing: `./gradlew :runtime-supervisor:test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorValueTeeTest.java
git commit -m "feat(supervisor): IS-051 tee served values to LiveValueListener

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Wire LiveValuesHub into the supervisor (RuntimeConfig)

**Files:**
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java`

**Interfaces:**
- Consumes: `LiveValuesHub` (Task 4, an api `@Component` → autowired), the 6-arg `Supervisor` ctor (Task 8).
- Produces: the `runtimeController` bean passes `liveValuesHub` as the supervisor's `valueListener`.

`@Bean` glue (no new test; behavior covered by Tasks 4/8 and the Task 10 e2e). Verify with a green `./gradlew build`.

- [ ] **Step 1: Edit `runtimeController`**

Add `LiveValuesHub liveValuesHub` to the method signature and pass it as the 6th `Supervisor` arg:
```java
    @Bean
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents, ObjectMapper json,
            ExecutorService runtimeEventExecutor, LiveEventHub liveEventHub,
            LiveValuesHub liveValuesHub) {
        if (props.isSupervisorMode()) {
            RuntimeActivityListener persister = new PersistingRuntimeActivityListener(
                    dataSources, runtimeEvents, json, runtimeEventExecutor);
            RuntimeActivityListener runtimeListener =
                    new CompositeRuntimeActivityListener(persister, liveEventHub);
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, liveEventHub, runtimeListener, liveValuesHub);
        }
        return new InMemoryRuntimeController();
    }
```
Add `import com.ainclusive.iotsim.api.stream.LiveValuesHub;`.

- [ ] **Step 2: Compile + module test**

Run: `./gradlew :app:compileJava` → BUILD SUCCESSFUL.
Run: `./gradlew :app:test` → PASS (no regressions; `ApplicationSmokeIT` still boots the full context — `LiveValuesHub`/`RuntimeStateSnapshot`/`ValuesStreamController` must all autowire).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java
git commit -m "feat(app): IS-051 wire LiveValuesHub as supervisor value listener

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: End-to-end values stream test (app, MockMvc async)

**Files:**
- Test: `app/src/test/java/com/ainclusive/iotsim/app/stream/ValuesStreamEndToEndTest.java`

**Interfaces:**
- Consumes: `ValuesStreamController` (Task 6), `LiveStreamRegistry` (public test ctor — inline sender), `LiveValuesHub` (test ctor — no scheduler, manual `flushTick`), `LiveValueStore`, `StreamKey.values`.

This proves the real servlet path: snapshot-on-connect + a flushed delta arrive as framed SSE. Mirror `RuntimeStreamEndToEndTest` (IS-046): `@WebMvcTest(ValuesStreamController.class, excludeAutoConfiguration=SecurityAutoConfiguration.class)` + an `@Import` config; flush the async body via `registry.close()` then `asyncDispatch`.

- [ ] **Step 1: Write the test**

```java
package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.stream.LiveStreamRegistry;
import com.ainclusive.iotsim.api.stream.LiveValueStore;
import com.ainclusive.iotsim.api.stream.LiveValuesHub;
import com.ainclusive.iotsim.api.stream.StreamKey;
import com.ainclusive.iotsim.api.stream.ValuesStreamController;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ValuesStreamController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ValuesStreamEndToEndTest.TestBeans.class)
class ValuesStreamEndToEndTest {

    static class TestBeans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean LiveStreamRegistry liveStreamRegistry(ObjectMapper json) {
            return new LiveStreamRegistry(json, 256, 256, Runnable::run); // inline sender
        }
        @Bean LiveValueStore liveValueStore() { return new LiveValueStore(); }
        @Bean LiveValuesHub liveValuesHub(LiveStreamRegistry registry, LiveValueStore store) {
            return new LiveValuesHub(registry, store); // test ctor: no scheduler
        }
    }

    @Autowired MockMvc mvc;
    @Autowired LiveStreamRegistry registry;
    @Autowired LiveValueStore store;
    @Autowired LiveValuesHub hub;

    @Test
    void streamsSnapshotThenDelta() throws Exception {
        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 1))); // seeds snapshot

        MvcResult result = mvc.perform(get("/api/v1/data-sources/d1/stream/values"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 2))); // change
        hub.flushTick(); // publish the "values" delta

        registry.close(); // completes the emitter so the async body finalizes
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:values-snapshot");
        assertThat(body).contains("event:values");
        assertThat(body).contains("\"nodeId\":\"n1\"");
    }
}
```

NOTE: `LiveValuesHub`'s test ctor is package-visible (`com.ainclusive.iotsim.api.stream`) — this test is in `com.ainclusive.iotsim.app.stream`. If it cannot access the package-visible ctor, mirror the IS-046 resolution: make the `LiveValuesHub(LiveEventPublisher, LiveValueStore)` test ctor **public** (a deliberate test/wiring seam) and keep `@Autowired` on the production ctor. The `ValuesStreamController` test-ctor `(LiveStreamSubscriptions, LiveValueStore)` is also package-visible; here the controller is built by `@WebMvcTest` via its public `(LiveStreamSubscriptions, LiveValuesHub)` ctor, so no change is needed for the controller. Adapt SSE-framing assertions to the real bytes if they differ (see IS-046 — observed form was `event:NAME\ndata:{json}\n...`); preserve the intent (snapshot + delta + payload present). Report any visibility change or assertion adaptation.

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.stream.ValuesStreamEndToEndTest"`
Expected: PASS — body contains the snapshot event, the delta event, and the JSON payload.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/ainclusive/iotsim/app/stream/ValuesStreamEndToEndTest.java
git commit -m "test(app): IS-051 end-to-end values stream (snapshot + delta)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Full build, catalog checkbox, open PR

- [ ] **Step 1: Forced full build** (defeat cache so `ApplicationSmokeIT` actually re-runs)

Run: `./gradlew build --rerun-tasks`
Expected: BUILD SUCCESSFUL — all modules compile, Spotless/Checkstyle clean, every test (api/platform/runtime-supervisor/app, incl. `ApplicationSmokeIT`) passes.

- [ ] **Step 2: Fix any Spotless/Checkstyle issues**

If Spotless fails: `./gradlew spotlessApply` then re-run the build. Fix Checkstyle (import order, Javadoc, line length) per `config/checkstyle/checkstyle.xml`.

- [ ] **Step 3: Open the PR via the project skill**

Invoke `/open-pr` from the feature branch: it runs the Definition-of-Done checks, flips the `backend-specs/TASKS.md` IS-051 checkbox in the same PR (CI catalog-sync gate), creates the PR with `Implements: IS-051` + `Closes #72`, arms squash auto-merge, and moves the board to In review.

---

## Self-Review

**1. Spec coverage:**
- Values stream `…/stream/values` → Tasks 1 (key), 2 (payload), 3 (store), 4 (hub), 6 (controller), 8 (tee), 9 (wiring), 10 (e2e). ✔
- Conflate latest-per-node + ~250 ms flush → Tasks 3 + 4. ✔
- Snapshot-on-connect (values) → Tasks 5 (seam) + 6. ✔
- Runtime-state snapshot on `…/stream/runtime` → Tasks 5 + 7. ✔
- Reconnect rule (VALUES ignore Last-Event-ID; RUNTIME snapshot only fresh) → Tasks 6 + 7. ✔
- Tee in `Supervisor.applyValues`, non-blocking, guarded → Task 8. ✔
- Reuse IS-046 transport, no new deps → all api tasks extend existing classes; no dependency edits. ✔
- Multi-ctor `@Component` `@Autowired` (LiveValuesHub) → Task 4. ✔
- Out of scope (health detail IS-053, overview IS-054, history IS-055, worker RPC) → not implemented. ✔

**2. Placeholder scan:** No "TBD/TODO". Three NOTEs (DataSource ctor confirm in Task 7; supervisor fake-launcher harness reuse in Task 8; SSE-framing/visibility adaptation in Tasks 5/10) are concrete verification instructions with explicit fallbacks, not deferred work.

**3. Type consistency:** `StreamKey.values`/`Type.VALUES`; `StreamValue(nodeId,value,quality,qualityReason,sourceTime)`+`from`; `LiveValueStore.record/snapshot/drainChanged/dirtySources`; `LiveValueListener.onValues(String,List<NeutralValue>,Instant)`+`NONE`; `LiveValuesHub` ctors + `store()`/`flushTick()`/`onValues`/`close`; `LiveStreamSubscriptions.subscribe(key,lastEventId,initial)`; `LiveStream.addSubscriber(sub,lastEventId,initial)`; `RuntimeStateSnapshot.initialFor` + `SourceRuntimeState`; `Supervisor` 6-arg ctor + tee; `RuntimeConfig` param. Consistent across producing/consuming tasks. ✔
