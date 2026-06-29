# IS-048 RuntimeEvents Stream Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the worker→supervisor `RuntimeEvents` gRPC stream end-to-end and persist its events to `runtime_events`, so a running source's start/stop/errors land as durable runtime-event history.

**Architecture:** Mirror the existing IS-047 `ClientEvents` vertical across four modules — `worker-opcua` produces events on a fan-out hub, `runtime-supervisor` consumes the stream and tags each event with its `dataSourceId`, `platform` carries the protocol-neutral domain event + listener, and `app` resolves the project and appends to the IS-049 repository. The one deviation from the ClientEvents pattern: the supervisor subscribes to the RuntimeEvents stream **before** `Start` (not at RUNNING), because the worker emits `SOURCE_START` the instant its server begins listening and the hub does not buffer.

**Tech Stack:** Java 25, gRPC (server-streaming), Eclipse Milo OPC UA, jOOQ/Postgres, Spring Boot, JUnit 5 + AssertJ + Mockito.

## Global Constraints

- Run `./gradlew build` and confirm green before reporting any task done (it runs Spotless, Checkstyle, ArchUnit module-boundary checks, JaCoCo, and Testcontainers ITs). — from [[always-compile-and-test]]
- No new dependencies, no proto changes (`RuntimeEvents` RPC and `RuntimeEvent` message already exist in `worker-contract/src/main/proto/iotsim/worker/v1/protocol_data_source.proto`).
- Imports: explicit, ordered, no wildcards (Spotless/Checkstyle enforce this). Public types/methods need Javadoc.
- Module boundaries: `runtime-supervisor` may depend only on `worker-contract` + `platform` (NOT `persistence`); persistence wiring lives in `app`. The domain event/listener live in `platform`.
- `RuntimeEvent.type` is a free string end-to-end (do NOT introduce a closed enum) — the proto chose a string so future types (FAULT_STATE_CHANGE, replay, scenario) land without supervisor changes.
- Commit after each task with a `feat(...)` / `test(...)` message; do NOT flip the `TASKS.md` checkbox here — `/open-pr` owns that (catalog-sync gate).

---

### Task 1: Platform domain — `RuntimeActivityEvent` + `RuntimeActivityListener`

