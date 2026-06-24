package com.epam.iotsim.domain.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.epam.iotsim.domain.common.ResourceNotFoundException;
import com.epam.iotsim.persistence.datasource.DataSourceRepository;
import com.epam.iotsim.persistence.datasource.DataSourceRow;
import com.epam.iotsim.persistence.recording.RecordingRepository;
import com.epam.iotsim.persistence.recording.RecordingRow;
import com.epam.iotsim.persistence.timeline.ValueTimelineRepository;
import com.epam.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordingServiceTest {

    private static final String PROJECT = "proj-1";
    private static final String SOURCE = "ds-1";

    private RecordingService service;

    @BeforeEach
    void setUp() {
        service = new RecordingService(
                new InMemoryRecordingRepository(),
                new InMemoryValueTimelineRepository(),
                new FakeDataSourceRepository(SOURCE, PROJECT));
    }

    @Test
    void createUnderExistingSource() {
        Recording r = service.create(PROJECT, SOURCE, "alice");
        assertThat(r.origin()).isEqualTo("SCAN_RECORD");
        assertThat(r.valueCount()).isZero();
        assertThat(r.dataSourceId()).isEqualTo(SOURCE);
    }

    @Test
    void createUnderMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.create(PROJECT, "nope", "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appendThenCompleteUpdatesValueCount() {
        Recording r = service.create(PROJECT, SOURCE, "a");
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long written = service.appendValues(PROJECT, r.id(), List.of(
                NeutralValue.good("v1", t, 1.0),
                NeutralValue.good("v1", t.plusMillis(1), 2.0),
                NeutralValue.good("v1", t.plusMillis(2), 3.0)));
        assertThat(written).isEqualTo(3);

        Recording completed = service.complete(PROJECT, r.id());
        assertThat(completed.valueCount()).isEqualTo(3);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class InMemoryRecordingRepository implements RecordingRepository {
        private final Map<String, RecordingRow> rows = new HashMap<>();
        private int seq;

        @Override
        public RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
                String origin, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            RecordingRow row = new RecordingRow("rec-" + (++seq), projectId, dataSourceId,
                    schemaVersion, origin, null, null, 0, 0, now, now, createdBy, 0);
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public Optional<RecordingRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
                long valueCount, long sizeBytes) {
            RecordingRow r = rows.get(id);
            RecordingRow updated = new RecordingRow(r.id(), r.projectId(), r.dataSourceId(),
                    r.schemaVersion(), r.origin(), timeStart, timeEnd, valueCount, sizeBytes,
                    r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
            rows.put(id, updated);
            return updated;
        }
    }

    private static final class InMemoryValueTimelineRepository implements ValueTimelineRepository {
        private final Map<String, List<NeutralValue>> byRecording = new HashMap<>();

        @Override
        public long append(String recordingId, List<NeutralValue> values) {
            byRecording.computeIfAbsent(recordingId, k -> new ArrayList<>()).addAll(values);
            return values.size();
        }

        @Override
        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            return byRecording.getOrDefault(recordingId, List.of()).stream()
                    .filter(v -> !v.sourceTime().isBefore(from) && !v.sourceTime().isAfter(to))
                    .toList();
        }

        @Override
        public List<NeutralValue> readAll(String recordingId) {
            return List.copyOf(byRecording.getOrDefault(recordingId, List.of()));
        }

        @Override
        public long count(String recordingId) {
            return byRecording.getOrDefault(recordingId, List.of()).size();
        }
    }

    private static final class FakeDataSourceRepository implements DataSourceRepository {
        private final String id;
        private final String projectId;

        FakeDataSourceRepository(String id, String projectId) {
            this.id = id;
            this.projectId = projectId;
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", "MANUAL",
                    null, 2, "{}", "{}", false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                String endpointJson, String runtimeConfigJson, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> update(String id, String name, String endpointJson,
                String runtimeConfigJson, boolean enabled, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }
}
