package com.epam.iotsim.api.error;

import com.epam.iotsim.domain.common.ConcurrencyConflictException;
import com.epam.iotsim.domain.common.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/API exceptions to RFC 9457 problem responses (backend-specs/05). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ProblemDetail conflict(ConcurrencyConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail preconditionRequired(PreconditionRequiredException e) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(detail);
        return pd;
    }
}
