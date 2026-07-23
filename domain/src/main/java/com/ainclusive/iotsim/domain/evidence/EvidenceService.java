package com.ainclusive.iotsim.domain.evidence;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import com.ainclusive.iotsim.persistence.timeline.RunValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.storage.ObjectStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Assembles and exports run evidence (IS-057, SPEC "Export Run Evidence"). Gathers
 * a run's value timeline, runtime events, client history, faults and errors into an
 * {@link EvidenceContent}, then serializes it with an {@link EvidenceArtifactWriter}
 * and stores the bundle in the {@link ObjectStore}. Evidence is built only from
 * timeline/event/client data — never from source config — so no secret can leak.
 * See backend-specs/05 &amp; 06.
 */
@Service
public class EvidenceService {

    private static final System.Logger log = System.getLogger(EvidenceService.class.getName());

    private final EvidenceRepository evidence;
    private final RunRepository runs;
    private final ValueTimelineRepository timeline;
    private final RunValueTimelineRepository runTimeline;
    private final RuntimeEventRepository runtimeEvents;
    private final ClientConnectionRepository clients;
    private final Map<EvidenceFormat, EvidenceArtifactWriter> writers;
    private final ObjectStore objectStore;
    private final ObjectMapper json;
    private final ProjectRepository projects;

    public EvidenceService(EvidenceRepository evidence, RunRepository runs,
            ValueTimelineRepository timeline, RunValueTimelineRepository runTimeline,
            RuntimeEventRepository runtimeEvents,
            ClientConnectionRepository clients, List<EvidenceArtifactWriter> writers,
            ObjectStore objectStore, ObjectMapper json, ProjectRepository projects) {
        this.evidence = evidence;
        this.runs = runs;
        this.timeline = timeline;
        this.runTimeline = runTimeline;
        this.runtimeEvents = runtimeEvents;
        this.clients = clients;
        this.writers = new EnumMap<>(EvidenceFormat.class);
        writers.forEach(w -> this.writers.put(w.format(), w));
        this.objectStore = objectStore;
        this.json = json;
        this.projects = projects;
    }

    /** All evidence for a project, newest first. */
    public List<EvidenceView> list(String projectId) {
        return evidence.findByProject(projectId).stream().map(EvidenceView::from).toList();
    }

