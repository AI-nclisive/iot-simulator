package com.ainclusive.iotsim.platform.scan;

/**
 * Outcome of a real-source connection probe or scan. Mirrors the states named in
 * {@code backend-specs/05_API_CONTRACT.md} §Scan.
 */
public enum ScanStatus {
    /** Reachable/authenticated; a scan returned the full discovered structure. */
    OK,
    /** A scan returned partial results (e.g. a large schema hit the node cap). */
    PARTIAL,
    /** The endpoint could not be reached (down, refused, timed out, bad address). */
    UNREACHABLE,
    /** The endpoint was reached but rejected the supplied credentials. */
    AUTH_FAILURE,
    /** No scanner is available for the protocol / runtime mode. */
    UNSUPPORTED
}
