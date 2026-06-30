package com.ainclusive.iotsim.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventQuery;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.storage.ObjectStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final FakeRuntimeEvents runtimeEvents = new FakeRuntimeEvents();
    private final FakeClients clients = new FakeClients();
    private final FakeObjectStore objectStore = new FakeObjectStore();
    private final EvidenceArtifactWriter writer = new ZipEvidenceArtifactWriter(new ObjectMapper());

    private EvidenceService service() {
        return new EvidenceService(evidence, runs, timeline, runtimeEvents, clients,
                writer, objectStore, new ObjectMapper());
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

        EvidenceView result = service().export("p1", "ev-1");

        assertThat(objectStore.lastKey).isEqualTo("evidence/ev-1/bundle.zip");
        assertThat(objectStore.lastContentType).isEqualTo("application/zip");
        assertThat(objectStore.lastBytes.length).isPositive();
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.objectRef()).isEqualTo("evidence/ev-1/bundle.zip");
        assertThat(evidence.byId.get("ev-1").manifestJson()).contains("\"formatVersion\":\"1.0.0\"");
    }

    @Test
    void exportMarksEvidenceFailedWhenWriterThrows() {
        seedReplayEvidence("COMPLETED");
        EvidenceArtifactWriter boom = new EvidenceArtifactWriter() {
            public void write(EvidenceContent c, java.io.OutputStream o) {
                throw new IllegalStateException("disk full");
            }

            public String formatVersion() {
                return "1.0.0";
            }

            public String contentType() {
                return "application/zip";
            }
        };
        EvidenceService svc = new EvidenceService(evidence, runs, timeline, runtimeEvents, clients,
                boom, objectStore, new ObjectMapper());

        EvidenceView result = svc.export("p1", "ev-1");

        assertThat(result.status()).isEqualTo("EXPORT_FAILED");
        assertThat(result.objectRef()).isNull();
        assertThat(objectStore.lastKey).isNull(); // nothing stored
    }

    // --- seed helpers ---

    private void seedReplayEvidence(String runState) {
        runs.byId.put("run-1", new RunRow("run-1", "p1", "REPLAY", "MANUAL", "local", runState,
                null, "ev-1", off(START), off(END), off(START), List.of("src-1")));
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

        public RunRow create(String p, String k, String t, String i, List<String> s, String sc) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findByProject(String projectId) {
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
        final List<String> stored = new ArrayList<>();

        public String put(String key, InputStream content, long sizeBytes, String contentType) {
            try {
                this.lastKey = key;
                this.lastBytes = content.readAllBytes();
                this.lastContentType = contentType;
                this.stored.add(key);
                return key;
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        public Optional<InputStream> get(String ref) {
            throw new UnsupportedOperationException();
        }

        public boolean delete(String ref) {
            throw new UnsupportedOperationException();
        }
    }
}
