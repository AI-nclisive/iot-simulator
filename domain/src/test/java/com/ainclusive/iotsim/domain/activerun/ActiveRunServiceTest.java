package com.ainclusive.iotsim.domain.activerun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ActiveRunService}: uses minimal stubs — no Spring context.
 * Verifies process-type/run-state mapping and related-label resolution.
 */
class ActiveRunServiceTest {

    // ---- Stub implementations ----

    private static class StubRunRepository implements RunRepository {
        private final List<RunRow> active;

        StubRunRepository(List<RunRow> active) {
            this.active = active;
        }

        @Override
        public RunRow create(String p, String k, String t, String i, List<String> s, String sid,
                String pr) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RunRow> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<RunRow> findByProject(String projectId) {
            return active;
        }

        @Override
        public List<RunRow> findActiveByProject(String projectId) {
            return active;
        }

        @Override
        public List<RunRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRow start(String id, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRow end(String id, String state, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRow linkEvidence(String runId, String evidenceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StubDataSourceRepository implements DataSourceRepository {
        private final List<DataSourceRow> rows;

        StubDataSourceRepository(List<DataSourceRow> rows) {
            this.rows = rows;
        }

        @Override
        public DataSourceRow insert(String p, String n, String prot, String b, int sp, String rde,
                String rc, String cb) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DataSourceRow> duplicate(String id, String newName, String cb) {
            return Optional.empty();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return rows;
        }

        @Override
        public List<DataSourceRow> findByProjectPaged(String p, String prot,
                OffsetDateTime afterAt, String afterId, int limit) {
            return rows;
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DataSourceRow> update(String id, String name, int sp, String rde, String rc,
                boolean enabled, long ev) {
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class StubScenarioRepository implements ScenarioRepository {
        private final List<ScenarioRow> rows;

        StubScenarioRepository(List<ScenarioRow> rows) {
            this.rows = rows;
        }

        @Override
        public ScenarioRow create(String p, String n, String d, List<ScenarioStepInput> steps,
                String cb) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ScenarioRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<ScenarioRow> findByProject(String projectId) {
            return rows;
        }

        @Override
        public List<ScenarioRow> findByProjectPaged(String p, OffsetDateTime afterAt,
                String afterId, int limit) {
            return rows;
        }

        @Override
        public Optional<ScenarioRow> update(String id, String name, String d,
                List<ScenarioStepInput> steps, long ev) {
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }

        @Override
        public Optional<ScenarioRow> updateStatus(String id, String status) {
            return Optional.empty();
        }
    }

    // ---- Factories ----

    private static RunRow runRow(String id, String kind, String state, List<String> sourceIds,
            String scenarioId) {
        OffsetDateTime at = OffsetDateTime.of(2026, 7, 1, 14, 0, 0, 0, ZoneOffset.UTC);
        return new RunRow(id, "p1", kind, "MANUAL", "alice", state, scenarioId, null,
                at, null, at, sourceIds, null);
    }

    private static DataSourceRow dsRow(String id, String name) {
        OffsetDateTime at = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        return new DataSourceRow(id, "p1", name, "OPC_UA", "REAL", null, 0, 0, null, "{}", true,
                at, at, "local", 0L);
    }

    private static ScenarioRow scenRow(String id, String name) {
        OffsetDateTime at = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        return new ScenarioRow(id, "p1", name, "READY", "{}", List.of(), at, at, "local", 0L);
    }

    // ---- Tests ----

    @Test
    void emptyRunListReturnsEmptyResult() {
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of()),
                new StubDataSourceRepository(List.of()),
                new StubScenarioRepository(List.of()));

        assertThat(svc.getActiveRuns("p1")).isEmpty();
    }

    @Test
    void replayRunMapsProcessTypeAndRunState() {
        RunRow row = runRow("run-1", "REPLAY", "RUNNING", List.of("src-1"), null);
        DataSourceRow ds = dsRow("src-1", "Line A stream");
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of(row)),
                new StubDataSourceRepository(List.of(ds)),
                new StubScenarioRepository(List.of()));

        List<ActiveRun> result = svc.getActiveRuns("p1");

        assertThat(result).hasSize(1);
        ActiveRun r = result.get(0);
        assertThat(r.id()).isEqualTo("run-1");
        assertThat(r.processType()).isEqualTo("Replay");
        assertThat(r.runState()).isEqualTo("running");
        assertThat(r.relatedSourceId()).isEqualTo("src-1");
        assertThat(r.relatedLabel()).isEqualTo("Line A stream");
        assertThat(r.initiator()).isEqualTo("alice");
    }

    @Test
    void recordingRunMapsCorrectly() {
        RunRow row = runRow("run-2", "RECORDING", "QUEUED", List.of("src-2"), null);
        DataSourceRow ds = dsRow("src-2", "Sensor feed");
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of(row)),
                new StubDataSourceRepository(List.of(ds)),
                new StubScenarioRepository(List.of()));

        ActiveRun r = svc.getActiveRuns("p1").get(0);
        assertThat(r.processType()).isEqualTo("Recording");
        assertThat(r.runState()).isEqualTo("queued");
        assertThat(r.relatedLabel()).isEqualTo("Sensor feed");
    }

    @Test
    void scenarioRunResolvesScenarioName() {
        RunRow row = runRow("run-3", "SCENARIO", "RUNNING", List.of(), "scn-1");
        ScenarioRow scn = scenRow("scn-1", "Smoke test flow");
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of(row)),
                new StubDataSourceRepository(List.of()),
                new StubScenarioRepository(List.of(scn)));

        ActiveRun r = svc.getActiveRuns("p1").get(0);
        assertThat(r.processType()).isEqualTo("Scenario");
        assertThat(r.relatedSourceId()).isNull();
        assertThat(r.relatedLabel()).isEqualTo("Smoke test flow");
    }

    @Test
    void missingSourceNameFallsBackGracefully() {
        RunRow row = runRow("run-4", "REPLAY", "RUNNING", List.of("src-missing"), null);
        // No data sources registered → sourceNameById will be empty.
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of(row)),
                new StubDataSourceRepository(List.of()),
                new StubScenarioRepository(List.of()));

        ActiveRun r = svc.getActiveRuns("p1").get(0);
        assertThat(r.relatedSourceId()).isEqualTo("src-missing");
        assertThat(r.relatedLabel()).isNull();
        // Label falls back to processType + abbreviated id.
        assertThat(r.label()).contains("Replay");
    }

    @Test
    void startedAtIsNullWhenRunNotYetStarted() {
        OffsetDateTime at = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        RunRow row = new RunRow("run-5", "p1", "REPLAY", "MANUAL", "local", "QUEUED",
                null, null, null /* startedAt=null */, null, at, List.of(), null);
        ActiveRunService svc = new ActiveRunService(
                new StubRunRepository(List.of(row)),
                new StubDataSourceRepository(List.of()),
                new StubScenarioRepository(List.of()));

        ActiveRun r = svc.getActiveRuns("p1").get(0);
        assertThat(r.startedAt()).isNull();
    }
}
