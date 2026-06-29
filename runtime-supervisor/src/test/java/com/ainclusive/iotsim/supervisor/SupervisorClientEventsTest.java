package com.ainclusive.iotsim.supervisor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the supervisor consumes each worker's {@code ClientEvents} stream
 * (IS-047): it opens the stream when a worker reaches RUNNING, forwards events to
 * the listener tagged with the data-source id, and cancels the stream on stop.
 */
class SupervisorClientEventsTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private final BlockingQueue<ClientActivityEvent> events = new LinkedBlockingQueue<>();
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
    void forwardsWorkerClientEventsTaggedWithSource() throws Exception {
        supervisor = new Supervisor(launcher, events::add);

        supervisor.start("ds1", spec());

        ClientActivityEvent event = events.poll(5, SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.dataSourceId()).isEqualTo("ds1");
        assertThat(event.kind()).isEqualTo(ClientActivityEvent.Kind.CONNECTED);
        assertThat(event.clientId()).isEqualTo("client-1");
        assertThat(event.at()).isEqualTo(Instant.ofEpochSecond(2));
    }

    @Test
    void stopCancelsTheClientEventStream() throws Exception {
        supervisor = new Supervisor(launcher, events::add);
        supervisor.start("ds1", spec());
        // Wait for the first event so we know the stream is established before stopping.
        assertThat(events.poll(5, SECONDS)).isNotNull();

        supervisor.stop("ds1");

        assertThat(launcher.last().service().awaitClientEventsCancelled(5)).isTrue();
    }

    @Test
    void defaultListenerIgnoresClientEventsWithoutError() {
        // No listener wired: the stream is still opened but events are discarded.
        supervisor = new Supervisor(launcher);
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }
}
