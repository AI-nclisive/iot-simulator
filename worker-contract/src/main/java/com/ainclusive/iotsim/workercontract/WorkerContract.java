package com.ainclusive.iotsim.workercontract;

/**
 * Shared metadata for the supervisor⇄worker contract. The version is exchanged in
 * the {@code Hello} handshake; a mismatched major version is refused, not
 * tolerated (openspec/specs/worker-contract/spec.md).
 */
public final class WorkerContract {

    // 1.4.0 adds SchemaNodeMsg.array_dimensions for lossless array capture/replay
    // (IS-201). 1.3.0 adds the additive SecurityConfig on ConfigureRequest (simulated OPC UA
    // endpoint auth, IS-131); 1.2.0 added Capture (IS-045); 1.1.0 added
    // TestConnection/Scan (IS-043). The major is unchanged so existing workers stay compatible.
    public static final String VERSION = "1.4.0";

    private WorkerContract() {}

    public static int major(String version) {
        return Integer.parseInt(version.split("\\.", 2)[0].trim());
    }
}
