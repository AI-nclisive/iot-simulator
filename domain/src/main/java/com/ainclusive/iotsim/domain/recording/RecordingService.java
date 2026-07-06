package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
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
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Recording lifecycle: create, capture values, finalize (backend-specs/03). */
@Service
public class RecordingService {

    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final CredentialStore credentials;
    private final SourceCapturer capturer;
    private final ProjectRepository projects;

    // Live captures in progress, keyed by data-source id (one capture per source).
    private final Map<String, ActiveCapture> active = new ConcurrentHashMap<>();

    public RecordingService(RecordingRepository recordings, ValueTimelineRepository timeline,
            DataSourceRepository dataSources, SchemaRepository schemas, CredentialStore credentials,
            SourceCapturer capturer, ProjectRepository projects) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.credentials = credentials;
        this.capturer = capturer;
        this.projects = projects;
    }

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
        return map(recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD",
                scanTypeStr, safeName, actor));
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
            Recording recording = map(recordings.create(
                    projectId, dsId, schema.version(), "SCAN_RECORD",
                    ScanType.SCHEMA_AND_DATA.name(), null, actor));
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
        return get(projectId, capture.recordingId());
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

    public Recording complete(String projectId, String recordingId) {
        RecordingRow recording = requireRecording(projectId, recordingId);
        long count = timeline.count(recordingId);
        return map(recordings.finalizeStats(
                recordingId, recording.timeStart(), recording.timeEnd(), count, 0L));
    }

    public List<Recording> list(String projectId) {
        return recordings.findByProject(projectId).stream().map(this::map).toList();
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
        return new Page<>(rows.stream().map(this::map).toList(), nextCursor, size);
    }

    public Recording get(String projectId, String recordingId) {
        return map(requireRecording(projectId, recordingId));
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

    /** Returns the schema captured for a recording (IS-137). */
    public RecordingSchema getRecordingSchema(String projectId, String recordingId) {
        RecordingRow rec = requireRecording(projectId, recordingId);
        SchemaWithNodes schema = schemas.findByVersion(rec.dataSourceId(), rec.schemaVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Schema",
                        rec.dataSourceId() + "@v" + rec.schemaVersion()));
        return new RecordingSchema(schema.nodes());
    }

    /** Schema snapshot linked to a recording (IS-137). */
    public record RecordingSchema(List<com.ainclusive.iotsim.protocolmodel.SchemaNode> nodes) {}

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

    private Recording map(RecordingRow r) {
        return new Recording(
                r.id(), r.projectId(), r.dataSourceId(), r.schemaVersion(), r.origin(),
                r.scanType(), r.name(), r.valueCount(), r.createdAt().toInstant(), r.createdBy(), r.version());
    }

    /** A live capture in progress: the recording it feeds and the session to stop. */
    private record ActiveCapture(String recordingId, CaptureSession session) {}
}
