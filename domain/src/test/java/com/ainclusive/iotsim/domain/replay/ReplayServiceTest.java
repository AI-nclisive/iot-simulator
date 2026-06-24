package com.ainclusive.iotsim.domain.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final String RECORDING = "rec1";

    private InMemoryRuntimeController runtime;
    private ReplayService service;

    @BeforeEach
    void setUp() {
        runtime = new InMemoryRuntimeController();
        List<NeutralValue> values = List.of(
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:00Z"), 1.0),
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:01Z"), 2.0),
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:02Z"), 3.0));
        service = new ReplayService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordings(RECORDING, PROJECT),
                new FakeTimeline(values),
                new EmptySchemas(),
                runtime);
    }

    @Test
    void replayStartsSourceAndStreamsAllValues() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING);
        assertThat(summary.valueCount()).isEqualTo(3);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.appliedCount(SOURCE)).isEqualTo(3);
    }

    @Test
    void replayMissingRecordingThrowsNotFound() {
        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replayMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.replay(PROJECT, "nope", RECORDING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private record FakeDataSources(String id, String projectId) implements DataSourceRepository {
        @Override
        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", "MANUAL",
                    null, null, "{}", "{}", false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String p, String n, String pr, String b, String e, String rc, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> update(String i, String n, String e, String rc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeRecordings(String id, String projectId) implements RecordingRepository {
        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", 1, "SCAN_RECORD",
                    null, null, 0, 0, now, now, "local", 0));
        }

        @Override
        public RecordingRow create(String p, String d, int sv, String o, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeTimeline(List<NeutralValue> values) implements ValueTimelineRepository {
        @Override
        public List<NeutralValue> readAll(String recordingId) {
            return values;
        }

        @Override
        public long append(String recordingId, List<NeutralValue> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            return values;
        }

        @Override
        public long count(String recordingId) {
            return values.size();
        }
    }

    private record EmptySchemas() implements SchemaRepository {
        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.empty();
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }
}
