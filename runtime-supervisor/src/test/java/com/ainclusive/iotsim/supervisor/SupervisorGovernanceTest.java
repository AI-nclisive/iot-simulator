package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the global concurrent-worker cap (IS-061) with an in-process launcher. */
class SupervisorGovernanceTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private Supervisor supervisor;

    private static RuntimeStartSpec spec() {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(), 0);
    }

    /** maxRestarts=0: an unexpected crash goes straight to ERROR (no recovery). */
    private static RestartPolicy noRetry() {
        return new RestartPolicy(Duration.ofMillis(1), 1.0, Duration.ofMillis(1), 0);
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    @Test
    void refusesStartBeyondCap() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(2));
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
        assertThatThrownBy(() -> supervisor.start("ds3", spec()))
                .isInstanceOf(RuntimeCapacityException.class)
                .hasMessageContaining("cap reached (2)");
        assertThat(launcher.launchCount()).isEqualTo(2);
    }

    @Test
    void stopFreesASlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        assertThatThrownBy(() -> supervisor.start("ds2", spec()))
                .isInstanceOf(RuntimeCapacityException.class);
        supervisor.stop("ds1");
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
        assertThat(launcher.launchCount()).isEqualTo(2);
    }

    @Test
    void idempotentRestartTakesNoSlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        supervisor.start("ds1", spec()); // idempotent: must not need a second permit
        assertThat(launcher.launchCount()).isEqualTo(1);
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    @Test
    void terminalWorkerFreesItsSlot() {
        supervisor = new Supervisor(launcher, noRetry(), new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        launcher.crashLast();
        assertThat(supervisor.state("ds1")).isEqualTo("ERROR");
        // The errored worker released its slot, so a different source can start.
        assertThat(supervisor.start("ds2", spec())).isEqualTo("RUNNING");
    }

    @Test
    void replacingErroredWorkerReusesTheSlotWithoutLeaking() {
        supervisor = new Supervisor(launcher, noRetry(), new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        launcher.crashLast();
        assertThat(supervisor.state("ds1")).isEqualTo("ERROR");
        // Restart the same source: replaces the ERROR entry, takes a fresh permit.
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(launcher.launchCount()).isEqualTo(2);
        // Cap is still 1 and ds1 is active again, so a different source is refused — no leak.
        assertThatThrownBy(() -> supervisor.start("ds2", spec()))
                .isInstanceOf(RuntimeCapacityException.class);
    }

    @Test
    void failedLaunchReleasesItsSlot() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(1));
        launcher.setLaunchFailing(true);
        assertThatThrownBy(() -> supervisor.start("ds1", spec()))
                .isInstanceOf(RuntimeException.class);
        launcher.setLaunchFailing(false);
        // The failed launch must not have leaked the only permit.
        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
    }

    @Test
    void unlimitedCapNeverRefuses() {
        supervisor = new Supervisor(launcher, RestartPolicy.DEFAULT, new ResourceGovernancePolicy(0));
        for (int i = 0; i < 5; i++) {
            assertThat(supervisor.start("ds" + i, spec())).isEqualTo("RUNNING");
        }
        assertThat(launcher.launchCount()).isEqualTo(5);
    }

    @Test
    void permitHeldAcrossRestartBackoffRefusesAnotherSource() {
        // A recovering worker keeps its permit for the whole restart-with-backoff
        // window. With a long backoff (30s, never fires during the test) and one
        // retry budget, an unexpected crash leaves ds1 in STARTING — still holding
        // the only slot — so a *different* source must still be refused. This is the
        // subtlest governance invariant: the restart path must not release or
        // re-acquire the permit. (If the permit were freed on exit, ds2 would start.)
        RestartPolicy slowRetry = new RestartPolicy(
                Duration.ofSeconds(30), 1.0, Duration.ofSeconds(30), 1);
        supervisor = new Supervisor(launcher, slowRetry, new ResourceGovernancePolicy(1));
        supervisor.start("ds1", spec());
        launcher.crashLast();
        assertThat(supervisor.state("ds1")).isEqualTo("STARTING");
        assertThatThrownBy(() -> supervisor.start("ds2", spec()))
                .isInstanceOf(RuntimeCapacityException.class);
    }
}
