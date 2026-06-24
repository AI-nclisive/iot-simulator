package com.ainclusive.iotsim.supervisor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Allocates a free loopback TCP port for a worker's control channel. */
public final class PortAllocator {

    private PortAllocator() {}

    public static int freeLoopbackPort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("could not allocate a loopback port", e);
        }
    }
}
