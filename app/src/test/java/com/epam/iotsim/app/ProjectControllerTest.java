package com.epam.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.epam.iotsim.api.error.GlobalExceptionHandler;
import com.epam.iotsim.api.error.PreconditionRequiredException;
import com.epam.iotsim.api.project.ProjectController;
import com.epam.iotsim.api.project.ProjectController.CreateProjectRequest;
import com.epam.iotsim.api.project.ProjectController.ProjectResponse;
import com.epam.iotsim.api.project.ProjectController.UpdateProjectRequest;
import com.epam.iotsim.domain.common.ConcurrencyConflictException;
import com.epam.iotsim.domain.common.ResourceNotFoundException;
import com.epam.iotsim.domain.project.Project;
import com.epam.iotsim.domain.project.ProjectService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link ProjectController} and {@link GlobalExceptionHandler}. */
class ProjectControllerTest {

    private ProjectService service;
    private ProjectController controller;
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        service = mock(ProjectService.class);
        controller = new ProjectController(service);
    }

    private static Project sample(long version) {
        Instant now = Instant.now();
        return new Project("p1", "Line 1", "desc",
                Project.ProjectStatus.ACTIVE, now, now, "local", version);
    }

    @Test
    void createReturns201WithEtag() {
        given(service.create(any(), any(), any())).willReturn(sample(0));
        ResponseEntity<ProjectResponse> resp =
                controller.create(new CreateProjectRequest("Line 1", "desc"));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().id()).isEqualTo("p1");
        assertThat(resp.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    void createWithBlankNameThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(new CreateProjectRequest("  ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsEtagOfCurrentVersion() {
        given(service.get("p1")).willReturn(sample(3));
        ResponseEntity<ProjectResponse> resp = controller.get("p1");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"3\"");
    }

    @Test
    void getMissingPropagatesNotFound() {
        given(service.get("missing")).willThrow(new ResourceNotFoundException("Project", "missing"));
        assertThatThrownBy(() -> controller.get("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithoutIfMatchThrowsPreconditionRequired() {
        assertThatThrownBy(() -> controller.update("p1", null, new UpdateProjectRequest("x", null)))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void updateWithStaleVersionPropagatesConflict() {
        given(service.update(eq("p1"), any(), any(), eq(1L)))
                .willThrow(new ConcurrencyConflictException("Project", "p1", 1));
        assertThatThrownBy(() -> controller.update("p1", "\"1\"", new UpdateProjectRequest("x", null)))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void updateHappyPathReturnsNewEtag() {
        given(service.update(eq("p1"), any(), any(), eq(0L))).willReturn(sample(1));
        ResponseEntity<ProjectResponse> resp =
                controller.update("p1", "\"0\"", new UpdateProjectRequest("x", null));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void deleteReturns204() {
        assertThat(controller.delete("p1").getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void exceptionHandlerMapsStatuses() {
        assertThat(handler.notFound(new ResourceNotFoundException("Project", "x")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(handler.conflict(new ConcurrencyConflictException("Project", "x", 1)).getStatus())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(handler.preconditionRequired(new PreconditionRequiredException("x")).getStatus())
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED.value());
        assertThat(handler.badRequest(new IllegalArgumentException("x")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
