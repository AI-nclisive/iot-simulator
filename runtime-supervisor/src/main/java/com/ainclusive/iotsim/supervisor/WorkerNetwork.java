package com.ainclusive.iotsim.supervisor;

/** Deployment-level network config passed to every worker (IS-128). */
public record WorkerNetwork(String bindAddress, String advertisedHost) {
    /** Loopback-only default — external access is opt-in via deployment config. */
    public static final WorkerNetwork LOOPBACK = new WorkerNetwork("127.0.0.1", "127.0.0.1");
}
