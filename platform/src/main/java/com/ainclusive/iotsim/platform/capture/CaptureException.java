package com.ainclusive.iotsim.platform.capture;

/**
 * A live capture could not be started or run. The {@link Kind} distinguishes why,
 * so the API can map it to the right status: a state conflict, a capability the
 * runtime does not provide, or a real source that is temporarily unreachable.
 */
public class CaptureException extends RuntimeException {

    /** Why a capture failed — drives the HTTP status the API returns. */
    public enum Kind {
        /** The source is already being captured (a state conflict). */
        CONFLICT,
        /** Capture is not available for this protocol or runtime mode. */
        UNSUPPORTED,
        /** The real source could not be reached, authenticated, or launched against. */
        UNAVAILABLE
    }

    private final Kind kind;

    public CaptureException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public CaptureException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
