package com.ainclusive.iotsim.platform.capture;

/**
 * A live capture in progress. The caller holds the handle and {@link #stop()}s it
 * to end capture (cancels the worker stream and tears the client down). Stopping
 * is idempotent.
 */
public interface CaptureSession {

    /** Ends the capture; safe to call more than once. */
    void stop();
}
