package com.ainclusive.iotsim.platform.capture;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port for live capture from a running real source (record real data, IS-045).
 * The domain depends on this abstraction; the runtime supervisor provides the real
 * implementation (driving a protocol worker in client mode that subscribes to the
 * real endpoint), and an "unsupported" stub is used when no workers are launched.
 * Kept protocol-neutral and free of domain types so the domain need not depend on
 * the supervisor — mirrors {@link com.ainclusive.iotsim.platform.scan.SourceScanner}.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §6.
 */
public interface SourceCapturer {

    /**
     * Starts capturing the source described by {@code spec}, delivering every
     * observed value change to {@code sink} (batched as the worker publishes) until
     * the returned session is {@link CaptureSession#stop() stopped}. The values are
     * already decoded against the schema's types.
     *
     * @throws CaptureException if the protocol/runtime mode is unsupported or the
     *     real endpoint cannot be reached or authenticated
     */
    CaptureSession startCapture(CaptureSpec spec, Consumer<List<NeutralValue>> sink);
}
