package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Health-monitoring loop + stale/error state propagation (IS-041). A fast poll
 * keeps tests quick while still exercising the probe → stale → recover path. See
 * backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
class SupervisorHealthTest {

    // Probe every 20ms with a 200ms deadline; STALE after 2 consecutive misses.
    private final HealthPolicy fastHealth =
            new HealthPolicy(Duration.ofMillis(20), Duration.ofMillis(200), 2);
    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
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
    void healthyWorkerStaysRunning() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth);
        supervisor.start("ds1", spec());

        // Let several poll cycles run; a responsive worker is never marked stale.
        assertStable(() -> "RUNNING".equals(supervisor.state("ds1")));
    }

    @Test
    void unresponsiveWorkerBecomesStaleWithoutRestart() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth);
        supervisor.start("ds1", spec());

        // Worker is alive (no process exit) but stops reporting healthy.
        launcher.last().service().setHealthLive(false);

        await(() -> "STALE".equals(supervisor.state("ds1")));
        // Staleness is propagation only — it must not spawn a replacement worker.
        assertStable(() -> "STALE".equals(supervisor.state("ds1")) && launcher.launchCount() == 1);
    }

    @Test
    void hungWorkerBecomesStaleViaProbeDeadline() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth);
        supervisor.start("ds1", spec());

        // Worker stops answering Health entirely; the probe deadline must drive STALE.
        launcher.last().service().setHealthHang(true);

        await(() -> "STALE".equals(supervisor.state("ds1")));
        assertThat(launcher.launchCount()).isEqualTo(1);
    }

    @Test
    void staleWorkerRecoversToRunning() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth);
        supervisor.start("ds1", spec());

        launcher.last().service().setHealthLive(false);
        await(() -> "STALE".equals(supervisor.state("ds1")));

        // A single good probe clears staleness without any restart.
        launcher.last().service().setHealthLive(true);
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
        assertThat(launcher.launchCount()).isEqualTo(1);
    }

    @Test
    void stoppedWorkerIsNotProbed() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth);
        supervisor.start("ds1", spec());
        supervisor.stop("ds1");

        // The health loop must not resurrect or re-state a stopped source.
        assertStable(() -> "STOPPED".equals(supervisor.state("ds1")));
    }

    @Test
    void staleTransitionEmitsHealthEventWithSimulatorOrigin() {
        List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
                ClientActivityListener.NONE, events::add);
        supervisor.start("ds1", spec());

        launcher.last().service().setHealthLive(false);

        await(() -> events.stream().anyMatch(e -> e.type().equals("SOURCE_STALE")));
        RuntimeActivityEvent stale = events.stream()
                .filter(e -> e.type().equals("SOURCE_STALE")).findFirst().orElseThrow();
        assertThat(stale.origin()).isEqualTo(HealthOrigin.SIMULATOR);
        assertThat(stale.dataSourceId()).isEqualTo("ds1");

        SourceHealth h = supervisor.health("ds1");
        assertThat(h.state()).isEqualTo("STALE");
        assertThat(h.lastError()).isNotNull();
        assertThat(h.lastError().origin()).isEqualTo(HealthOrigin.SIMULATOR);
    }

    @Test
    void recoveryEmitsRecoveredEventAndRetainsLastError() {
        List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
                ClientActivityListener.NONE, events::add);
        supervisor.start("ds1", spec());

        launcher.last().service().setHealthLive(false);
        await(() -> "STALE".equals(supervisor.state("ds1")));
        launcher.last().service().setHealthLive(true);

        await(() -> events.stream().anyMatch(e -> e.type().equals("SOURCE_RECOVERED")));
        SourceHealth h = supervisor.health("ds1");
        assertThat(h.state()).isEqualTo("RUNNING");
        assertThat(h.lastError()).isNotNull(); // retained after recovery
    }

    @Test
    void staleEventIsEmittedOnlyOncePerTransition() {
        List<RuntimeActivityEvent> events = new CopyOnWriteArrayList<>();
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, fastHealth,
                ClientActivityListener.NONE, events::add);
        supervisor.start("ds1", spec());

        launcher.last().service().setHealthLive(false);
        await(() -> "STALE".equals(supervisor.state("ds1")));
        sleep(300); // several more poll cycles while still unhealthy

        long staleCount = events.stream().filter(e -> e.type().equals("SOURCE_STALE")).count();
        assertThat(staleCount).isEqualTo(1);
    }

    /** Polls until the condition holds, up to ~5s. */
    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("condition not met within timeout").isTrue();
    }

    /** Asserts the condition holds now and still holds after a short settle window. */
    private static void assertStable(BooleanSupplier condition) {
        await(condition);
        sleep(300);
        assertThat(condition.getAsBoolean()).as("condition should remain stable").isTrue();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
