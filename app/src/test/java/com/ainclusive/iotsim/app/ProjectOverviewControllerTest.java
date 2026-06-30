package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.project.ProjectOverviewController;
import com.ainclusive.iotsim.api.project.ProjectOverviewController.ProjectOverviewResponse;
import com.ainclusive.iotsim.domain.project.ProjectOverview;
import com.ainclusive.iotsim.domain.project.ProjectOverviewService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ProjectOverviewController}. */
class ProjectOverviewControllerTest {

    private final ProjectOverviewService service = mock(ProjectOverviewService.class);
    private final ProjectOverviewController controller = new ProjectOverviewController(service);

    @Test
    void overviewMapsDomainRollupsToResponseFields() {
        given(service.overview()).willReturn(List.of(
                new ProjectOverview("p1", "Line 1", 5, 2, 3, 1),
                new ProjectOverview("p2", "Line 2", 0, 0, 0, 0)));

        List<ProjectOverviewResponse> resp = controller.overview();

        assertThat(resp).hasSize(2);
        ProjectOverviewResponse first = resp.get(0);
        assertThat(first.projectId()).isEqualTo("p1");
        assertThat(first.name()).isEqualTo("Line 1");
        assertThat(first.configuredSources()).isEqualTo(5);
        assertThat(first.runningSources()).isEqualTo(2);
        assertThat(first.reusableArtifacts()).isEqualTo(3);
        assertThat(first.sourcesNeedingAttention()).isEqualTo(1);
    }

    @Test
    void overviewReturnsEmptyListWhenNoProjects() {
        given(service.overview()).willReturn(List.of());
        assertThat(controller.overview()).isEmpty();
    }
}
