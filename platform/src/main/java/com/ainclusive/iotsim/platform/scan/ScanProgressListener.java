package com.ainclusive.iotsim.platform.scan;

/** Reports in-flight scan progress; {@code discoveredSoFar} is only meaningful while {@link ScanPhase#SCANNING}. */
@FunctionalInterface
public interface ScanProgressListener {
    void onProgress(ScanPhase phase, int discoveredSoFar);
}
