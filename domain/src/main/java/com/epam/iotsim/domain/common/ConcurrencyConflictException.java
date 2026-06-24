package com.epam.iotsim.domain.common;

/** An optimistic-concurrency version check failed. Mapped to HTTP 409. */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String resource, String id, long expectedVersion) {
        super(resource + " " + id + " was modified concurrently (expected version " + expectedVersion + ")");
    }
}
