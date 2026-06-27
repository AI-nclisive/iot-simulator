package com.ainclusive.iotsim.platform.scan;

import java.util.List;

/**
 * The discovered structure of a real source, plus the states the UI surfaces:
 * {@code truncated} (large schema), {@code unknownCount} (unknown types), and the
 * overall {@link ScanStatus}. Never contains secrets (backend-specs/08).
 */
public record ScanResult(ScanStatus status, List<DiscoveredNode> nodes, boolean truncated,
        int unknownCount, String message) {

    public ScanResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public static ScanResult failure(ScanStatus status, String message) {
        return new ScanResult(status, List.of(), false, 0, message);
    }
}
