package com.ainclusive.iotsim.platform.capture;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import java.util.function.Consumer;

/**
 * Default capturer for local/dev mode where no out-of-process workers are launched
 * (mirrors {@link com.ainclusive.iotsim.platform.scan.UnsupportedSourceScanner}).
 * Live capture needs a worker in client mode, so this refuses rather than
 * pretending to record anything.
 */
public class UnsupportedSourceCapturer implements SourceCapturer {

    private static final String MESSAGE =
            "real-source capture requires supervisor runtime mode (iotsim.runtime.mode=supervisor)";

    @Override
    public CaptureSession startCapture(CaptureSpec spec, Consumer<List<NeutralValue>> sink) {
        throw new CaptureException(MESSAGE);
    }
}