    public Page<EvidenceView> listPaged(String projectId, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<EvidenceRow> rows = evidence.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            EvidenceRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(EvidenceView::from).toList(), nextCursor, size);
    }

    /** One evidence record, scoped to its project. */
    public Optional<EvidenceView> find(String projectId, String evidenceId) {
        return row(projectId, evidenceId).map(EvidenceView::from);
    }

    /** Opens the exported artifact for download (with its content-type), or empty if not exported. */
    public Optional<EvidenceBundle> openBundle(String projectId, String evidenceId) {
        return row(projectId, evidenceId)
                .filter(e -> e.objectRef() != null)
                .flatMap(e -> objectStore.get(e.objectRef()).map(in -> new EvidenceBundle(
                        in, contentTypeFor(e.objectRef()), "evidence-" + evidenceId + extensionFor(e.objectRef()))));
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private static String contentTypeFor(String objectRef) {
        return objectRef.endsWith(".json") ? "application/json" : "application/zip";
    }

    private static String extensionFor(String objectRef) {
        return objectRef.endsWith(".json") ? ".json" : ".zip";
    }

    private Optional<EvidenceRow> row(String projectId, String evidenceId) {
        return evidence.findById(evidenceId).filter(e -> e.projectId().equals(projectId));
    }

    /** Gathers the in-memory content of an evidence artifact from its run's data. */
    public EvidenceContent assemble(String evidenceId) {
        EvidenceRow ev = evidence.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId));
        RunRow run = ev.runId() == null ? null : runs.findById(ev.runId()).orElse(null);

        String recordingId = readField(ev.manifestJson(), "recordingId");
        Instant startedAt = run != null ? toInstant(run.startedAt()) : null;
        Instant endedAt = run != null ? toInstant(run.endedAt()) : null;
        List<String> sourceIds = run != null ? run.sourceIds() : List.of();

        List<ValueSample> valueTimeline;
        if (recordingId != null) {
            valueTimeline = timeline.readAll(recordingId).stream().map(EvidenceService::toSample).toList();
        } else if (ev.runId() != null) {
            valueTimeline = runTimeline.readAll(ev.runId()).stream().map(EvidenceService::toSample).toList();
        } else {
            valueTimeline = List.of();
        }

        // Runtime events aren't run-tagged at the source (the supervisor writes runId=null) and
        // client connections carry no runId, so both are scoped by source + the run's time window.
        List<RuntimeEventRecord> events = new ArrayList<>();
        List<ErrorRecord> errors = new ArrayList<>();
        List<ClientConnectionRecord> clientRecords = new ArrayList<>();
        for (String sourceId : sourceIds) {
            for (RuntimeEventRow row : runtimeEvents.findByDataSource(sourceId)) {
                Instant at = toInstant(row.at());
                if (!inWindow(at, startedAt, endedAt)) {
                    continue;
                }
                events.add(new RuntimeEventRecord(row.type(), at, row.dataSourceId(), row.payloadJson()));
                if (isError(row.type())) {
                    errors.add(new ErrorRecord(row.type(), at, row.dataSourceId(),
                            readField(row.payloadJson(), "detail")));
                }
            }
            for (ClientConnectionRow conn : clients.findByDataSource(sourceId)) {
                Instant from = toInstant(conn.connectedAt());
                Instant to = toInstant(conn.disconnectedAt());
                if (!overlapsWindow(from, to, startedAt, endedAt)) {
                    continue;
                }
                clientRecords.add(new ClientConnectionRecord(conn.clientId(), conn.dataSourceId(), from, to));
            }
        }

        String scenarioId = run != null ? run.scenarioId() : null;
        ScenarioMetadata scenario = scenarioId == null ? null : new ScenarioMetadata(scenarioId, null);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceArtifactWriter.FORMAT_VERSION,
                run != null ? run.id() : ev.runId(),
                run != null ? run.kind() : null,
                run != null ? run.trigger() : null,
                run != null ? run.initiator() : null,
                startedAt, endedAt, completeness(run), sourceIds, scenarioId, recordingId);

        return new EvidenceContent(manifest, valueTimeline, events, clientRecords, scenario, List.of(), errors);
    }

    /**
     * Assembles, serializes (in the requested {@link EvidenceFormat}) and stores the
     * artifact, then records the terminal status: {@code READY} (or {@code PARTIAL} for
     * an incomplete run) on success, or {@code EXPORT_FAILED} if assembly/serialization/
     * storage fails (re-invoke to retry).
     */
    public EvidenceView export(String projectId, String evidenceId, EvidenceFormat format) {
        row(projectId, evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId));
        EvidenceArtifactWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalArgumentException("unsupported evidence format: " + format);
        }
        try {
            EvidenceContent content = assemble(evidenceId);
            byte[] artifact = serialize(content, writer);
            String key = "evidence/" + evidenceId + "/" + writer.artifactFilename();
            String ref = objectStore.put(
                    key, new ByteArrayInputStream(artifact), artifact.length, writer.contentType());
            evidence.updateManifest(evidenceId, json.writeValueAsString(content.manifest()));
            // evidence.status has no FAILED value (CAPTURING|READY|PARTIAL|EXPORT_FAILED); a
            // failed/incomplete run exports as PARTIAL, with manifest.completeness carrying the detail.
            String status = content.manifest().completeness() == Completeness.COMPLETE ? "READY" : "PARTIAL";
            return EvidenceView.from(evidence.updateStatus(evidenceId, status, ref));
        } catch (RuntimeException e) {
            // Export failure is a first-class state (UI shows it + offers retry); log so a
            // genuine fault isn't hidden behind the retryable status.
            log.log(System.Logger.Level.WARNING, "evidence export failed for " + evidenceId, e);
            return EvidenceView.from(evidence.updateStatus(evidenceId, "EXPORT_FAILED", null));
        }
    }

    private static byte[] serialize(EvidenceContent content, EvidenceArtifactWriter writer) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.write(content, buffer);
        return buffer.toByteArray();
    }

    private static Completeness completeness(RunRow run) {
        if (run == null) {
            return Completeness.PARTIAL;
        }
        return switch (run.state()) {
            case "COMPLETED" -> Completeness.COMPLETE;
            case "FAILED" -> Completeness.FAILED;
            default -> Completeness.PARTIAL;
        };
    }

    private static boolean isError(String type) {
        return type != null && type.startsWith("ERROR");
    }

    private static boolean inWindow(Instant at, Instant start, Instant end) {
        if (start != null && at.isBefore(start)) {
            return false;
        }
        return end == null || !at.isAfter(end);
    }

    private static boolean overlapsWindow(Instant from, Instant to, Instant start, Instant end) {
        if (end != null && from.isAfter(end)) {
            return false;
        }
        return start == null || to == null || !to.isBefore(start);
    }

    private static ValueSample toSample(NeutralValue value) {
        return new ValueSample(value.nodeId(), value.sourceTime(), value.value(),
                value.quality() == null ? null : value.quality().name(), value.qualityReason());
    }

    private static Instant toInstant(java.time.OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    /** Reads a top-level string field from a JSON object, or {@code null} if absent/unparseable. */
    private String readField(String objectJson, String field) {
        if (objectJson == null || objectJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = json.readTree(objectJson).path(field);
            return node.isMissingNode() || node.isNull() ? null : node.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
