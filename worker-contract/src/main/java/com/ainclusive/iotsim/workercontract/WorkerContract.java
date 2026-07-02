package com.ainclusive.iotsim.workercontract;

/**
 * Shared metadata for the supervisor⇄worker contract. The version is exchanged in
 * the {@code Hello} handshake; a mismatched major version is refused, not
 * tolerated (backend-specs/02_WORKER_CONTRACT_AND_IPC.md).
 */
public final class WorkerContract {

    // 1.3.0 adds the additive SecurityConfig on ConfigureRequest (simulated OPC UA
    // endpoint auth, IS-131); 1.2.0 added Capture (IS-045); 1.1.0 added
    // TestConnection/Scan (IS-043). The major is unchanged so existing workers stay compatible.
    public static final String VERSION = "1.3.0";

    private WorkerContract() {}

    public static int major(String version) {
        return Integer.parseInt(version.split("\\.", 2)[0].trim());
    }
}
