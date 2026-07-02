package com.ainclusive.iotsim.worker.opcua;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.Test;

/** IS-131: the simulated server advertises UserName policy and validates credentials. */
class OpcUaServerAuthIT {

    @Test
    void usernameOnlyServerAcceptsValidAndRejectsInvalid() throws Exception {
        int port = freePort();
        AuthConfig auth = new AuthConfig(false, true, Map.of("operator", PasswordHash.encode("s3cret")));
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("temp", "Temperature", "FLOAT64")), auth, e -> {}, e -> {});
        runtime.start();
        try {
            OpcUaClient ok = OpcUaClientSupport.connect(runtime.endpointUrl(), "PASSWORD", "operator", "s3cret");
            ok.disconnect().get(10, SECONDS);

            assertThatThrownBy(() ->
                    OpcUaClientSupport.connect(runtime.endpointUrl(), "PASSWORD", "operator", "wrong"));
            assertThatThrownBy(() ->
                    OpcUaClientSupport.connect(runtime.endpointUrl(), "ANONYMOUS", null, null));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void anonymousStillWorksByDefault() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("temp", "Temperature", "FLOAT64")), AuthConfig.anonymous(), e -> {}, e -> {});
        runtime.start();
        try {
            OpcUaClient client = OpcUaClientSupport.connect(runtime.endpointUrl(), "ANONYMOUS", null, null);
            assertThat(client).isNotNull();
            client.disconnect().get(10, SECONDS);
        } finally {
            runtime.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
