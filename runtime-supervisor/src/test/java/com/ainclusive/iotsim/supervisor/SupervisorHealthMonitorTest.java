package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Health monitoring loop + stale/error state propagation (IS-041).
 *
 * <p>Uses a very short poll interval so behaviour is observable without
 * making tests slow. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
class SupervisorHealthMonitorTest {

    /** Poll every 20 ms; mark stale after 2 consecutive missed polls. */
    private static final HealthMonitorPolicy FAST_HEALTH =
            new HealthMonitorPolicy(Duration.ofMillis(20), 2);

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

    // ── healthy path ─────────────────────────────────────────────────────────

    @Test
    void runningWorkerRemainsRunningWhenHealthy() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, FAST_HEALTH);
        supervisor.start("ds1", spec());

        // Let several polls fire — state must stay RUNNING.
        await(() -> launcher.last().service().healthCallCount() >= 3);
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    // ── stale path ───────────────────────────────────────────────────────────

    @Test
    void workerTransitionsToStaleAfterThresholdMissedPolls() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, FAST_HEALTH);
        supervisor.start("ds1", spec());

        // Confirm healthy first, then degrade.
        await(() -> launcher.last().service().healthCallCount() >= 1);
        launcher.last().service().simulateUnhealthy();

        // staleThreshold = 2, so after 2 bad polls → STALE.
        await(() -> "STALE".equals(supervisor.state("ds1")));
        assertThat(supervisor.state("ds1")).isEqualTo("STALE");
    }

    @Test
    void staleWorkerRecovershWhenHealthReturns() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, FAST_HEALTH);
        supervisor.start("ds1", spec());

        // Go stale.
        await(() -> launcher.last().service().healthCallCount() >= 1);
        launcher.last().service().simulateUnhealthy();
        await(() -> "STALE".equals(supervisor.state("ds1")));

        // Restore health — worker should go back to RUNNING.
        launcher.last().service().simulateHealthy();
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    // ── interaction with restart ──────────────────────────────────────────────

    @Test
    void staleCounterResetAfterRestart() {
        RestartPolicy fastRestart =
                new RestartPolicy(Duration.ofMillis(10), 2.0, Duration.ofMillis(50), 2);
        supervisor = new Supervisor(launcher, fastRestart, FAST_HEALTH);
        supervisor.start("ds1", spec());

        // Make the current worker stale.
        await(() -> launcher.last().service().healthCallCount() >= 1);
        launcher.last().service().simulateUnhealthy();
        await(() -> "STALE".equals(supervisor.state("ds1")));

        // Crash the worker — restart brings up a fresh, healthy one.
        launcher.crashLast();
        await(() -> launcher.launchCount() == 2);

        // New worker responds healthy → RUNNING (not immediately STALE).
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    @Test
    void stoppedWorkerIsNotPolled() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, FAST_HEALTH);
        supervisor.start("ds1", spec());

        // Let at least one poll happen so the worker is observed as healthy.
        await(() -> launcher.last().service().healthCallCount() >= 1);
        int pollsBeforeStop = launcher.last().service().healthCallCount();

        supervisor.stop("ds1");
        assertThat(supervisor.state("ds1")).isEqualTo("STOPPED");

        // Wait longer than one poll interval — counter must not grow.
        sleep(60);
        assertThat(launcher.last().service().healthCallCount())
                .isLessThanOrEqualTo(pollsBeforeStop + 1); // at most one in-flight at stop time
    }

    // ── HealthMonitorPolicy validation ───────────────────────────────────────

    @Test
    void defaultPolicyHasReasonableValues() {
        HealthMonitorPolicy policy = HealthMonitorPolicy.DEFAULT;
        assertThat(policy.pollInterval()).isGreaterThan(Duration.ZERO);
        assertThat(policy.staleThreshold()).isGreaterThanOrEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
