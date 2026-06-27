package com.ainclusive.iotsim.platform.scan;

/** Result of a reachability/auth probe against a real source. */
public record ConnectionTestResult(ScanStatus status, String message) {

    public boolean ok() {
        return status == ScanStatus.OK;
    }
}
