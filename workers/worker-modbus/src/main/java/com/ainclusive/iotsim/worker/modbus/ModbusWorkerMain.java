package com.ainclusive.iotsim.worker.modbus;

/**
 * Modbus TCP worker entry point. The supervisor launches this as a child process
 * and passes the loopback control port. Hosts a gRPC {@code ProtocolDataSource}
 * server (worker-contract) and a j2mod Modbus slave. Mirrors {@code worker-opcua}'s
 * {@code OpcUaWorkerMain}. See openspec/specs/worker-contract/spec.md.
 */
public final class ModbusWorkerMain {

    private ModbusWorkerMain() {}

    public static void main(String[] args) throws Exception {
        int controlPort = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        WorkerServer server = new WorkerServer(controlPort, new ModbusProtocolService()).start();
        System.out.printf("modbus-worker gRPC listening on 127.0.0.1:%d%n", server.port());
        server.awaitTermination();
    }
}
