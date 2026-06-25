package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises the supervisor end to end with an in-process worker launcher. */
class SupervisorTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private Supervisor supervisor;

    private Supervisor supervisor() {
        supervisor = new Supervisor(launcher);
        return supervisor;
    }

    private static RuntimeStartSpec spec(SchemaNode... nodes) {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(nodes), 0);
    }

    private static SchemaNode folder(String id) {
        return new SchemaNode(id, null, id, id, NodeKind.FOLDER, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    @Test
    void startLaunchesWorkerAndStopTearsItDown() {
        Supervisor supervisor = supervisor();

        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
        assertThat(launcher.launchCount()).isEqualTo(1);

        assertThat(supervisor.stop("ds1")).isEqualTo("STOPPED");
        assertThat(supervisor.state("ds1")).isEqualTo("STOPPED");
    }

    @Test
    void startIsIdempotentForSameSource() {
        Supervisor supervisor = supervisor();
        supervisor.start("ds1", spec());
        supervisor.start("ds1", spec());
        assertThat(launcher.launchCount()).isEqualTo(1);
    }

    @Test
    void stateOfUnknownSourceIsStopped() {
        assertThat(supervisor().state("unknown")).isEqualTo("STOPPED");
    }

    @Test
    void configurePropagatesSchemaToWorker() {
        Supervisor supervisor = supervisor();
        supervisor.start("ds1", spec(folder("a"), folder("b")));
        assertThat(launcher.launchCount()).isEqualTo(1);
        assertThat(launcher.last().service().configuredNodeCount()).isEqualTo(2);
    }

    @Test
    void applyValuesStreamsToRunningWorker() {
        Supervisor supervisor = supervisor();
        supervisor.start("ds1", spec());

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long applied = supervisor.applyValues("ds1", List.of(
                NeutralValue.good("temp", t, 21.5),
                NeutralValue.good("temp", t.plusMillis(1), 21.6)));

        assertThat(applied).isEqualTo(2);
        assertThat(launcher.launchCount()).isEqualTo(1);
        assertThat(launcher.last().service().appliedCount()).isEqualTo(2);
    }
}
