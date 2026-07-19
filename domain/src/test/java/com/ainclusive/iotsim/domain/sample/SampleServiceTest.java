package com.ainclusive.iotsim.domain.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.sample.SampleRepository;
import com.ainclusive.iotsim.persistence.sample.SampleRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SampleServiceTest {

    private static final String PROJECT = "proj-1";
    private static final String RECORDING = "rec-1";

    private SampleService service;
    private InMemorySampleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySampleRepository();
        FakeRecordingRepository recordings = new FakeRecordingRepository(RECORDING, PROJECT);
        service = new SampleService(
                repository,
                recordings,
                new FakeProjectRepository(Set.of(PROJECT)),
                new ObjectMapper());
    }

    @Test
    void createUnderExistingProjectSucceeds() {
        Sample s = service.create(PROJECT, null, "Baseline", "{}", List.of("env", "prod"), "alice");
        assertThat(s.id()).isNotBlank();
        assertThat(s.name()).isEqualTo("Baseline");
        assertThat(s.projectId()).isEqualTo(PROJECT);
        assertThat(s.tags()).containsExactly("env", "prod");
        assertThat(s.selection()).isEqualTo("{}");
        assertThat(s.createdBy()).isEqualTo("alice");
        assertThat(s.version()).isZero();
    }

    @Test
    void createUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.create("nope", null, "X", null, null, "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithInvalidRecordingRefThrowsNotFound() {
        assertThatThrownBy(() -> service.create(PROJECT, "no-such-rec", "X", null, null, "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listReturnsAllSamplesForProject() {
        service.create(PROJECT, null, "S1", null, null, "a");
        service.create(PROJECT, null, "S2", null, null, "b");
        assertThat(service.list(PROJECT)).hasSize(2);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesSample() {
        Sample s = service.create(PROJECT, null, "ToDelete", null, null, "a");
        service.delete(PROJECT, s.id());
        assertThatThrownBy(() -> service.get(PROJECT, s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getFromWrongProjectThrowsNotFound() {
        Sample s = service.create(PROJECT, null, "Mine", null, null, "a");
        assertThatThrownBy(() -> service.get("other-project", s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPagedThrowsNotFoundForMissingProject() {
        assertThatThrownBy(() -> service.listPaged("no-such-project", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPagedEmitsCursorWhenResultsExceedLimit() {
        service.create(PROJECT, null, "S1", null, null, "it");
        service.create(PROJECT, null, "S2", null, null, "it");
        service.create(PROJECT, null, "S3", null, null, "it");

        Page<Sample> page = service.listPaged(PROJECT, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.limit()).isEqualTo(2);

        Page<Sample> page2 = service.listPaged(PROJECT, page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.nextCursor()).isNull();
    }

    private static final class InMemorySampleRepository implements SampleRepository {
        private final Map<String, SampleRow> rows = new HashMap<>();
        private int seq;

        @Override
        public SampleRow create(String projectId, String derivedFromRecordingId, String name,
                String selection, String tags, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            SampleRow row = new SampleRow("smp-" + (++seq), projectId, derivedFromRecordingId,
                    name, selection, tags, now, now, createdBy, 0);
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public Optional<SampleRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<SampleRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<SampleRow> findByProjectPaged(String projectId,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return rows.values().stream()
                    .filter(r -> r.projectId().equals(projectId))
                    .filter(r -> afterAt == null || r.createdAt().isBefore(afterAt)
                            || (r.createdAt().isEqual(afterAt) && r.id().compareTo(afterId) < 0))
                    .sorted(java.util.Comparator.comparing(SampleRow::createdAt).reversed()
                            .thenComparing(java.util.Comparator.comparing(SampleRow::id).reversed()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        private final Set<String> existing;

        FakeProjectRepository(Set<String> existing) {
            this.existing = existing;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!existing.contains(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new ProjectRow(id, "p", null, "ACTIVE", now, now, "local", 0));
        }

        @Override
        public ProjectRow insert(String name, String description, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProjectRow> findAll() {
            return List.of();
        }

        @Override
        public List<ProjectRow> findAllPaged(String status, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProjectRow> archive(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRecordingRepository implements RecordingRepository {
        private final String id;
        private final String projectId;

        FakeRecordingRepository(String id, String projectId) {
            this.id = id;
            this.projectId = projectId;
        }

        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds-1", "OPC_UA", 1, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        @Override
        public RecordingRow create(String projectId, String dataSourceId, String protocol,
                int schemaVersion, String origin, String scanType, String name, String createdBy,
                String schemaNodesJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String id, java.time.OffsetDateTime timeStart,
                java.time.OffsetDateTime timeEnd, long valueCount, long sizeBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }
}
