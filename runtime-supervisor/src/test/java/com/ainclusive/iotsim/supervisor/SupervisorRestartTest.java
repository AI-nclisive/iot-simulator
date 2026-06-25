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
 * Restart-with-backoff on unexpected worker failure (IS-040). Uses a fast policy
 * so the backoff is observable but tests stay quick. See
 * backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
class SupervisorRestartTest {

    // Tiny backoff: 10ms, 20ms, … capped at 50ms; up to 2 restart attempts.
    private final RestartPolicy fastPolicy =
            new RestartPolicy(Duration.ofMillis(10), 2.0, Duration.ofMillis(50), 2);
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
    void unexpectedExitTriggersRestartAndRecovers() {
        supervisor = new Supervisor(launcher, fastPolicy);
        supervisor.start("ds1", spec());
        assertThat(launcher.launchCount()).isEqualTo(1);

        launcher.crashLast();

        await(() -> launcher.launchCount() == 2);
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
    }

    @Test
    void recoveringWorkerReportsStarting() {
        // initialBackoff long enough that we can observe the STARTING window.
        RestartPolicy slowEnough =
                new RestartPolicy(Duration.ofMillis(300), 2.0, Duration.ofSeconds(1), 2);
        supervisor = new Supervisor(launcher, slowEnough);
        supervisor.start("ds1", spec());

        launcher.crashLast();

        await(() -> "STARTING".equals(supervisor.state("ds1")));
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
        assertThat(launcher.launchCount()).isEqualTo(2);
    }

    @Test
    void givesUpAndReportsErrorAfterCap() {
        supervisor = new Supervisor(launcher, fastPolicy);
        supervisor.start("ds1", spec());

        // Crash each worker once it is fully up (RUNNING ⇒ exit watcher registered):
        // initial + 2 restart attempts = 3 launches, then the cap is reached.
        launcher.crashLast();
        awaitRunning(2);
        launcher.crashLast();
        awaitRunning(3);
        launcher.crashLast();

        await(() -> "ERROR".equals(supervisor.state("ds1")));
        // The cap (2 restarts) is honoured: no fourth launch.
        assertStable(() -> launcher.launchCount() == 3);
    }

    /** Waits until exactly {@code launches} workers exist and the latest is RUNNING. */
    private void awaitRunning(int launches) {
        await(() -> launcher.launchCount() == launches && "RUNNING".equals(supervisor.state("ds1")));
    }

    @Test
    void erroredSourceCanBeRestartedExplicitly() {
        supervisor = new Supervisor(launcher, fastPolicy);
        supervisor.start("ds1", spec());

        // Drive the source past its restart budget into ERROR.
        launcher.crashLast();
        awaitRunning(2);
        launcher.crashLast();
        awaitRunning(3);
        launcher.crashLast();
        await(() -> "ERROR".equals(supervisor.state("ds1")));

        // An explicit start retries it with a fresh budget.
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        await(() -> "RUNNING".equals(supervisor.state("ds1")));
        assertThat(launcher.launchCount()).isEqualTo(4);
    }

    @Test
    void intentionalStopIsNotRestarted() {
        supervisor = new Supervisor(launcher, fastPolicy);
        supervisor.start("ds1", spec());
        assertThat(launcher.launchCount()).isEqualTo(1);

        assertThat(supervisor.stop("ds1")).isEqualTo("STOPPED");

        // Stopping closes the worker (which signals onExit); the supervisor must not
        // mistake that for a crash. Give any erroneous restart time to fire.
        assertStable(() -> launcher.launchCount() == 1 && "STOPPED".equals(supervisor.state("ds1")));
    }

    @Test
    void closeStopsRunningWorkers() {
        supervisor = new Supervisor(launcher, fastPolicy);
        supervisor.start("ds1", spec());

        supervisor.close();

        assertThat(supervisor.state("ds1")).isEqualTo("STOPPED");
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
        assertThat(condition.getAsBoolean()).as("condition should hold immediately").isTrue();
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
