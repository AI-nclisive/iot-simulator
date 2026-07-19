package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.error.GlobalExceptionHandler;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** Verifies {@link GlobalExceptionHandler} maps capture failures to the right status. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void captureConflictMapsTo409() {
        ProblemDetail pd = handler.captureFailed(
                new CaptureException(CaptureException.Kind.CONFLICT, "already capturing"));
        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getDetail()).isEqualTo("already capturing");
    }

    @Test
    void captureUnsupportedMapsTo501() {
        ProblemDetail pd = handler.captureFailed(
                new CaptureException(CaptureException.Kind.UNSUPPORTED, "needs supervisor mode"));
        assertThat(pd.getStatus()).isEqualTo(501);
    }

    @Test
    void captureUnavailableMapsTo503() {
        ProblemDetail pd = handler.captureFailed(
                new CaptureException(CaptureException.Kind.UNAVAILABLE, "endpoint unreachable"));
        assertThat(pd.getStatus()).isEqualTo(503);
    }

    @Test
    void runtimeCapacityMapsTo503() {
        ProblemDetail pd = handler.capacityExceeded(
                new RuntimeCapacityException("concurrent-source cap reached (50)"));
        assertThat(pd.getStatus()).isEqualTo(503);
        assertThat(pd.getDetail()).isEqualTo("concurrent-source cap reached (50)");
    }

    @Test
    void unmappedExceptionMapsTo500WithGenericMessage() {
        ProblemDetail pd = handler.internalError(
                new RuntimeException("some internal detail that must not leak"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail())
                .isEqualTo("An unexpected error occurred while processing the request.")
                .doesNotContain("internal detail");
    }
}
