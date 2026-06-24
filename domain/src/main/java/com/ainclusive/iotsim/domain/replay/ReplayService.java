package com.ainclusive.iotsim.domain.replay;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Replays a recording through a (started) data-source by streaming its value
 * timeline to the runtime. Core of the Record -> Replay flow (backend-specs/03).
 */
@Service
public class ReplayService {

    private final DataSourceRepository dataSources;
    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;

    public ReplayService(DataSourceRepository dataSources, RecordingRepository recordings,
            ValueTimelineRepository timeline, SchemaRepository schemas, RuntimeController runtime) {
        this.dataSources = dataSources;
        this.recordings = recordings;
        this.timeline = timeline;
        this.schemas = schemas;
        this.runtime = runtime;
    }

    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        requireRecording(projectId, recordingId);

        List<NeutralValue> values = timeline.readAll(recordingId);
        runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source));
        long applied = runtime.applyValues(dataSourceId, values);
        return new ReplaySummary(recordingId, dataSourceId, applied);
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
}
