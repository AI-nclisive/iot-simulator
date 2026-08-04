package com.ainclusive.iotsim.worker.modbus;

/**
 * Modbus TCP worker entry point. The supervisor launches this as a child process
 * and passes the loopback control port. It will host a gRPC
 * {@code ProtocolDataSource} server (worker-contract) and a j2mod Modbus slave.
 *
 * <p>Scaffold placeholder — see openspec/specs/worker-contract/spec.md.
 */
public final class ModbusWorkerMain {

    private ModbusWorkerMain() {}

    public static void main(String[] args) {
        int controlPort = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        System.out.printf("modbus-worker starting (control port=%d)%n", controlPort);
        // TODO: start gRPC ProtocolDataSource server bound to loopback:controlPort.
    }
}
