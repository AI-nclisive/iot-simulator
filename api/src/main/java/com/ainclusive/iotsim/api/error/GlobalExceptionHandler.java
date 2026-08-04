package com.ainclusive.iotsim.api.error;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
import com.ainclusive.iotsim.domain.common.PortInUseException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.RetentionDependencyException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.common.SchemaImpactException;
import com.ainclusive.iotsim.domain.common.SchemaVersionMismatchException;
import com.ainclusive.iotsim.domain.common.UnsupportedTypesException;
import com.ainclusive.iotsim.domain.io.ProjectImportException;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.runtime.RuntimeCapacityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** Maps domain/API exceptions to RFC 9457 problem responses (backend-specs/05). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ProblemDetail conflict(ConcurrencyConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PortInUseException.class)
    public ProblemDetail portInUse(PortInUseException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail preconditionRequired(PreconditionRequiredException e) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(SchemaVersionMismatchException.class)
    public ProblemDetail schemaVersionMismatch(SchemaVersionMismatchException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CaptureException.class)
    public ProblemDetail captureFailed(CaptureException e) {
        // The request is valid, but the source cannot be captured — map by cause:
        // a state conflict (already capturing), a capability the runtime lacks, or a
        // real source that is temporarily unreachable.
        HttpStatus status = switch (e.kind()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNSUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, e.getMessage());
    }

    @ExceptionHandler(RuntimeCapacityException.class)
    public ProblemDetail capacityExceeded(RuntimeCapacityException e) {
        // The request is valid; the supervisor is simply at its concurrent-worker cap.
        // Retryable once a running source is stopped or the cap is raised (IS-061).
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ProjectImportException.class)
    public ProblemDetail projectImportFailed(ProjectImportException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(FeatureNotAvailableException.class)
    public ProblemDetail featureNotAvailable(FeatureNotAvailableException e) {
        return problem(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
    }

    @ExceptionHandler(ScenarioInvalidException.class)
    public ProblemDetail scenarioInvalid(ScenarioInvalidException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("issues", e.issues());
        return pd;
    }

    @ExceptionHandler(SchemaImpactException.class)
    public ProblemDetail schemaImpact(SchemaImpactException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("issues", e.issues());
        return pd;
    }

    @ExceptionHandler(UnsupportedTypesException.class)
    public ProblemDetail unsupportedTypes(UnsupportedTypesException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("issues", e.issues());
        return pd;
    }

    @ExceptionHandler(RetentionDependencyException.class)
    public ProblemDetail retentionDependency(RetentionDependencyException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("issues", e.issues());
        return pd;
    }

    /**
     * Maps {@link AccessDeniedException} thrown by {@code @PreAuthorize} to RFC 9457 403
     * (backend-specs/05).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * A client that is gone (e.g. broken pipe) mid-{@code text/event-stream} response —
     * SSE endpoints like {@code Subscriber}/{@code SseEmitterSink} hit this whenever
     * {@code emitter.complete()}/{@code send()} is called on a connection Spring has
     * already marked unusable. The response is already committed to
     * {@code text/event-stream} (or gone outright), so writing a {@link ProblemDetail}
     * body here — as the generic {@link #internalError(Exception)} handler below would —
     * is itself impossible: Spring has no converter for {@code ProblemDetail} against a
     * preset {@code text/event-stream} content type, so it throws a second
     * {@code HttpMessageNotWritableException} while trying to report the first one,
     * masking the real cause in the logs (#704). There is nothing to report to a client
     * that is already gone, so just log at debug and write no body.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncRequestNotUsable(AsyncRequestNotUsableException e) {
        LOG.debug("Async request no longer usable (client likely disconnected mid-stream): {}",
                e.getMessage());
    }

    /**
     * Last-resort boundary for any exception not mapped above. Spring's own framework
     * exceptions (405 method-not-allowed, 415 unsupported media type, 406
     * not-acceptable, etc.) implement {@link ErrorResponse} and already carry the
     * correct status/body — defer to it. Anything else is an unanticipated failure
     * (e.g. I/O or persistence); log it server-side but never leak its message or
     * stack trace to the client — the client gets a generic, honest 500 instead of a
     * bare/opaque error page.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail internalError(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        LOG.error("Unhandled exception while processing request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing the request.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(detail);
        return pd;
    }
}
