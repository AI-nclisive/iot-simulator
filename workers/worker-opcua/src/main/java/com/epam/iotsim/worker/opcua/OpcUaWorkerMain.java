package com.epam.iotsim.worker.opcua;

/**
 * OPC UA worker entry point. The supervisor launches this as a child process and
 * passes the loopback control port (0 = ephemeral). It hosts a gRPC
 * {@code ProtocolDataSource} server; the Eclipse Milo OPC UA server is wired in a
 * later step. See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public final class OpcUaWorkerMain {

    private OpcUaWorkerMain() {}

    public static void main(String[] args) throws Exception {
        int controlPort = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        WorkerServer server = new WorkerServer(controlPort, new OpcUaProtocolService()).start();
        System.out.printf("opcua-worker gRPC listening on 127.0.0.1:%d%n", server.port());
        server.awaitTermination();
    }
}
