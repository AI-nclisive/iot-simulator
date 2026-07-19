package com.ainclusive.iotsim.domain.project;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Aggregates per-project rollups for the workspace overview (IS-054): configured
 * and running data-source counts, reusable-artifact (recording) counts, and an
 * attention count of sources in an unhealthy runtime state (ERROR or STALE).
 * Composes the existing project, data-source and recording services so runtime
 * state uses the same source of truth as the data-source API.
 * See backend-specs/05_API_CONTRACT.md and the IS-054 design doc.
 */
@Service
public class ProjectOverviewService {

    private final ProjectService projects;
    private final DataSourceService dataSources;
    private final RecordingService recordings;

    public ProjectOverviewService(ProjectService projects, DataSourceService dataSources,
            RecordingService recordings) {
        this.projects = projects;
        this.dataSources = dataSources;
        this.recordings = recordings;
    }

    /** One rollup per project, in {@link ProjectService#list()} order. */
    public List<ProjectOverview> overview() {
        return projects.list().stream().map(this::aggregate).toList();
    }

    private ProjectOverview aggregate(Project project) {
        List<DataSource> sources = dataSources.list(project.id());
        int running = (int) sources.stream()
                .filter(s -> s.runtimeState() == RuntimeState.RUNNING)
                .count();
        int attention = (int) sources.stream()
                .filter(s -> s.runtimeState() == RuntimeState.ERROR
                        || s.runtimeState() == RuntimeState.STALE)
                .count();
        int artifacts = (int) recordings.count(project.id());
        return new ProjectOverview(
                project.id(), project.name(), sources.size(), running, artifacts, attention);
    }
}
