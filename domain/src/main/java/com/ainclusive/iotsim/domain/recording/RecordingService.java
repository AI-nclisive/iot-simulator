package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.common.JsonField;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.RetentionDependencyException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository.ValueTimelineEntry;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.capture.CaptureSession;
import com.ainclusive.iotsim.platform.capture.CaptureSpec;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.secret.CredentialStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ScanType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Recording lifecycle: create, capture values, finalize (backend-specs/03). */
@Service
public class RecordingService {

    private static final TypeReference<List<SchemaNode>> SCHEMA_NODE_LIST =
            new TypeReference<>() {};

    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final CredentialStore credentials;
    private final SourceCapturer capturer;
    private final ProjectRepository projects;
    private final ActivityEventService activity;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;

    // Live captures in progress, keyed by data-source id (one capture per source).
    private final Map<String, ActiveCapture> active = new ConcurrentHashMap<>();

    public RecordingService(RecordingRepository recordings, ValueTimelineRepository timeline,
            DataSourceRepository dataSources, SchemaRepository schemas, CredentialStore credentials,
            SourceCapturer capturer, ProjectRepository projects, ActivityEventService activity,
            ScenarioRepository scenarios, RunRepository runs, EvidenceRepository evidence,
            ObjectMapper json) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.credentials = credentials;
        this.capturer = capturer;
        this.projects = projects;
        this.activity = activity;
        this.scenarios = scenarios;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
    }

    @Transactional
    public Recording create(String projectId, String dataSourceId, ScanType scanType,
            String name, String actor) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        int schemaVersion = source.schemaVersion() == null ? 0 : source.schemaVersion();
        String scanTypeStr = scanType != null ? scanType.name() : ScanType.SCHEMA_AND_DATA.name();
        String trimmedName = (name != null && !name.isBlank()) ? name.trim() : null;
        if (trimmedName != null && trimmedName.length() > 255) {
            throw new IllegalArgumentException("Recording name must not exceed 255 characters");
        }
        String safeName = trimmedName;
        // Capture the source's current schema snapshot with the recording (IS-161) so
        // schema-serving never depends on a live lookup against dataSourceId later.
        String schemaNodesJson = schemas.findCurrent(dataSourceId)
                .map(s -> json.writeValueAsString(s.nodes()))
                .orElse("[]");
        RecordingRow row = recordings.create(projectId, dataSourceId, source.protocol(), schemaVersion,
                "SCAN_RECORD", scanTypeStr, safeName, actor, schemaNodesJson);
        activity.emit(projectId, actor, "create", "recording", row.id());
        return map(row);
    }

    /** Appends captured values; skipped entirely when the recording is {@code SCHEMA_ONLY}. */
    public long appendValues(String projectId, String recordingId, List<NeutralValue> values) {
        RecordingRow rec = requireRecording(projectId, recordingId);
        if (ScanType.SCHEMA_ONLY.name().equals(rec.scanType())) {
            return 0;
        }
        return timeline.append(recordingId, values);
    }

    /**
     * Starts live capture from a running real source (record real data, IS-045):
     * connects to the source's real endpoint in client mode and streams every
     * observed value change into a new recording until {@link #stopCapture} is
     * called. The recording is captured against the source's current schema version.
     *
     * @throws IllegalArgumentException if the source has no endpoint or schema to capture
     * @throws CaptureException if a capture is already running for the source, the
     *     real source is unreachable, or capture is unsupported in this runtime mode
     */
    public Recording startCapture(String projectId, String dataSourceId, String actor) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        if (source.realDeviceEndpoint() == null || source.realDeviceEndpoint().isBlank()) {
            throw new IllegalArgumentException("data source has no real-device endpoint to capture from");
        }
        SchemaWithNodes schema = schemas.findCurrent(dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "data source has no schema to capture against"));
        boolean hasVariables = schema.nodes().stream().anyMatch(n -> n.kind() == NodeKind.VARIABLE);
        if (!hasVariables) {
            throw new IllegalArgumentException("schema has no variables to capture");
        }
        // computeIfAbsent serializes concurrent starts for the same source: only the
        // first creates the recording and opens the session; a second start sees the
        // existing mapping and is rejected below. If the session fails to open, the
        // lambda throws and no mapping is added (the empty recording row remains as a
        // record of the attempt — deletion is out of scope until a delete path exists).
        boolean[] started = {false};
        ActiveCapture capture = active.computeIfAbsent(dataSourceId, dsId -> {
            started[0] = true;
            String schemaNodesJson = json.writeValueAsString(schema.nodes());
            Recording recording = map(recordings.create(
                    projectId, dsId, source.protocol(), schema.version(), "SCAN_RECORD",
                    ScanType.SCHEMA_AND_DATA.name(), defaultCaptureName(source.name()), actor,
                    schemaNodesJson));
            CaptureSpec spec = new CaptureSpec(source.protocol(), source.realDeviceEndpoint(),
                    credentials.find(dsId).orElse(null), schema.version(), schema.nodes());
            CaptureSession session = capturer.startCapture(
                    spec, values -> timeline.append(recording.id(), values));
            return new ActiveCapture(recording.id(), session);
        });
        if (!started[0]) {
            throw new CaptureException(
                    CaptureException.Kind.CONFLICT, "a capture is already running for this data source");
        }
        activity.emit(projectId, actor, "start_capture", "recording", capture.recordingId());
        return get(projectId, capture.recordingId());
    }

    private static final DateTimeFormatter DEFAULT_NAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneOffset.UTC);

    /**
     * A default name for a capture-started recording (IS-167): recordings had no name
     * at all before this, so every list/picker screen fell back to something else
     * (the source's name, a truncated id, ...) and two captures from the same source
     * were indistinguishable. Mirrors the date format the frontend already uses
     * elsewhere (e.g. {@code 20 Jul 2026, 15:30}).
     */
    private static String defaultCaptureName(String sourceName) {
        return sourceName + " — " + DEFAULT_NAME_TIMESTAMP.format(Instant.now());
    }

    /**
     * Stops the live capture running for a data source and finalizes its recording.
     *
     * @throws IllegalArgumentException if no capture is running for the source
     */
    public Recording stopCapture(String projectId, String dataSourceId) {
        requireSource(projectId, dataSourceId);
        ActiveCapture capture = active.remove(dataSourceId);
        if (capture == null) {
            throw new IllegalArgumentException("no active capture for this data source");
        }
        capture.session().stop();
        return complete(projectId, capture.recordingId());
    }

    /**
     * Reports whether a live capture is currently running for a data source (IS-166),
     * so a client can discover an in-progress (or orphaned, e.g. left running after a
     * page reload) capture instead of only finding out via a rejected {@code start}.
     */
    public CaptureStatus captureStatus(String projectId, String dataSourceId) {
        requireSource(projectId, dataSourceId);
        ActiveCapture capture = active.get(dataSourceId);
        return capture == null
                ? new CaptureStatus(false, null)
                : new CaptureStatus(true, capture.recordingId());
    }

    /** Whether a capture is active for a data source and, if so, which recording it feeds. */
    public record CaptureStatus(boolean capturing, String recordingId) {}

    public Recording complete(String projectId, String recordingId) {
        RecordingRow recording = requireRecording(projectId, recordingId);
        long count = timeline.count(recordingId);
        long sizeBytes = timeline.sumBytes(recordingId);
        RecordingRow updated = recordings.finalizeStats(
                recordingId, recording.timeStart(), recording.timeEnd(), count, sizeBytes);
        UsageContext usage = usageContext(projectId);
        return map(updated, usage.lastUsedAt(recordingId), usage.hasDependents(recordingId));
    }

    /**
     * Deletes a recording and its captured values (IS-092: retention & cleanup).
     * Rejected with {@link RetentionDependencyException} if a scenario step still
     * targets it (by {@code params.recordingId}) or an active/queued run was started
     * from it — dropping the recording out from under either would break replay.
     * A no-op (no activity emitted) if the row was already deleted by a concurrent
     * request that won the race.
     */
    @Transactional
    public void delete(String projectId, String recordingId, String actor) {
        requireRecording(projectId, recordingId);
        List<String> issues = usageContext(projectId).issuesFor(recordingId);
        if (!issues.isEmpty()) {
            throw new RetentionDependencyException(recordingId, issues);
        }
        timeline.deleteByRecording(recordingId);
        if (recordings.deleteById(recordingId)) {
            activity.emit(projectId, actor, "delete", "recording", recordingId);
        }
    }

    /**
     * Recording count for a project without the usage/dependency scan below — used by
     * surfaces (e.g. the project overview dashboard) that only need "how many" (IS-092).
     */
    public long count(String projectId) {
        return recordings.countByProject(projectId);
    }

    /**
     * Every recording's last-replay time and active (scenario/run) references for a
     * project, computed in one pass: one {@code runs.findByProject} plus one batched
     * {@code evidence.findByProject} (not a DB round-trip per run), shared by
     * {@code list}, {@code listPaged}, {@code get}, and {@code complete} (IS-092).
     */
    private UsageContext usageContext(String projectId) {
        Map<String, EvidenceRow> evidenceById = evidence.findByProject(projectId).stream()
                .collect(Collectors.toMap(EvidenceRow::id, e -> e));
        Map<String, Instant> lastUsed = new HashMap<>();
        List<DependencyRef> activeRefs = new ArrayList<>();
        for (RunRow run : runs.findByProject(projectId)) {
            if (run.evidenceId() == null) {
                continue;
            }
            EvidenceRow ev = evidenceById.get(run.evidenceId());
            String recordingId = ev != null ? recordingIdField(ev.manifestJson()) : null;
            if (recordingId == null) {
                continue;
            }
            Instant at = run.startedAt() != null ? run.startedAt().toInstant() : run.createdAt().toInstant();
            lastUsed.merge(recordingId, at, (a, b) -> a.isAfter(b) ? a : b);
            if ("RUNNING".equals(run.state()) || "QUEUED".equals(run.state())) {
                activeRefs.add(new DependencyRef(recordingId, "referenced by an active run (" + run.id() + ")"));
            }
        }
        for (ScenarioRow scenario : scenarios.findByProject(projectId)) {
            for (ScenarioStepRow step : scenario.steps()) {
                String recordingId = "REPLAY".equals(step.type())
                        ? JsonField.text(json, step.params(), "recordingId") : null;
                if (recordingId != null) {
                    activeRefs.add(new DependencyRef(recordingId,
                            "referenced by step " + step.ordinal() + " of scenario \"" + scenario.name() + "\""));
                }
            }
        }
        return new UsageContext(lastUsed, activeRefs);
    }

    /**
     * Extracts the {@code recordingId} field written into a run's evidence manifest by
     * {@code ReplayService}/{@code ReplayLiveRunService}'s {@code manifest()}/{@code seed()}
     * helpers when a replay starts — the only producers of this key. Renaming it there
     * without updating this reader silently stops matching (IS-092).
     */
    private String recordingIdField(String manifestJson) {
        return JsonField.text(json, manifestJson, "recordingId");
    }

    /** One recording's active (blocking) reference, with a human-readable reason. */
    private record DependencyRef(String recordingId, String description) {}

    /** Per-project usage snapshot shared across the read paths below (IS-092). */
    private record UsageContext(Map<String, Instant> lastUsedAt, List<DependencyRef> activeDependents) {
        Instant lastUsedAt(String recordingId) {
            return lastUsedAt.get(recordingId);
        }

        boolean hasDependents(String recordingId) {
            return activeDependents.stream().anyMatch(ref -> ref.recordingId().equals(recordingId));
        }

        List<String> issuesFor(String recordingId) {
            return activeDependents.stream()
                    .filter(ref -> ref.recordingId().equals(recordingId))
                    .map(DependencyRef::description)
                    .toList();
        }
    }

    public List<Recording> list(String projectId) {
        UsageContext usage = usageContext(projectId);
        return recordings.findByProject(projectId).stream()
                .map(r -> map(r, usage.lastUsedAt(r.id()), usage.hasDependents(r.id())))
                .toList();
    }

    public Page<Recording> listPaged(String projectId, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<RecordingRow> rows = recordings.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            RecordingRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        UsageContext usage = usageContext(projectId);
        return new Page<>(rows.stream()
                .map(r -> map(r, usage.lastUsedAt(r.id()), usage.hasDependents(r.id())))
                .toList(), nextCursor, size);
    }

    public Recording get(String projectId, String recordingId) {
        RecordingRow row = requireRecording(projectId, recordingId);
        UsageContext usage = usageContext(projectId);
        return map(row, usage.lastUsedAt(recordingId), usage.hasDependents(recordingId));
    }

    /**
     * Keyset-paginated read of captured values for a recording (IS-134).
     * Cursor encodes the last-seen SEQ as a base64url string.
     */
    public RecordingValuesPage listValues(String projectId, String recordingId,
            String cursor, Integer limit, ValueFilter filter) {
        RecordingRow rec = requireRecording(projectId, recordingId);
        int size = Math.min(limit != null && limit > 0 ? limit : 200, 1000);
        long afterSeq = decodeValueCursor(cursor);
        List<ValueTimelineEntry> raw = timeline.readPage(recordingId, afterSeq, size + 1, filter);
        String nextCursor = null;
        if (raw.size() > size) {
            raw = raw.subList(0, size);
            nextCursor = encodeValueCursor(raw.get(raw.size() - 1).seq());
        }
        List<RecordingValue> items = raw.stream().map(e -> new RecordingValue(
                e.value().nodeId(),
                e.parameterPath(),
                e.value().sourceTime(),
                e.value().value() != null ? String.valueOf(e.value().value()) : null,
                e.value().quality().name())).toList();
        long total = filter.isBlank() ? rec.valueCount() : timeline.countFiltered(recordingId, filter);
        return new RecordingValuesPage(items, nextCursor, total);
    }

    /** Paginated response for the value browse API (IS-134). */
    public record RecordingValuesPage(
            List<RecordingValue> items, String nextCursor, long total) {}

    /**
     * Returns the schema captured for a recording (IS-137), read from the recording's
     * own stored schema snapshot (IS-161) rather than a live lookup against the
     * (possibly gone) data source — a recording is self-contained and its schema never
     * changes underneath it once captured. Throws {@link ResourceNotFoundException} only
     * when the stored snapshot is empty (e.g. an ancient pre-IS-161 recording, or one
     * whose schema could not be resolved at capture/import time).
     */
    public RecordingSchema getRecordingSchema(String projectId, String recordingId) {
        RecordingRow rec = requireRecording(projectId, recordingId);
        List<SchemaNode> nodes = readSchemaNodes(rec.schemaNodesJson());
        if (nodes.isEmpty()) {
            throw new ResourceNotFoundException("Schema",
                    (rec.dataSourceId() != null ? rec.dataSourceId() : recordingId) + "@v" + rec.schemaVersion());
        }
        return new RecordingSchema(nodes);
    }

    private List<SchemaNode> readSchemaNodes(String schemaNodesJson) {
        if (schemaNodesJson == null || schemaNodesJson.isBlank()) {
            return List.of();
        }
        return json.readValue(schemaNodesJson, SCHEMA_NODE_LIST);
    }

    /** Schema snapshot linked to a recording (IS-137). */
    public record RecordingSchema(List<SchemaNode> nodes) {}

    private static long decodeValueCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return -1L;
        }
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(cursor);
            return Long.parseLong(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor", e);
        }
    }

    private static String encodeValueCursor(long seq) {
        byte[] bytes = Long.toString(seq).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A single captured value row returned by the browse API (IS-134). */
    public record RecordingValue(String parameterId, String parameterPath,
            java.time.Instant timestamp, String value, String quality) {}

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private DataSourceRow requireSource(String projectId, String dataSourceId) {
        return dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
    }

    private RecordingRow requireRecording(String projectId, String recordingId) {
        return recordings.findById(recordingId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Recording", recordingId));
    }

    /** Maps a freshly created/updated row with no usage context yet (create, startCapture). */
    private Recording map(RecordingRow r) {
        return map(r, null, false);
    }

    private Recording map(RecordingRow r, Instant lastUsedAt, boolean hasDependents) {
        return new Recording(
                r.id(), r.projectId(), r.dataSourceId(), r.protocol(), r.schemaVersion(), r.origin(),
                r.scanType(), r.name(), r.valueCount(), r.sizeBytes(), r.createdAt().toInstant(),
                r.createdBy(), r.version(), lastUsedAt, hasDependents);
    }

    /** A live capture in progress: the recording it feeds and the session to stop. */
    private record ActiveCapture(String recordingId, CaptureSession session) {}
}
