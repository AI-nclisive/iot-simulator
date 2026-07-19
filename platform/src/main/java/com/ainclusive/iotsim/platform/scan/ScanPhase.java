package com.ainclusive.iotsim.platform.scan;

/** In-flight phase of a running scan, reported via {@link ScanProgressListener}. */
public enum ScanPhase {
    CONNECTING,
    CONNECTED,
    SCANNING
}
