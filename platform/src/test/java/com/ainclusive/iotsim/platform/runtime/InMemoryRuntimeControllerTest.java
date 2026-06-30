package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryRuntimeControllerTest {

    private static RuntimeStartSpec spec() {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(), 0);
    }

    @Test
    void healthReportsRunningStateWithNoError() {
        InMemoryRuntimeController rc = new InMemoryRuntimeController();
        rc.start("d1", spec());

        SourceHealth h = rc.health("d1");

        assertThat(h.state()).isEqualTo("RUNNING");
        assertThat(h.lastError()).isNull();
    }

    @Test
    void healthForUnknownSourceIsStoppedWithNoError() {
        InMemoryRuntimeController rc = new InMemoryRuntimeController();

        SourceHealth h = rc.health("unknown");

        assertThat(h.state()).isEqualTo("STOPPED");
        assertThat(h.lastError()).isNull();
    }
}
