package com.ainclusive.iotsim.domain.io;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.project.ProjectService;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleService;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Assembles and serializes a full project export (IS-073).
 *
 * <p>Gathers project metadata, data sources (no credentials), schemas,
 * recordings, and samples, then delegates ZIP serialization to
 * {@link ProjectZipExporter}. Returns a ready-to-stream {@link ProjectBundle}.
 */
@Service
public class ProjectExportService {

    private final ProjectService projects;
    private final DataSourceService dataSources;
    private final SchemaRepository schemas;
    private final RecordingService recordings;
    private final SampleService samples;
    private final ProjectZipExporter exporter;

    public ProjectExportService(ProjectService projects, DataSourceService dataSources,
            SchemaRepository schemas, RecordingService recordings, SampleService samples,
            ProjectZipExporter exporter) {
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.recordings = recordings;
        this.samples = samples;
        this.exporter = exporter;
    }

    /**
     * Exports the project identified by {@code projectId} as a versioned ZIP+manifest
     * bundle. Throws {@link ResourceNotFoundException} if the project does not exist.
     */
    public ProjectBundle export(String projectId) {
        Project project = projects.get(projectId);

        List<DataSource> dsList = dataSources.list(projectId);

        Map<String, SchemaWithNodes> schemaMap = new HashMap<>();
        for (DataSource ds : dsList) {
            schemas.findCurrent(ds.id()).ifPresent(s -> schemaMap.put(ds.id(), s));
        }

        List<Recording> recordingList = recordings.list(projectId);
        List<Sample> sampleList = samples.list(projectId);

        ProjectExportContent content = new ProjectExportContent(
                project, dsList, schemaMap, recordingList, sampleList);

        byte[] bytes = exporter.toBytes(content);
        String filename = "project-" + projectId + ".zip";
        return new ProjectBundle(new ByteArrayInputStream(bytes), ProjectBundle.CONTENT_TYPE, filename);
    }
}