The protocol-neutral, supervisor-side projection of a worker `RuntimeEvent`, plus the sink the app implements. Copies the `ClientActivityEvent` / `ClientActivityListener` pattern in the same package.

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEvent.java`
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityListener.java`
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEventTest.java`

**Interfaces:**
- Produces:
  - `record RuntimeActivityEvent(String dataSourceId, String type, java.time.Instant at, String detail)` — `dataSourceId`, `type`, `at` non-null; `type` non-blank; `detail` nullable (kept as-is).
  - `interface RuntimeActivityListener { RuntimeActivityListener NONE = e -> {}; void onRuntimeActivity(RuntimeActivityEvent event); }`

- [ ] **Step 1: Write the failing test**

Create `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEventTest.java`:

```java
package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuntimeActivityEventTest {

    @Test
    void keepsFieldsIncludingNullDetail() {
        RuntimeActivityEvent event =
                new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null);

        assertThat(event.dataSourceId()).isEqualTo("ds1");
        assertThat(event.type()).isEqualTo("SOURCE_START");
        assertThat(event.at()).isEqualTo(Instant.ofEpochSecond(5));
        assertThat(event.detail()).isNull();
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(
                        () -> new RuntimeActivityEvent("ds1", "  ", Instant.ofEpochSecond(5), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new RuntimeActivityEvent(null, "T", Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RuntimeActivityEvent("ds1", "T", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform:test --tests '*RuntimeActivityEventTest'`
Expected: FAIL — compilation error, `RuntimeActivityEvent` does not exist.

- [ ] **Step 3: Write the implementation**

Create `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEvent.java`:

```java
package com.ainclusive.iotsim.platform.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * A protocol-neutral runtime event observed at a running data source: the source
 * started or stopped serving, or hit a runtime error. This is the supervisor-side
 * projection of the worker's {@code RuntimeEvents} stream (IS-048), kept free of
 * wire/proto types so the domain can consume it without depending on the IPC
 * contract.
 *
 * <p>Append-only — it feeds the runtime-event history (IS-055). {@code type} is a
 * free string (e.g. {@code SOURCE_START}, {@code SOURCE_STOP}, {@code ERROR}) so
 * new event kinds need no model change; {@code detail} is optional human-readable
 * context; {@code at} is when the worker observed the event.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public record RuntimeActivityEvent(String dataSourceId, String type, Instant at, String detail) {

    public RuntimeActivityEvent {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }
}
```

Create `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityListener.java`:

```java
package com.ainclusive.iotsim.platform.runtime;

/**
 * Sink for {@link RuntimeActivityEvent}s the supervisor receives from running
 * workers (IS-048). The app supplies an implementation to persist them as
 * runtime-event history (IS-049/IS-055); the supervisor calls it as events arrive
 * on each worker's {@code RuntimeEvents} stream.
 *
 * <p>Called on an IPC delivery thread, so implementations must be cheap and
 * non-blocking — hand off to a queue/executor for any real work. {@link #NONE} is
 * the default when no observer is wired.
 */
@FunctionalInterface
public interface RuntimeActivityListener {

    /** Discards every event; the default when nothing observes runtime activity. */
    RuntimeActivityListener NONE = event -> {};

    void onRuntimeActivity(RuntimeActivityEvent event);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :platform:test --tests '*RuntimeActivityEventTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEvent.java \
        platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityListener.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/RuntimeActivityEventTest.java
git commit -m "feat(platform): IS-048 RuntimeActivityEvent + listener"
```

---

### Task 2: Worker emits RuntimeEvents (`worker-opcua` vertical)

The worker fans `RuntimeEvent`s out to open `RuntimeEvents` streams via a new hub (copy of `ClientEventHub`), emits `SOURCE_START` when its Milo server starts listening, `SOURCE_STOP` before graceful shutdown, and `ERROR` when a value fails to apply.

**Files:**
- Create: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/RuntimeEventHub.java`
- Modify: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaProtocolService.java`
- Modify: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerRuntime.java`
- Test: `workers/worker-opcua/src/test/java/com/ainclusive/iotsim/worker/opcua/OpcUaRuntimeEventsIT.java`

**Interfaces:**
- Consumes: nothing from other tasks (proto types `RuntimeEvent`, `StreamRequest` are generated already).
- Produces (used by the IT and indirectly by Task 3):
  - `OpcUaProtocolService.runtimeEvents(StreamRequest, StreamObserver<RuntimeEvent>)` — registers the supervisor's observer with the hub.
  - `OpcUaProtocolService.openRuntimeEventStreams()` → `int` — open-stream count for the await-subscribed test pattern.
  - Worker emits `RuntimeEvent` with `type` ∈ {`SOURCE_START`, `SOURCE_STOP`, `ERROR`}.

- [ ] **Step 1: Write the failing test**

Create `workers/worker-opcua/src/test/java/com/ainclusive/iotsim/worker/opcua/OpcUaRuntimeEventsIT.java`:

```java
package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the worker's {@code RuntimeEvents} stream (IS-048): with the
 * full gRPC worker server running, starting the server surfaces SOURCE_START, a
 * value that cannot be applied surfaces ERROR, and stopping surfaces SOURCE_STOP.
 * Exercises the whole worker vertical — service, {@link RuntimeEventHub}, and the
 * server runtime. The stream is subscribed before Start because events are not
 * buffered.
 */
class OpcUaRuntimeEventsIT {

    @Test
    void serverLifecycleAndApplyFailureSurfaceAsRuntimeEvents() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer server = new WorkerServer(0, service).start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.port()).usePlaintext().build();
        int opcPort = freePort();
        BlockingQueue<RuntimeEvent> events = new LinkedBlockingQueue<>();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            blocking.configure(ConfigureRequest.newBuilder()
                    .setListenPort(opcPort)
                    .setSchema(Schema.newBuilder().setVersion(1).addNodes(SchemaNodeMsg.newBuilder()
                            .setNodeId("temp").setPath("Temperature").setName("Temperature")
                            .setKind("VARIABLE").setDataType("FLOAT64")))
                    .build());

            // Subscribe BEFORE start so SOURCE_START (emitted when the server begins
            // listening) is not missed — events are not buffered.
            ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
            async.runtimeEvents(StreamRequest.getDefaultInstance(), collectInto(events));
            awaitUntil(() -> service.openRuntimeEventStreams() > 0);

            blocking.start(StartRequest.getDefaultInstance());

            RuntimeEvent started = events.poll(10, TimeUnit.SECONDS);
            assertThat(started).isNotNull();
            assertThat(started.getType()).isEqualTo("SOURCE_START");
            assertThat(started.getAtMicros()).isPositive();

            // A value too short to decode as FLOAT64 fails projection -> ERROR.
            StreamObserver<Ack> ackObserver = noopAckObserver();
            StreamObserver<ValueBatch> values = async.applyValues(ackObserver);
            values.onNext(ValueBatch.newBuilder()
                    .addValues(Value.newBuilder()
                            .setNodeId("temp")
                            .setValueEnc(ByteString.copyFrom(new byte[] {1, 2})))
                    .build());
            values.onCompleted();

            RuntimeEvent error = events.poll(10, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.getType()).isEqualTo("ERROR");
            assertThat(error.getDetail()).contains("temp");

            blocking.stop(StopRequest.getDefaultInstance());

            RuntimeEvent stopped = events.poll(10, TimeUnit.SECONDS);
            assertThat(stopped).isNotNull();
            assertThat(stopped.getType()).isEqualTo("SOURCE_STOP");
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.stop();
        }
    }

    private static StreamObserver<RuntimeEvent> collectInto(BlockingQueue<RuntimeEvent> events) {
        return new StreamObserver<>() {
            @Override
            public void onNext(RuntimeEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable t) {
                // stream cancelled on teardown; nothing to do
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private static StreamObserver<Ack> noopAckObserver() {
        return new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaRuntimeEventsIT'`
Expected: FAIL — `service.openRuntimeEventStreams()` does not exist (compilation error), and no events arrive.

- [ ] **Step 3a: Create the hub**

Create `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/RuntimeEventHub.java`:

```java
package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans worker-side runtime events out to every open {@code RuntimeEvents} stream
 * (IS-048). The server runtime publishes start/stop here and the service publishes
 * errors; the gRPC service registers one observer per supervisor subscription.
 *
 * <p>Events are point-in-time and not buffered — one published while no stream is
 * open is dropped. The supervisor opens the stream before Start, so SOURCE_START
 * is captured. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
final class RuntimeEventHub {

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /** Registers a supervisor's stream; it is removed when the supervisor cancels it. */
    void register(ServerCallStreamObserver<RuntimeEvent> observer) {
        Subscriber subscriber = new Subscriber(observer);
        observer.setOnCancelHandler(() -> subscribers.remove(subscriber));
        subscribers.add(subscriber);
    }

    /**
     * Publishes an event to every open stream. Callbacks can fire from different
     * threads, so each subscriber's {@code onNext} is serialized on a lock the hub
     * owns; a cancelled or failing stream is dropped.
     */
    void emit(RuntimeEvent event) {
        for (Subscriber subscriber : subscribers) {
            if (!subscriber.deliver(event)) {
                subscribers.remove(subscriber);
            }
        }
    }

    /** Number of open supervisor streams (introspection/tests). */
    int openStreamCount() {
        return subscribers.size();
    }

    /** One supervisor stream plus a hub-owned lock that serializes delivery to it. */
    private static final class Subscriber {

        private final ServerCallStreamObserver<RuntimeEvent> observer;
        private final Object lock = new Object();

        Subscriber(ServerCallStreamObserver<RuntimeEvent> observer) {
            this.observer = observer;
        }

        /** Delivers one event under the hub-owned lock; returns false if the stream is gone. */
        boolean deliver(RuntimeEvent event) {
            synchronized (lock) {
                if (observer.isCancelled()) {
                    return false;
                }
                try {
                    observer.onNext(event);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }
    }
}
```

- [ ] **Step 3b: Emit SOURCE_START / SOURCE_STOP from the server runtime**

In `OpcUaServerRuntime.java`, add the import (with the other `workercontract.v1` import):

```java
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
```

Add a field next to `endpointUrl`:

```java
    private final java.util.function.Consumer<RuntimeEvent> runtimeEventSink;
```

Replace the two existing constructors (the 2-arg and 3-arg, lines 37–41) with these three, so the runtime sink is optional and defaults to a no-op:

```java
    OpcUaServerRuntime(int port, List<VarDef> variables) {
        this(port, variables, event -> {}, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink) {
        this(port, variables, clientEventSink, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
```

Inside that 4-arg constructor body, store the sink (add right after the `try {` opens, or alongside the other field assignments at the end of the try block — place it as the first statement in the constructor body, before `try`):

```java
        this.runtimeEventSink = runtimeEventSink;
```

Update `start()` and `stop()`:

```java
    void start() {
        namespace.startup();
        await(server.startup());
        // Server is now listening: surface SOURCE_START on the runtime stream (IS-048).
        runtimeEventSink.accept(runtimeEvent("SOURCE_START", ""));
    }

    void stop() {
        // Emit before tearing down so the supervisor sees SOURCE_STOP while the stream
        // is still open (best-effort on teardown).
        runtimeEventSink.accept(runtimeEvent("SOURCE_STOP", ""));
        await(server.shutdown());
        namespace.shutdown();
    }
```

Add a helper next to `clientEvent(...)`:

```java
    /** Builds a neutral runtime event with the current wall-clock time in micros. */
    private static RuntimeEvent runtimeEvent(String type, String detail) {
        return RuntimeEvent.newBuilder()
                .setType(type)
                .setAtMicros(System.currentTimeMillis() * 1_000L)
                .setDetail(detail == null ? "" : detail)
                .build();
    }
```

- [ ] **Step 3c: Wire the hub into the service and emit ERROR on apply failure**

In `OpcUaProtocolService.java`, add the import (with the other `workercontract.v1` imports):

```java
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
```

Add the hub field next to `clientEventHub` (line 49):

```java
    private final RuntimeEventHub runtimeEventHub = new RuntimeEventHub();
```

Add an introspection accessor next to `openClientEventStreams()` (line 62):

```java
    /** Number of open supervisor {@code RuntimeEvents} streams (introspection/tests). */
    public int openRuntimeEventStreams() {
        return runtimeEventHub.openStreamCount();
    }
```

In `configure(...)`, pass the runtime sink to the server runtime — replace line 86:

```java
        serverRuntime.set(new OpcUaServerRuntime(
                request.getListenPort(), variables, clientEventHub::emit, runtimeEventHub::emit));
```

Add the RPC implementation next to `clientEvents(...)` (after line 179):

```java
    /**
     * Worker → supervisor runtime-event stream (IS-048): registers the supervisor's
     * observer with the {@link RuntimeEventHub} and leaves it open. The running server
     * publishes SOURCE_START/SOURCE_STOP and value-apply failures publish ERROR; the
     * stream ends when the supervisor cancels it.
     * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
     */
    @Override
    public void runtimeEvents(StreamRequest request, StreamObserver<RuntimeEvent> responseObserver) {
        runtimeEventHub.register((ServerCallStreamObserver<RuntimeEvent>) responseObserver);
    }
```

Replace `project(...)` (lines 238–251) so a per-value failure emits ERROR instead of propagating:

```java
    private void project(ValueBatch batch) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime == null) {
            return;
        }
        for (Value value : batch.getValuesList()) {
            String dataType = nodeDataTypes.get(value.getNodeId());
            if (dataType != null) {
                try {
                    Object decoded = ValueCodec.decode(
                            OpcUaTypes.codecKind(dataType), value.getValueEnc().toByteArray());
                    runtime.updateValue(value.getNodeId(), OpcUaTypes.toOpcUaValue(dataType, decoded));
                } catch (RuntimeException e) {
                    runtimeEventHub.emit(RuntimeEvent.newBuilder()
                            .setType("ERROR")
                            .setAtMicros(System.currentTimeMillis() * 1_000L)
                            .setDetail("failed to apply value for node " + value.getNodeId()
                                    + ": " + e.getMessage())
                            .build());
                }
            }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaRuntimeEventsIT'`
Expected: PASS. Also run the existing client-events IT to confirm no regression:
Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaClientEventsIT'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/RuntimeEventHub.java \
        workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaProtocolService.java \
        workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerRuntime.java \
        workers/worker-opcua/src/test/java/com/ainclusive/iotsim/worker/opcua/OpcUaRuntimeEventsIT.java
git commit -m "feat(worker-opcua): IS-048 emit SOURCE_START/STOP/ERROR on RuntimeEvents stream"
```

---

### Task 3: Supervisor consumes + tags RuntimeEvents

Add the IPC client method, subscribe to the stream **before** Start, map each `RuntimeEvent` to a `RuntimeActivityEvent` tagged with the data-source id, forward it to the listener, and cancel the stream on stop/exit/restart.

**Files:**
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/WorkerClient.java`
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java`
- Modify (test harness): `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/TestProtocolService.java`
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorRuntimeEventsTest.java`

**Interfaces:**
- Consumes: `RuntimeActivityEvent`, `RuntimeActivityListener` (Task 1); `OpcUaProtocolService.runtimeEvents` semantics (Task 2); generated `ProtocolDataSourceGrpc` async stub `runtimeEvents(...)`.
- Produces (used by Task 4):
  - `WorkerClient.runtimeEvents(Consumer<RuntimeEvent> onEvent, Consumer<Throwable> onError)` → `StreamHandle`.
  - `new Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, RuntimeActivityListener runtimeActivityListener)` constructor.
  - `new Supervisor(WorkerLauncher launcher, RuntimeActivityListener runtimeActivityListener)` constructor.

- [ ] **Step 1: Write the failing test**

First extend the in-process worker so it emits a runtime event and tracks cancellation. In `TestProtocolService.java`:

Add the import (with the other `workercontract.v1` imports):

```java
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
```

Add a latch field next to `clientEventsCancelled` (line 43):

```java
    private final CountDownLatch runtimeEventsCancelled = new CountDownLatch(1);
```

Add an await method next to `awaitClientEventsCancelled` (after line 69):

```java
    /** Waits until the supervisor cancels the runtime-events stream (stop()/teardown). */
    boolean awaitRuntimeEventsCancelled(long timeoutSeconds) throws InterruptedException {
        return runtimeEventsCancelled.await(timeoutSeconds, TimeUnit.SECONDS);
    }
```

Add the RPC implementation next to `clientEvents(...)` (after line 164):

```java
    @Override
    public void runtimeEvents(StreamRequest request, StreamObserver<RuntimeEvent> obs) {
        ServerCallStreamObserver<RuntimeEvent> server = (ServerCallStreamObserver<RuntimeEvent>) obs;
        server.setOnCancelHandler(runtimeEventsCancelled::countDown);
        // Emit one event the moment the supervisor subscribes, so a test can assert it
        // is forwarded (tagged with the data-source id) to the listener.
        server.onNext(RuntimeEvent.newBuilder()
                .setType("SOURCE_START")
                .setAtMicros(2_000_000L)
                .setDetail("started")
                .build());
        // Stream stays open until the supervisor cancels (stop()).
    }
```

Now create `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorRuntimeEventsTest.java`:

```java
package com.ainclusive.iotsim.supervisor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the supervisor consumes each worker's {@code RuntimeEvents} stream
 * (IS-048): it opens the stream before Start, forwards events to the listener
 * tagged with the data-source id, and cancels the stream on stop.
 */
class SupervisorRuntimeEventsTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private final BlockingQueue<RuntimeActivityEvent> events = new LinkedBlockingQueue<>();
    private Supervisor supervisor;

    private static RuntimeStartSpec spec() {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(), 0);
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    @Test
    void forwardsWorkerRuntimeEventsTaggedWithSource() throws Exception {
        supervisor = new Supervisor(launcher, events::add);

        supervisor.start("ds1", spec());

        RuntimeActivityEvent event = events.poll(5, SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.dataSourceId()).isEqualTo("ds1");
        assertThat(event.type()).isEqualTo("SOURCE_START");
        assertThat(event.detail()).isEqualTo("started");
        assertThat(event.at()).isEqualTo(Instant.ofEpochSecond(2));
    }

    @Test
    void stopCancelsTheRuntimeEventStream() throws Exception {
        supervisor = new Supervisor(launcher, events::add);
        supervisor.start("ds1", spec());
        // Wait for the first event so we know the stream is established before stopping.
        assertThat(events.poll(5, SECONDS)).isNotNull();

        supervisor.stop("ds1");

        assertThat(launcher.last().service().awaitRuntimeEventsCancelled(5)).isTrue();
    }

    @Test
    void defaultListenerIgnoresRuntimeEventsWithoutError() {
        // No listener wired: the stream is still opened but events are discarded.
        supervisor = new Supervisor(launcher);
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests '*SupervisorRuntimeEventsTest'`
Expected: FAIL — `new Supervisor(launcher, events::add)` where the queue is `RuntimeActivityEvent` won't resolve (no `RuntimeActivityListener` constructor), compilation error.

- [ ] **Step 3a: Add the IPC client method**

In `WorkerClient.java`, add the import (with the other `workercontract.v1` imports):

```java
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
```

Add this method right after `clientEvents(...)` (after line 201):

```java
    /**
     * Opens the worker → supervisor {@code RuntimeEvents} stream: the worker pushes a
     * {@link RuntimeEvent} for source start/stop and runtime errors until the returned
     * handle is cancelled (server-streaming, IS-048). {@code onEvent} is called per
     * event; {@code onError} on a non-cancel stream failure (a worker that does not
     * implement the stream fails here with {@code UNIMPLEMENTED} — the caller treats
     * that as "no runtime events", never fatal).
     * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
     */
    public StreamHandle runtimeEvents(Consumer<RuntimeEvent> onEvent, Consumer<Throwable> onError) {
        ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
        AtomicReference<ClientCallStreamObserver<StreamRequest>> requestStream = new AtomicReference<>();
        ClientResponseObserver<StreamRequest, RuntimeEvent> observer = new ClientResponseObserver<>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<StreamRequest> stream) {
                requestStream.set(stream);
            }

            @Override
            public void onNext(RuntimeEvent event) {
                onEvent.accept(event);
            }

            @Override
            public void onError(Throwable t) {
                if (onError != null) {
                    onError.accept(t);
                }
            }

            @Override
            public void onCompleted() {
                // server-streaming ends only via cancel; nothing to do
            }
        };
        async.runtimeEvents(StreamRequest.getDefaultInstance(), observer);
        return () -> {
            ClientCallStreamObserver<StreamRequest> stream = requestStream.get();
            if (stream != null) {
                stream.cancel("runtime events stopped", null);
            }
        };
    }
```

- [ ] **Step 3b: Supervisor — imports, field, constructors, mapping**

In `Supervisor.java`, add imports (with the other `platform.runtime` / `workercontract.v1` imports):

```java
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
```

Add the field next to `clientActivityListener` (line 90):

```java
    private final RuntimeActivityListener runtimeActivityListener;
```

Replace the constructor block (lines 95–130) so the canonical constructor takes both listeners and the existing ones delegate with `RuntimeActivityListener.NONE`, plus two new runtime-listener conveniences:

```java
    public Supervisor(WorkerLauncher launcher) {
        this(launcher, RestartPolicy.DEFAULT, HealthPolicy.DEFAULT,
                ClientActivityListener.NONE, RuntimeActivityListener.NONE);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy) {
        this(launcher, restartPolicy, HealthPolicy.DEFAULT,
                ClientActivityListener.NONE, RuntimeActivityListener.NONE);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy) {
        this(launcher, restartPolicy, healthPolicy,
                ClientActivityListener.NONE, RuntimeActivityListener.NONE);
    }

    /** Observes worker client-activity events (IS-047); pass {@link ClientActivityListener#NONE} to ignore them. */
    public Supervisor(WorkerLauncher launcher, ClientActivityListener clientActivityListener) {
        this(launcher, RestartPolicy.DEFAULT, HealthPolicy.DEFAULT,
                clientActivityListener, RuntimeActivityListener.NONE);
    }

    /** Observes worker runtime events (IS-048); pass {@link RuntimeActivityListener#NONE} to ignore them. */
    public Supervisor(WorkerLauncher launcher, RuntimeActivityListener runtimeActivityListener) {
        this(launcher, RestartPolicy.DEFAULT, HealthPolicy.DEFAULT,
                ClientActivityListener.NONE, runtimeActivityListener);
    }

    /** Observes worker runtime events (IS-048) with a custom restart policy. */
    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy,
            RuntimeActivityListener runtimeActivityListener) {
        this(launcher, restartPolicy, HealthPolicy.DEFAULT,
                ClientActivityListener.NONE, runtimeActivityListener);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy,
            ClientActivityListener clientActivityListener,
            RuntimeActivityListener runtimeActivityListener) {
        this.launcher = launcher;
        this.restartPolicy = restartPolicy;
        this.healthPolicy = healthPolicy;
        this.clientActivityListener = clientActivityListener == null
                ? ClientActivityListener.NONE : clientActivityListener;
        this.runtimeActivityListener = runtimeActivityListener == null
                ? RuntimeActivityListener.NONE : runtimeActivityListener;
        AtomicInteger seq = new AtomicInteger();
        // One pool serves restarts and the health-monitoring loop; sized so a probe
        // (bounded by HealthPolicy.probeTimeout) cannot starve a pending restart.
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "worker-supervisor-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        long periodMillis = healthPolicy.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(
                this::pollHealth, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }
```

Add the mapping method right after `toClientActivity(...)` (after line 379):

```java
    /**
     * Maps a wire {@link RuntimeEvent} to the neutral {@link RuntimeActivityEvent} for a
     * data source, or {@code null} for a blank type (skipped). {@code type} is passed
     * through verbatim so new worker event kinds need no supervisor change.
     */
    private static RuntimeActivityEvent toRuntimeActivity(String dataSourceId, RuntimeEvent event) {
        String type = event.getType();
        if (type == null || type.isBlank()) {
            return null;
        }
        long micros = event.getAtMicros();
        Instant at = Instant.ofEpochSecond(
                Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
        String detail = event.getDetail().isEmpty() ? null : event.getDetail();
        return new RuntimeActivityEvent(dataSourceId, type, at, detail);
    }
```

- [ ] **Step 3c: Supervisor — subscribe before Start, adopt, cancel both streams**

Replace the `Connection` record (lines 535–542) so it carries the runtime stream handle:

```java
    /** A live worker built by {@code connect()} but not yet adopted as the managed worker. */
    private record Connection(LaunchedWorker launched, WorkerClient client,
            WorkerClient.StreamHandle runtimeEvents) {

        void close() {
            if (runtimeEvents != null) {
                try {
                    runtimeEvents.cancel();
                } catch (RuntimeException ignored) {
                    // best effort; closing the channel also ends the stream
                }
            }
            closeQuietly(client);
            launched.close();
        }
    }
```

Add the field to `ManagedWorker` next to `clientEvents` (line 557):

```java
        private WorkerClient.StreamHandle runtimeEvents;
```

Replace `connect()` (lines 585–605) so it opens the runtime stream after Configure, before Start, and cleans it up on failure:

```java
        private Connection connect() {
            int controlPort = PortAllocator.freeLoopbackPort();
            LaunchedWorker newLaunched;
            try {
                newLaunched = launcher.launch(spec.protocol(), controlPort);
            } catch (Exception e) {
                throw new WorkerLaunchException("failed to launch " + spec.protocol() + " worker", e);
            }
            WorkerClient newClient = new WorkerClient("127.0.0.1", controlPort);
            WorkerClient.StreamHandle runtimeStream = null;
            try {
                awaitReady(newClient);
                newClient.configure(
                        toProtoSchema(spec.schemaVersion(), spec.schemaNodes()), spec.listenPort());
                // Subscribe to runtime events BEFORE start so SOURCE_START — emitted the
                // instant the worker's server begins listening — is not missed; the hub
                // does not buffer. UNIMPLEMENTED on an older worker is non-fatal.
                runtimeStream = newClient.runtimeEvents(
                        event -> {
                            RuntimeActivityEvent activity = toRuntimeActivity(dataSourceId, event);
                            if (activity != null) {
                                runtimeActivityListener.onRuntimeActivity(activity);
                            }
                        },
                        error -> {
                            // Stream end (incl. CANCELLED on stop, UNIMPLEMENTED on a worker
                            // without the stream) is non-fatal; the worker keeps running.
                        });
                newClient.start();
            } catch (RuntimeException e) {
                if (runtimeStream != null) {
                    try {
                        runtimeStream.cancel();
                    } catch (RuntimeException ignored) {
                        // best effort; we are tearing down
                    }
                }
                newClient.close();
                newLaunched.close();
                throw e;
            }
            return new Connection(newLaunched, newClient, runtimeStream);
        }
```

In `install(...)`, adopt the runtime stream — add this line right after `this.client = conn.client();` (line 610):

```java
            this.runtimeEvents = conn.runtimeEvents();
```

Replace `cancelClientEvents()` (lines 635–646) with a method that cancels both streams:

```java
        /** Caller holds the monitor. Cancels the client- and runtime-event streams, if any. */
        private void cancelStreams() {
            WorkerClient.StreamHandle ce = clientEvents;
            clientEvents = null;
            if (ce != null) {
                try {
                    ce.cancel();
                } catch (RuntimeException ignored) {
                    // best effort; closing the channel also ends the stream
                }
            }
            WorkerClient.StreamHandle re = runtimeEvents;
            runtimeEvents = null;
            if (re != null) {
                try {
                    re.cancel();
                } catch (RuntimeException ignored) {
                    // best effort; closing the channel also ends the stream
                }
            }
        }
```

Update the two call sites of the old method:
- In `onWorkerExit(...)` (line 654): `cancelClientEvents();` → `cancelStreams();`
- In `stop()` (line 720): `cancelClientEvents();` → `cancelStreams();`

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :runtime-supervisor:test --tests '*SupervisorRuntimeEventsTest'`
Expected: PASS (3 tests). Then run the whole supervisor suite to confirm no regression (client-events, restart, health):
Run: `./gradlew :runtime-supervisor:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/WorkerClient.java \
        runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/TestProtocolService.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/SupervisorRuntimeEventsTest.java
git commit -m "feat(supervisor): IS-048 consume + tag RuntimeEvents stream, subscribe before Start"
```

---

### Task 4: App persists RuntimeEvents to `runtime_events`

The app supplies a `RuntimeActivityListener` that resolves the owning project from the data source and appends an append-only row, off the IPC thread, and wires it into the supervisor bean.

**Files:**
- Create: `app/src/main/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListener.java`
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListenerTest.java`

**Interfaces:**
- Consumes: `RuntimeActivityEvent` / `RuntimeActivityListener` (Task 1); `new Supervisor(WorkerLauncher, RestartPolicy, RuntimeActivityListener)` (Task 3); `DataSourceRepository.findById(String) -> Optional<DataSourceRow>`, `DataSourceRow.projectId()`, `RuntimeEventRepository.append(String projectId, String dataSourceId, String runId, String type, OffsetDateTime at, String payloadJson)` (existing).
- Produces: `class PersistingRuntimeActivityListener implements RuntimeActivityListener` with constructor `(DataSourceRepository, RuntimeEventRepository, ObjectMapper, Executor)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListenerTest.java`:

```java
package com.ainclusive.iotsim.app.runtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersistingRuntimeActivityListenerTest {

    private final DataSourceRepository sources = mock(DataSourceRepository.class);
    private final RuntimeEventRepository events = mock(RuntimeEventRepository.class);
    private final PersistingRuntimeActivityListener listener = new PersistingRuntimeActivityListener(
            sources, events, new ObjectMapper(), Runnable::run);

    private static DataSourceRow row(String id, String projectId) {
        return new DataSourceRow(id, projectId, "name", "OPC_UA", "SCAN", null, null, null, null,
                true, OffsetDateTime.now(), OffsetDateTime.now(), null, 1L);
    }

    @Test
    void appendsRowWithResolvedProjectEmptyPayloadAndNullRun() {
        when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null));

        verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("SOURCE_START"),
                eq(Instant.ofEpochSecond(5).atOffset(ZoneOffset.UTC)), eq("{}"));
    }

    @Test
    void wrapsDetailIntoJsonPayload() {
        when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("ds1", "ERROR", Instant.ofEpochSecond(7), "boom"));

        verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("ERROR"),
                eq(Instant.ofEpochSecond(7).atOffset(ZoneOffset.UTC)), eq("{\"detail\":\"boom\"}"));
    }

    @Test
    void dropsEventForUnknownDataSource() {
        when(sources.findById("gone")).thenReturn(Optional.empty());

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("gone", "SOURCE_STOP", Instant.ofEpochSecond(9), null));

        verifyNoInteractions(events);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests '*PersistingRuntimeActivityListenerTest'`
Expected: FAIL — `PersistingRuntimeActivityListener` does not exist (compilation error).

- [ ] **Step 3a: Write the listener**

Create `app/src/main/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListener.java`:

```java
package com.ainclusive.iotsim.app.runtime;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists supervisor runtime-activity events (IS-048) into {@code runtime_events}
 * (IS-049). Resolves the owning project from the data source, then appends an
 * append-only row. The supervisor calls this on an IPC delivery thread, so the
 * blocking lookup + insert is handed to {@code executor}.
 */
public final class PersistingRuntimeActivityListener implements RuntimeActivityListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingRuntimeActivityListener.class);
    private static final String EMPTY_PAYLOAD = "{}";

    private final DataSourceRepository dataSources;
    private final RuntimeEventRepository runtimeEvents;
    private final ObjectMapper json;
    private final Executor executor;

    public PersistingRuntimeActivityListener(DataSourceRepository dataSources,
            RuntimeEventRepository runtimeEvents, ObjectMapper json, Executor executor) {
        this.dataSources = dataSources;
        this.runtimeEvents = runtimeEvents;
        this.json = json;
        this.executor = executor;
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        executor.execute(() -> persist(event));
    }

    private void persist(RuntimeActivityEvent event) {
        try {
            Optional<String> projectId =
                    dataSources.findById(event.dataSourceId()).map(DataSourceRow::projectId);
            if (projectId.isEmpty()) {
                log.warn("dropping runtime event {} for unknown data source {}",
                        event.type(), event.dataSourceId());
                return;
            }
            runtimeEvents.append(projectId.get(), event.dataSourceId(), null, event.type(),
                    event.at().atOffset(ZoneOffset.UTC), payload(event.detail()));
        } catch (RuntimeException e) {
            log.warn("failed to persist runtime event {} for {}",
                    event.type(), event.dataSourceId(), e);
        }
    }

    private String payload(String detail) {
        if (detail == null || detail.isBlank()) {
            return EMPTY_PAYLOAD;
        }
        try {
            return json.writeValueAsString(Map.of("detail", detail));
        } catch (JsonProcessingException e) {
            return EMPTY_PAYLOAD;
        }
    }
}
```

- [ ] **Step 3b: Wire it into the supervisor bean**

In `RuntimeConfig.java`, add imports:

```java
import com.ainclusive.iotsim.app.runtime.PersistingRuntimeActivityListener;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
```

Replace the `runtimeController` bean (lines 26–32) with:

```java
    @Bean
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents, ObjectMapper json) {
        if (props.isSupervisorMode()) {
            // Persist runtime events off the IPC delivery thread (IS-048): the listener
            // is called on a gRPC thread that must stay non-blocking.
            Executor executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "runtime-event-persist");
                t.setDaemon(true);
                return t;
            });
            RuntimeActivityListener listener = new PersistingRuntimeActivityListener(
                    dataSources, runtimeEvents, json, executor);
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(), listener);
        }
        return new InMemoryRuntimeController();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests '*PersistingRuntimeActivityListenerTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListener.java \
        app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java \
        app/src/test/java/com/ainclusive/iotsim/app/runtime/PersistingRuntimeActivityListenerTest.java
git commit -m "feat(app): IS-048 persist RuntimeEvents to runtime_events"
```

---

### Task 5: Full build + verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — Spotless, Checkstyle, ArchUnit boundaries, JaCoCo, and all Testcontainers ITs green. If Spotless reports formatting, run `./gradlew spotlessApply` and re-run `./gradlew build`, then amend the relevant commit.

- [ ] **Step 2: Confirm the new tests ran**

Confirm in the output that `OpcUaRuntimeEventsIT`, `SupervisorRuntimeEventsTest`, `RuntimeActivityEventTest`, and `PersistingRuntimeActivityListenerTest` all executed and passed.

- [ ] **Step 3: Hand off to `/open-pr`**

Do NOT flip the `backend-specs/TASKS.md` IS-048 checkbox here — `/open-pr` flips it in the same PR (the catalog-sync CI gate requires the task-linked PR to flip its own checkbox), fills the PR template with `Implements: IS-048`, moves the board to In review, and arms squash auto-merge.

---

## Self-Review

**Spec coverage:**
- Worker emits SOURCE_START/SOURCE_STOP/ERROR → Task 2 (with the `subscribe-before-Start` ordering validated by the IT). ✓
- Stream worker→supervisor + tag dataSourceId → Task 3 (`WorkerClient.runtimeEvents`, `Supervisor.toRuntimeActivity`, subscribe before Start, cancel on stop). ✓
- Domain boundary (`platform`, free-string `type`) → Task 1. ✓
- Persist to `runtime_events`, resolve projectId, null runId, JSON payload, off-thread → Task 4. ✓
- Activates IS-049 repo via app wiring → Task 4 (`RuntimeConfig`). ✓
- Tests at three layers (worker IT, supervisor test, app unit test) → Tasks 2/3/4. ✓
- `./gradlew build` green → Task 5. ✓
- Out-of-scope (FAULT_STATE_CHANGE, replay/scenario, supervisor-origin events, read API/SSE) → not implemented, by design. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code. ✓

**Type consistency:** `RuntimeActivityEvent(dataSourceId, type, at, detail)` and `RuntimeActivityListener.onRuntimeActivity` are used identically across Tasks 1/3/4. `WorkerClient.runtimeEvents(Consumer<RuntimeEvent>, Consumer<Throwable>)` (Task 3) is consumed by `Supervisor.connect()` (same task). `Supervisor(WorkerLauncher, RestartPolicy, RuntimeActivityListener)` (Task 3) is the exact constructor `RuntimeConfig` calls (Task 4). `RuntimeEventRepository.append(...)` and `DataSourceRow.projectId()` match the existing signatures. The supervisor-test `new Supervisor(launcher, queue::add)` resolves unambiguously to the `RuntimeActivityListener` overload because the queue's element type is `RuntimeActivityEvent`. ✓
