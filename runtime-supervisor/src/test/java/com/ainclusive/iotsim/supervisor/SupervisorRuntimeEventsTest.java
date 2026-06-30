package com.ainclusive.iotsim.supervisor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
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

    @Test
    void mapsWorkerErrorEventToProtocolOrigin() {
        RuntimeEvent error = RuntimeEvent.newBuilder()
                .setType("ERROR").setAtMicros(3_000_000L).setDetail("connection refused").build();

        RuntimeActivityEvent activity = Supervisor.toRuntimeActivity("ds1", error);

        assertThat(activity.type()).isEqualTo("ERROR");
        assertThat(activity.origin()).isEqualTo(HealthOrigin.PROTOCOL);
        assertThat(activity.detail()).isEqualTo("connection refused");
        assertThat(activity.at()).isEqualTo(Instant.ofEpochSecond(3));
    }

    @Test
    void mapsLifecycleEventsWithoutOrigin() {
        RuntimeEvent start = RuntimeEvent.newBuilder()
                .setType("SOURCE_START").setAtMicros(2_000_000L).build();

        assertThat(Supervisor.toRuntimeActivity("ds1", start).origin()).isNull();
    }
}
