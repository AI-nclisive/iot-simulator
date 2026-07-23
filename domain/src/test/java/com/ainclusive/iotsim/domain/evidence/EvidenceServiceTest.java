package com.ainclusive.iotsim.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventQuery;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import com.ainclusive.iotsim.persistence.timeline.RunValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository.ValueTimelineEntry;
import com.ainclusive.iotsim.platform.storage.ObjectStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvidenceServiceTest {

    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(30);

    private final FakeEvidence evidence = new FakeEvidence();
    private final FakeRuns runs = new FakeRuns();
    private final FakeTimeline timeline = new FakeTimeline();
    private final FakeRunTimeline runTimeline = new FakeRunTimeline();
    private final FakeRuntimeEvents runtimeEvents = new FakeRuntimeEvents();
    private final FakeClients clients = new FakeClients();
    private final FakeObjectStore objectStore = new FakeObjectStore();
    private final EvidenceArtifactWriter bundleWriter = new ZipEvidenceArtifactWriter(new ObjectMapper());
    private final EvidenceArtifactWriter summaryWriter = new JsonSummaryEvidenceWriter(new ObjectMapper());

    private EvidenceService service() {
        return new EvidenceService(evidence, runs, timeline, runTimeline, runtimeEvents, clients,
                List.of(bundleWriter, summaryWriter), objectStore, new ObjectMapper(), fakeProjects());
    }

    @Test
    void listPagedThrowsNotFoundForMissingProject() {
        assertThatThrownBy(() -> service().listPaged("no-such-project", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPagedEmitsCursorWhenResultsExceedLimit() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < 3; i++) {
            String evId = "paged-ev-" + i;
            evidence.byId.put(evId, new EvidenceRow(evId, "page-proj", null, "CAPTURING",
                    "{}", null, now.minusSeconds(i), "local"));
        }
        EvidenceService svc = new EvidenceService(evidence, runs, timeline, runTimeline, runtimeEvents, clients,
                List.of(bundleWriter, summaryWriter), objectStore, new ObjectMapper(), existsProjects("page-proj"));

        Page<EvidenceView> page = svc.listPaged("page-proj", null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.limit()).isEqualTo(2);

        // second page using the cursor returns the remaining item and no further cursor
        Page<EvidenceView> page2 = svc.listPaged("page-proj", page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.nextCursor()).isNull();
    }

    private static ProjectRepository existsProjects(String... ids) {
        java.util.Set<String> known = java.util.Set.of(ids);
        return new ProjectRepository() {
            @Override
            public Optional<ProjectRow> findById(String id) {
                if (!known.contains(id)) {
                    return Optional.empty();
                }
                return Optional.of(new ProjectRow(id, "n", null, "ACTIVE",
                        OffsetDateTime.now(ZoneOffset.UTC), null, "it", 0));
            }
            @Override public ProjectRow insert(String n, String d, String c) { throw new UnsupportedOperationException(); }
            @Override public List<ProjectRow> findAll() { return List.of(); }
            @Override public List<ProjectRow> findAllPaged(String s, java.time.OffsetDateTime a, String i, int l) { return List.of(); }
            @Override public Optional<ProjectRow> update(String id, String n, String d, long v) { throw new UnsupportedOperationException(); }
            @Override public Optional<ProjectRow> archive(String id) { throw new UnsupportedOperationException(); }
            @Override public boolean deleteById(String id) { throw new UnsupportedOperationException(); }
        };
    }

    private static ProjectRepository fakeProjects() {
        return new ProjectRepository() {
            @Override
            public Optional<ProjectRow> findById(String id) {
                return Optional.empty();
            }
            @Override
            public ProjectRow insert(String n, String d, String c) {
                throw new UnsupportedOperationException();
            }
            @Override
            public List<ProjectRow> findAll() {
                return List.of();
            }
            @Override
            public List<ProjectRow> findAllPaged(String s, java.time.OffsetDateTime a, String i, int l) {
                return List.of();
            }
            @Override
            public Optional<ProjectRow> update(String id, String n, String d, long v) {
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
        };
    }

    @Test
    void assemblesAllSectionsForAReplayRun() {
        seedReplayEvidence("COMPLETED");
        timeline.values = List.of(
                new NeutralValue("ns=2;s=Temp", START.plusSeconds(1), 21.5, Quality.GOOD, null));
        runtimeEvents.bySource.put("src-1", List.of(
                event("SOURCE_START", START.plusSeconds(1), "{}"),
                event("ERROR", START.plusSeconds(5), "{\"detail\":\"timeout\"}")));
        clients.bySource.put("src-1", List.of(
                conn("edge-7", START.plusSeconds(2), END)));

        EvidenceContent content = service().assemble("ev-1");

        assertThat(content.manifest().kind()).isEqualTo("REPLAY");
        assertThat(content.manifest().completeness()).isEqualTo(Completeness.COMPLETE);
        assertThat(content.manifest().recordingId()).isEqualTo("rec-1");
        assertThat(content.manifest().sourceIds()).containsExactly("src-1");
        assertThat(content.valueTimeline()).hasSize(1);
        assertThat(content.runtimeEvents()).hasSize(2);
        assertThat(content.errors()).extracting(ErrorRecord::type).containsExactly("ERROR");
        assertThat(content.clients()).extracting(ClientConnectionRecord::clientId).containsExactly("edge-7");
        assertThat(content.scenario()).isNull();
        assertThat(content.faults()).isEmpty();
    }

    @Test
    void assemblesValueTimelineFromRunValueTimelineWhenNoRecording() {
        // IS-185: SYNTHETIC (and SCENARIO) evidence carries no recordingId — the value
        // timeline must come from the run-keyed store instead of always being empty.
        runs.byId.put("run-2", new RunRow("run-2", "p1", "SYNTHETIC", "MANUAL", "local", "COMPLETED",
                null, "ev-2", off(START), off(END), off(START), List.of("src-1"), null));
        evidence.byId.put("ev-2", new EvidenceRow("ev-2", "p1", "run-2", "CAPTURING",
                "{\"recordingId\":null}", null, off(START), "local"));
        runTimeline.byRun.put("run-2", List.of(
                new NeutralValue("ns=2;s=Temp", START.plusSeconds(1), 42.0, Quality.GOOD, null)));

        EvidenceContent content = service().assemble("ev-2");

        assertThat(content.manifest().recordingId()).isNull();
        assertThat(content.valueTimeline()).hasSize(1);
    }

    @Test
    void failedRunMapsToFailedCompleteness() {
        seedReplayEvidence("FAILED");
        assertThat(service().assemble("ev-1").manifest().completeness()).isEqualTo(Completeness.FAILED);
    }

    @Test
    void filtersRuntimeEventsAndClientsToTheRunWindow() {
        seedReplayEvidence("COMPLETED");
        runtimeEvents.bySource.put("src-1", List.of(
                event("BEFORE", START.minusSeconds(5), "{}"),
                event("DURING", START.plusSeconds(10), "{}"),
                event("AFTER", END.plusSeconds(5), "{}")));
        clients.bySource.put("src-1", List.of(
                conn("during", START.plusSeconds(1), END.minusSeconds(1)),
                conn("ended-before", START.minusSeconds(20), START.minusSeconds(10)),
                conn("started-after", END.plusSeconds(10), null)));

        EvidenceContent content = service().assemble("ev-1");

        assertThat(content.runtimeEvents()).extracting(RuntimeEventRecord::type).containsExactly("DURING");
        assertThat(content.clients()).extracting(ClientConnectionRecord::clientId).containsExactly("during");
    }

    @Test
    void exportWritesBundleToObjectStoreAndMarksReady() {
        seedReplayEvidence("COMPLETED");

        EvidenceView result = service().export("p1", "ev-1", EvidenceFormat.BUNDLE);

        assertThat(objectStore.lastKey).isEqualTo("evidence/ev-1/bundle.zip");
        assertThat(objectStore.lastContentType).isEqualTo("application/zip");
        assertThat(objectStore.lastBytes.length).isPositive();
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.objectRef()).isEqualTo("evidence/ev-1/bundle.zip");
        assertThat(evidence.byId.get("ev-1").manifestJson()).contains("\"formatVersion\":\"1.0.0\"");
    }

    @Test
    void exportSummaryStoresJsonArtifact() {
        seedReplayEvidence("COMPLETED");

        EvidenceView result = service().export("p1", "ev-1", EvidenceFormat.SUMMARY);

        assertThat(objectStore.lastKey).isEqualTo("evidence/ev-1/summary.json");
        assertThat(objectStore.lastContentType).isEqualTo("application/json");
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.objectRef()).isEqualTo("evidence/ev-1/summary.json");
    }

    @Test
    void openBundleReportsContentTypeFromStoredArtifact() {
        seedReplayEvidence("COMPLETED");
        service().export("p1", "ev-1", EvidenceFormat.SUMMARY);

        EvidenceBundle bundle = service().openBundle("p1", "ev-1").orElseThrow();

        assertThat(bundle.contentType()).isEqualTo("application/json");
        assertThat(bundle.filename()).isEqualTo("evidence-ev-1.json");
    }

    @Test
    void exportMarksEvidenceFailedWhenWriterThrows() {
        seedReplayEvidence("COMPLETED");
        EvidenceArtifactWriter boom = new EvidenceArtifactWriter() {
            public EvidenceFormat format() {
                return EvidenceFormat.BUNDLE;
            }

            public void write(EvidenceContent c, java.io.OutputStream o) {
                throw new IllegalStateException("disk full");
            }

            public String contentType() {
                return "application/zip";
            }

            public String artifactFilename() {
                return "bundle.zip";
            }
        };
        EvidenceService svc = new EvidenceService(evidence, runs, timeline, runTimeline, runtimeEvents, clients,
                List.of(boom), objectStore, new ObjectMapper(), fakeProjects());

        EvidenceView result = svc.export("p1", "ev-1", EvidenceFormat.BUNDLE);

        assertThat(result.status()).isEqualTo("EXPORT_FAILED");
        assertThat(result.objectRef()).isNull();
        assertThat(objectStore.lastKey).isNull(); // nothing stored
    }

    // --- seed helpers ---

    private void seedReplayEvidence(String runState) {
        runs.byId.put("run-1", new RunRow("run-1", "p1", "REPLAY", "MANUAL", "local", runState,
                null, "ev-1", off(START), off(END), off(START), List.of("src-1"), null));
        evidence.byId.put("ev-1", new EvidenceRow("ev-1", "p1", "run-1", "CAPTURING",
                "{\"recordingId\":\"rec-1\"}", null, off(START), "local"));
    }

    private static RuntimeEventRow event(String type, Instant at, String payload) {
        return new RuntimeEventRow(1, "p1", "src-1", "run-1", type, off(at), payload);
    }

    private static ClientConnectionRow conn(String clientId, Instant from, Instant to) {
        return new ClientConnectionRow("c-" + clientId, "src-1", clientId, off(from),
                to == null ? null : off(to), "{}");
    }

    private static OffsetDateTime off(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    // --- fakes (only the methods EvidenceService calls are implemented) ---

    private static final class FakeEvidence implements EvidenceRepository {
        final Map<String, EvidenceRow> byId = new HashMap<>();

        public Optional<EvidenceRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<EvidenceRow> findByProject(String projectId) {
            return byId.values().stream().filter(e -> e.projectId().equals(projectId)).toList();
        }

        @Override
        public List<EvidenceRow> findByProjectPaged(String projectId,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return byId.values().stream()
                    .filter(e -> e.projectId().equals(projectId))
                    .filter(e -> afterAt == null || e.createdAt().isBefore(afterAt)
                            || (e.createdAt().isEqual(afterAt) && e.id().compareTo(afterId) < 0))
                    .sorted(java.util.Comparator.comparing(EvidenceRow::createdAt).reversed()
                            .thenComparing(java.util.Comparator.comparing(EvidenceRow::id).reversed()))
                    .limit(limit)
                    .toList();
        }

        public EvidenceRow updateManifest(String id, String manifestJson) {
            EvidenceRow e = byId.get(id);
            EvidenceRow updated = new EvidenceRow(e.id(), e.projectId(), e.runId(), e.status(),
                    manifestJson, e.objectRef(), e.createdAt(), e.createdBy());
            byId.put(id, updated);
            return updated;
        }

        public EvidenceRow updateStatus(String id, String status, String objectRef) {
            EvidenceRow e = byId.get(id);
            EvidenceRow updated = new EvidenceRow(e.id(), e.projectId(), e.runId(), status,
                    e.manifestJson(), objectRef, e.createdAt(), e.createdBy());
            byId.put(id, updated);
            return updated;
        }

        public EvidenceRow create(String p, String r, String c) {
            throw new UnsupportedOperationException();
        }

        public Optional<EvidenceRow> findByRun(String runId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRuns implements RunRepository {
        final Map<String, RunRow> byId = new HashMap<>();

        public Optional<RunRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public RunRow create(String p, String k, String t, String i, List<String> s, String sc,
                String parentRunId) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findByProjectPaged(String projectId, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findActiveByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public RunRow start(String id, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        public RunRow end(String id, String state, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        public RunRow linkEvidence(String runId, String evidenceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeTimeline implements ValueTimelineRepository {
        List<NeutralValue> values = List.of();

        public List<NeutralValue> readAll(String recordingId) {
            return values;
        }

        public long append(String recordingId, List<NeutralValue> v) {
            throw new UnsupportedOperationException();
        }

        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            throw new UnsupportedOperationException();
        }

        public long count(String recordingId) {
            throw new UnsupportedOperationException();
        }

        public long sumBytes(String recordingId) {
            throw new UnsupportedOperationException();
        }

        public void deleteByRecording(String recordingId) {
            throw new UnsupportedOperationException();
        }

        public List<ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit,
                com.ainclusive.iotsim.protocolmodel.ValueFilter filter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRunTimeline implements RunValueTimelineRepository {
        final Map<String, List<NeutralValue>> byRun = new HashMap<>();

        public long append(String runId, List<NeutralValue> values) {
            byRun.put(runId, values);
            return values.size();
        }

        public List<NeutralValue> readAll(String runId) {
            return byRun.getOrDefault(runId, List.of());
        }

        public void deleteByRun(String runId) {
            byRun.remove(runId);
        }
    }

    private static final class FakeRuntimeEvents implements RuntimeEventRepository {
        final Map<String, List<RuntimeEventRow>> bySource = new HashMap<>();

        public List<RuntimeEventRow> findByDataSource(String dataSourceId) {
            return bySource.getOrDefault(dataSourceId, List.of());
        }

        public RuntimeEventRow append(String p, String d, String r, String t, OffsetDateTime a, String j) {
            throw new UnsupportedOperationException();
        }

        public Optional<RuntimeEventRow> findById(long id) {
            throw new UnsupportedOperationException();
        }

        public List<RuntimeEventRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public List<RuntimeEventRow> findByRun(String runId) {
            throw new UnsupportedOperationException();
        }

        public List<RuntimeEventRow> query(RuntimeEventQuery filter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeClients implements ClientConnectionRepository {
        final Map<String, List<ClientConnectionRow>> bySource = new HashMap<>();

        public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
            return bySource.getOrDefault(dataSourceId, List.of());
        }

        public ClientConnectionRow open(String d, String c, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        public int close(String d, String c, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        public List<ClientConnectionRow> findCurrent(String dataSourceId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeObjectStore implements ObjectStore {
        String lastKey;
        byte[] lastBytes;
        String lastContentType;
        final Map<String, byte[]> blobs = new HashMap<>();

        public String put(String key, InputStream content, long sizeBytes, String contentType) {
            try {
                this.lastKey = key;
                this.lastBytes = content.readAllBytes();
                this.lastContentType = contentType;
                this.blobs.put(key, this.lastBytes);
                return key;
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        public Optional<InputStream> get(String ref) {
            return Optional.ofNullable(blobs.get(ref)).map(ByteArrayInputStream::new);
        }

        public boolean delete(String ref) {
            throw new UnsupportedOperationException();
        }
    }
}
