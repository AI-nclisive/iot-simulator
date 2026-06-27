package com.ainclusive.iotsim.platform.scan;

/**
 * Default scanner for local/dev mode where no out-of-process workers are launched
 * (mirrors {@code InMemoryRuntimeController}). Real-source discovery needs a worker
 * in client mode, so this reports {@link ScanStatus#UNSUPPORTED} rather than
 * pretending to reach anything.
 */
public class UnsupportedSourceScanner implements SourceScanner {

    private static final String MESSAGE =
            "real-source scanning requires supervisor runtime mode (iotsim.runtime.mode=supervisor)";

    @Override
    public ConnectionTestResult testConnection(ScanSpec spec) {
        return new ConnectionTestResult(ScanStatus.UNSUPPORTED, MESSAGE);
    }

    @Override
    public ScanResult scan(ScanSpec spec) {
        return ScanResult.failure(ScanStatus.UNSUPPORTED, MESSAGE);
    }
}
