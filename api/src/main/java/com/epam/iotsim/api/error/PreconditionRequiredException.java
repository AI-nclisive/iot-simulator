package com.epam.iotsim.api.error;

/** Raised when a mutating request omits the required {@code If-Match} header. */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
