package com.ainclusive.iotsim.workercontract;

/**
 * Shared metadata for the supervisor⇄worker contract. The version is exchanged in
 * the {@code Hello} handshake; a mismatched major version is refused, not
 * tolerated (backend-specs/02_WORKER_CONTRACT_AND_IPC.md).
 */
public final class WorkerContract {

    public static final String VERSION = "1.0.0";

    private WorkerContract() {}

    public static int major(String version) {
        return Integer.parseInt(version.split("\\.", 2)[0].trim());
    }
}
