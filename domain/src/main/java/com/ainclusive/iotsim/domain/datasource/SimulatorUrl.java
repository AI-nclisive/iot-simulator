package com.ainclusive.iotsim.domain.datasource;

/** Builds the advertised endpoint URL for a simulated source. */
public final class SimulatorUrl {

    private SimulatorUrl() {}

    /** Default listen port for each protocol. OPC UA: 4840, Modbus/TCP: 502. */
    public static int defaultPort(Protocol protocol) {
        return switch (protocol) {
            case OPC_UA -> 4840;
            case MODBUS_TCP -> 502;
        };
    }

    /**
     * Constructs the serve URL for the simulator listener.
     * OPC UA: {@code opc.tcp://<host>:<port>/iotsim}
     * Modbus/TCP: {@code modbus.tcp://<host>:<port>}
     */
    public static String of(Protocol protocol, String host, int port) {
        return switch (protocol) {
            case OPC_UA -> "opc.tcp://" + host + ":" + port + "/iotsim";
            case MODBUS_TCP -> "modbus.tcp://" + host + ":" + port;
        };
    }
}
