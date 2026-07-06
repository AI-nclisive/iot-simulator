package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioValidationServiceTest {

    @Test
    void emptyScenarioIsInvalid() {
        var f = new Fixture();
        String id = f.scenario();
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("INVALID");
        assertThat(v.issues()).anySatisfy(i -> assertThat(i.message()).contains("no steps"));
    }

    @Test
    void startStepWithMissingTargetIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "START", "no-such-src", "{}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void replayWithoutRecordingIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "REPLAY", f.SOURCE, "{}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void replaySchemaMismatchIsWarningButReady() {
        var f = new Fixture(); // recording schemaVersion=2, source current schema=1
        String id = f.scenario(new ScenarioStepRow(0, "REPLAY", f.SOURCE,
                "{\"recordingId\":\"" + f.RECORDING + "\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("READY");
        assertThat(v.issues()).anySatisfy(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    void syntheticStepRequiresSyntheticSourceAndPositiveDuration() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "SYNTHETIC", f.SOURCE, "{\"durationMs\":0}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void faultStepWithValidKindIsReady() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "FAULT", f.SOURCE, "{\"kind\":\"DELAY\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("READY");
        assertThat(v.issues()).isEmpty();
    }

    @Test
    void faultStepWithMissingSourceIdIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "FAULT", null, "{\"kind\":\"DELAY\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("INVALID");
        assertThat(v.issues()).anySatisfy(i ->
                assertThat(i.severity()).isEqualTo(ValidationIssue.ERROR));
    }

    @Test
    void faultStepWithMissingKindIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "FAULT", f.SOURCE, "{}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("INVALID");
        assertThat(v.issues()).anySatisfy(i ->
                assertThat(i.severity()).isEqualTo(ValidationIssue.ERROR));
    }

    @Test
    void faultStepWithUnknownKindIsWarningButReady() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "FAULT", f.SOURCE, "{\"kind\":\"UNKNOWN_KIND\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("READY");
        assertThat(v.issues()).anySatisfy(i ->
                assertThat(i.severity()).isEqualTo(ValidationIssue.WARNING));
    }

    @Test
    void stopStepWithMissingTargetIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "STOP", "no-such-src", "{}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void syntheticStepTargetingNonSyntheticSourceIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "SYNTHETIC", f.SOURCE_SCAN, "{\"durationMs\":1000}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void replayWithMissingSourceProducesOneErrorAndNoWarning() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "REPLAY", "no-such-src",
                "{\"recordingId\":\"" + f.RECORDING + "\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("INVALID");
        List<ValidationIssue> atOrdinal = v.issues().stream()
                .filter(i -> i.ordinal() == 0).toList();
        assertThat(atOrdinal).hasSize(1);
        assertThat(atOrdinal.get(0).severity()).isEqualTo(ValidationIssue.ERROR);
        assertThat(v.issues()).noneMatch(i -> i.ordinal() == 0
                && ValidationIssue.WARNING.equals(i.severity()));
    }

    @Test
    void markerAndValidStartAreReadyAndStatusPersisted() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "START", f.SOURCE, "{}"),
                new ScenarioStepRow(1, "MARKER", null, "{\"label\":\"checkpoint\"}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("READY");
        assertThat(f.persistedStatus(id)).isEqualTo("READY");
    }

    // ---- fixture ----

    private static final class Fixture {
        final String PROJECT = "proj-1";
        final String SOURCE = "src-synthetic-1";
        final String SOURCE_SCAN = "src-scan-1";
        final String RECORDING = "rec-1";

        private final InMemoryScenarioRepository scenarioRepo = new InMemoryScenarioRepository();
        final ScenarioValidationService service;

        Fixture() {
            service = new ScenarioValidationService(
                    scenarioRepo,
                    new FakeDataSourceRepository(PROJECT, SOURCE, SOURCE_SCAN),
                    new FakeRecordingRepository(PROJECT, RECORDING),
                    new FakeSchemaRepository(SOURCE),
                    new ObjectMapper());
        }

        /** Seeds the repo with a scenario with no steps and returns its id. */
        String scenario(ScenarioStepRow... steps) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String id = "scn-" + (scenarioRepo.seq + 1);
            ScenarioRow row = new ScenarioRow(id, PROJECT, "Test", "DRAFT", "{}",
                    List.of(steps), now, now, "tester", 0);
            scenarioRepo.rows.put(id, row);
            scenarioRepo.seq++;
            return id;
        }

        String persistedStatus(String id) {
            return scenarioRepo.rows.get(id).status();
        }
    }

    // ---- in-memory fakes ----

    private static final class InMemoryScenarioRepository implements ScenarioRepository {
        final Map<String, ScenarioRow> rows = new HashMap<>();
        int seq;

        @Override
        public Optional<ScenarioRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<ScenarioRow> updateStatus(String id, String status) {
            ScenarioRow cur = rows.get(id);
            if (cur == null) {
                return Optional.empty();
            }
            ScenarioRow next = new ScenarioRow(cur.id(), cur.projectId(), cur.name(), status,
                    cur.deterministicSettings(), cur.steps(),
                    cur.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), cur.createdBy(),
                    cur.version() + 1);
            rows.put(id, next);
            return Optional.of(next);
        }

        @Override
        public ScenarioRow create(String projectId, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ScenarioRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ScenarioRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeDataSourceRepository implements DataSourceRepository {
        private final String projectId;
        private final String sourceId;
        private final String scanSourceId;

        FakeDataSourceRepository(String projectId, String sourceId, String scanSourceId) {
            this.projectId = projectId;
            this.sourceId = sourceId;
            this.scanSourceId = scanSourceId;
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (sourceId.equals(id)) {
                return Optional.of(new DataSourceRow(sourceId, projectId, "Synthetic Src",
                        "OPC_UA", "SYNTHETIC", null, null, 0, null, null, null, true, now, now, "tester", 0));
            }
            if (scanSourceId.equals(id)) {
                return Optional.of(new DataSourceRow(scanSourceId, projectId, "Scan Src",
                        "OPC_UA", "SCAN", null, null, 0, null, null, null, true, now, now, "tester", 0));
            }
            return Optional.empty();
        }

        @Override
        public com.ainclusive.iotsim.persistence.datasource.DataSourceRow insert(
                String projectId, String name, String protocol, String basis,
                int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
                String securityConfigJson, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.ainclusive.iotsim.persistence.datasource.DataSourceRow> duplicate(
                String sourceId, String newName, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.ainclusive.iotsim.persistence.datasource.DataSourceRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.ainclusive.iotsim.persistence.datasource.DataSourceRow> findByProjectPaged(
                String projectId, String protocol, OffsetDateTime afterAt, String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.ainclusive.iotsim.persistence.datasource.DataSourceRow> update(
                String id, String name, int simulatorPort, String realDeviceEndpoint,
                String runtimeConfigJson, String securityConfigJson, boolean enabled, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRecordingRepository implements RecordingRepository {
        private final String projectId;
        private final String recordingId;

        FakeRecordingRepository(String projectId, String recordingId) {
            this.projectId = projectId;
            this.recordingId = recordingId;
        }

        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!recordingId.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            // schemaVersion=2, source's current schema is version=1 → mismatch = WARNING
            return Optional.of(new RecordingRow(recordingId, projectId, "src-synthetic-1",
                    2, "LIVE", "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "tester", 0));
        }

        @Override
        public RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
                String origin, String scanType, String name, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
                long valueCount, long sizeBytes) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeSchemaRepository implements SchemaRepository {
        private final String sourceId;

        FakeSchemaRepository(String sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            if (!sourceId.equals(dataSourceId)) {
                return Optional.empty();
            }
            // current schema version=1 (recording has version=2 → mismatch)
            return Optional.of(new SchemaWithNodes("schema-1", dataSourceId, 1,
                    OffsetDateTime.now(ZoneOffset.UTC), List.of()));
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId,
                List<com.ainclusive.iotsim.protocolmodel.SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }
}
