package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.error.GlobalExceptionHandler;
import com.ainclusive.iotsim.platform.capture.CaptureException;
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
}
