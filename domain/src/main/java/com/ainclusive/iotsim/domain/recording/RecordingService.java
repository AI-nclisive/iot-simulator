package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import org.springframework.stereotype.Service;

/** Recording lifecycle: create, capture values, finalize (backend-specs/03). */
@Service
public class RecordingService {

    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final DataSourceRepository dataSources;

    public RecordingService(RecordingRepository recordings, ValueTimelineRepository timeline,
            DataSourceRepository dataSources) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.dataSources = dataSources;
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
}
