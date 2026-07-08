package com.ainclusive.iotsim.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.activityevent.NoOpActivityEventRepository;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectOverviewServiceTest {

    @Test
    void aggregatesCountsRunningAttentionAndArtifactsForAProject() {
        Map<String, List<DataSource>> sources = Map.of("p1", List.of(
                source("a", RuntimeState.RUNNING),
                source("b", RuntimeState.RUNNING),
                source("c", RuntimeState.ERROR),
                source("d", RuntimeState.STALE),
                source("e", RuntimeState.STOPPED)));
        Map<String, List<Recording>> recs = Map.of("p1", List.of(
                recording("r1"), recording("r2"), recording("r3")));

        ProjectOverviewService service = service(List.of(project("p1", "Line 1")), sources, recs);

        List<ProjectOverview> overview = service.overview();

        assertThat(overview).hasSize(1);
        ProjectOverview o = overview.get(0);
        assertThat(o.projectId()).isEqualTo("p1");
        assertThat(o.name()).isEqualTo("Line 1");
        assertThat(o.configuredSources()).isEqualTo(5);
        assertThat(o.runningSources()).isEqualTo(2);
        assertThat(o.sourcesNeedingAttention()).isEqualTo(2); // ERROR + STALE only
        assertThat(o.reusableArtifacts()).isEqualTo(3);
    }

    @Test
    void returnsOneRowPerProjectInListOrderWithIndependentTallies() {
        Map<String, List<DataSource>> sources = Map.of(
                "p1", List.of(source("a", RuntimeState.RUNNING)),
                "p2", List.of(source("b", RuntimeState.STOPPED), source("c", RuntimeState.RUNNING)));
        Map<String, List<Recording>> recs = Map.of(
                "p1", List.of(),
                "p2", List.of(recording("r1")));

        ProjectOverviewService service = service(
                List.of(project("p1", "First"), project("p2", "Second")), sources, recs);

        List<ProjectOverview> overview = service.overview();

        assertThat(overview).extracting(ProjectOverview::projectId).containsExactly("p1", "p2");
        assertThat(overview.get(0).runningSources()).isEqualTo(1);
        assertThat(overview.get(1).configuredSources()).isEqualTo(2);
        assertThat(overview.get(1).runningSources()).isEqualTo(1);
        assertThat(overview.get(1).reusableArtifacts()).isEqualTo(1);
    }

    @Test
    void emptyProjectListYieldsEmptyOverview() {
        assertThat(service(List.of(), Map.of(), Map.of()).overview()).isEmpty();
    }

    // --- test doubles: override only the read methods the aggregator calls ---

    private static ProjectOverviewService service(List<Project> projects,
            Map<String, List<DataSource>> sourcesByProject,
            Map<String, List<Recording>> recordingsByProject) {
        ProjectService projectService = new ProjectService(null, null, null, null) {
            @Override
            public List<Project> list() {
                return projects;
            }
        };
        DataSourceService dataSourceService = new DataSourceService(null, null, null, null, null, null, "localhost", new ActivityEventService(new NoOpActivityEventRepository())) {
            @Override
            public List<DataSource> list(String projectId) {
                return sourcesByProject.getOrDefault(projectId, List.of());
            }
        };
        RecordingService recordingService = new RecordingService(null, null, null, null, null, null, null, new ActivityEventService(new NoOpActivityEventRepository())) {
            @Override
            public List<Recording> list(String projectId) {
                return recordingsByProject.getOrDefault(projectId, List.of());
            }
        };
        return new ProjectOverviewService(projectService, dataSourceService, recordingService);
    }

    private static Project project(String id, String name) {
        Instant now = Instant.now();
        return new Project(id, name, null, Project.ProjectStatus.ACTIVE, now, now, "local", 0);
    }

    private static DataSource source(String id, RuntimeState state) {
        Instant now = Instant.now();
        return new DataSource(id, "p1", "src-" + id, Protocol.OPC_UA, SourceBasis.MANUAL,
                null, null, 0, null, "{}", null, false, state, CredentialState.MISSING,
                "opc.tcp://localhost:0/iotsim", now, now, "local", 0);
    }

    private static Recording recording(String id) {
        return new Recording(id, "p1", "ds", 1, "SCAN_RECORD", "SCHEMA_AND_DATA", null, 0L, Instant.now(), "local", 0);
    }
}
