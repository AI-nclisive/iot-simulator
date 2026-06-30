package com.ainclusive.iotsim.domain.io;

/**
 * Thrown when a project import ZIP is malformed, unreadable, or uses an
 * unsupported format version (IS-073, IS-091).
 *
 * <p>Maps to HTTP 422 (Unprocessable Entity) in the API layer.
 */
public class ProjectImportException extends RuntimeException {

    public ProjectImportException(String message) {
        super(message);
    }

    public ProjectImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
