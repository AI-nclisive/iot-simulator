package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.LiveValueListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SupervisorValueTeeTest {

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
    void applyValuesTeesToTheListener() {
        List<List<NeutralValue>> seen = new CopyOnWriteArrayList<>();
        LiveValueListener listener = (id, values, at) -> seen.add(values);

        supervisor = new Supervisor(
                launcher,
                RestartPolicy.DEFAULT,
                HealthPolicy.DEFAULT,
                ClientActivityListener.NONE,
                RuntimeActivityListener.NONE,
                listener);

        supervisor.start("d1", spec());

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long applied = supervisor.applyValues("d1", List.of(
                NeutralValue.good("n1", t, 1.0)));

        assertThat(applied).isEqualTo(1);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)).hasSize(1);
        assertThat(seen.get(0).get(0).nodeId()).isEqualTo("n1");
    }

    @Test
    void listenerErrorDoesNotBreakApply() {
        LiveValueListener throwing = (id, values, at) -> {
            throw new RuntimeException("listener boom");
        };

        supervisor = new Supervisor(
                launcher,
                RestartPolicy.DEFAULT,
                HealthPolicy.DEFAULT,
                ClientActivityListener.NONE,
                RuntimeActivityListener.NONE,
                throwing);

        supervisor.start("d1", spec());

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        // Must not throw; listener error is swallowed and the applied count is returned.
        assertThatCode(() -> {
            long applied = supervisor.applyValues("d1", List.of(
                    NeutralValue.good("n1", t, 1.0)));
            assertThat(applied).isEqualTo(1);
        }).doesNotThrowAnyException();
    }
}
