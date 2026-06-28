package com.ainclusive.iotsim.platform.capture;

/**
 * A live capture could not be started or run: the protocol/runtime mode is
 * unsupported, or the real endpoint was unreachable or rejected the credentials.
 * Distinct from a bad request — the inputs may be valid but the source cannot be
 * captured right now.
 */
public class CaptureException extends RuntimeException {

    public CaptureException(String message) {
        super(message);
    }

    public CaptureException(String message, Throwable cause) {
        super(message, cause);
    }
}
