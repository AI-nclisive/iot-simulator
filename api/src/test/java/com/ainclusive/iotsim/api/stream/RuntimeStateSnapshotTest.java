package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.stream.RuntimeStateSnapshot.SourceRuntimeState;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class RuntimeStateSnapshotTest {

    private static RuntimeController controllerWhere(String runningId) {
        return new RuntimeController() {
            public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
            public String stop(String id) { return "STOPPED"; }
            public String state(String id) { return id.equals(runningId) ? "RUNNING" : "STOPPED"; }
            public long applyValues(String id, List<NeutralValue> v) { return 0; }
        };
    }

    @Test
    void buildsStatePerSourceInProject() {
        ProjectSources sources = pid -> List.of("d1", "d2");
        RuntimeStateSnapshot snapshot = new RuntimeStateSnapshot(controllerWhere("d1"), sources);

        List<LiveEvent> initial = snapshot.initialFor("p1");

        assertThat(initial).singleElement().satisfies(ev -> {
            assertThat(ev.type()).isEqualTo("runtime-state");
            assertThat(ev.hasSeq()).isFalse();
            assertThat((List<?>) ev.data())
                    .extracting("dataSourceId", "state")
                    .containsExactlyInAnyOrder(
                            Tuple.tuple("d1", "RUNNING"),
                            Tuple.tuple("d2", "STOPPED"));
        });
    }

    @Test
    void includesLastErrorFromHealth() {
        RuntimeController rc = new RuntimeController() {
            public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
            public String stop(String id) { return "STOPPED"; }
            public String state(String id) { return "STALE"; }
            public long applyValues(String id, List<NeutralValue> v) { return 0; }
            public SourceHealth health(String id) {
                return new SourceHealth("STALE",
                        new SourceError(HealthOrigin.SIMULATOR, "stale", Instant.EPOCH));
            }
        };
        ProjectSources sources = pid -> List.of("d1");

        List<LiveEvent> initial = new RuntimeStateSnapshot(rc, sources).initialFor("p1");

        List<?> data = (List<?>) initial.get(0).data();
        SourceRuntimeState s = (SourceRuntimeState) data.get(0);
        assertThat(s.state()).isEqualTo("STALE");
        assertThat(s.lastError().origin()).isEqualTo(HealthOrigin.SIMULATOR);
    }
}
