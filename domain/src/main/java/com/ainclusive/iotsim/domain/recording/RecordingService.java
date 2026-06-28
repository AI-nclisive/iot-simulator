package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.capture.CaptureSession;
import com.ainclusive.iotsim.platform.capture.CaptureSpec;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.secret.CredentialStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
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

    // Live captures in progress, keyed by data-source id (one capture per source).
    private final Map<String, ActiveCapture> active = new ConcurrentHashMap<>();

    public RecordingService(RecordingRepository recordings, ValueTimelineRepository timeline,
            DataSourceRepository dataSources, SchemaRepository schemas, CredentialStore credentials,
            SourceCapturer capturer) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.credentials = credentials;
        this.capturer = capturer;
    }

    public Recording create(String projectId, String dataSourceId, String actor) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        int schemaVersion = source.schemaVersion() == null ? 0 : source.schemaVersion();
        return map(recordings.create(projectId, dataSourceId, schemaVersion, "SCAN_RECORD", actor));
    }

    /** Appends captured values; used by the source reader (and tests). */
    public long appendValues(String projectId, String recordingId, List<NeutralValue> values) {
        requireRecording(projectId, recordingId);
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
        if (source.endpoint() == null || source.endpoint().isBlank()) {
            throw new IllegalArgumentException("data source has no endpoint to capture from");
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
                    projectId, dsId, schema.version(), "SCAN_RECORD", actor));
            CaptureSpec spec = new CaptureSpec(source.protocol(), source.endpoint(),
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

    public Recording get(String projectId, String recordingId) {
        return map(requireRecording(projectId, recordingId));
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
                r.valueCount(), r.createdAt().toInstant(), r.createdBy(), r.version());
    }

    /** A live capture in progress: the recording it feeds and the session to stop. */
    private record ActiveCapture(String recordingId, CaptureSession session) {}
}
