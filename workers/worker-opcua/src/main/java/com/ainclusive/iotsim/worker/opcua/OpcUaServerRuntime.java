package com.ainclusive.iotsim.worker.opcua;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;

/**
 * A real OPC UA server (Eclipse Milo) with an address space built from the
 * neutral schema, bound to loopback. None security / anonymous for now.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
final class OpcUaServerRuntime {

    private final OpcUaServer server;
    private final SchemaNamespace namespace;
    private final String endpointUrl;

    OpcUaServerRuntime(int port, List<VarDef> variables) {
        try {
            File pki = Files.createTempDirectory("iotsim-pki").toFile();
            DefaultTrustListManager trustList = new DefaultTrustListManager(pki);

            EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress("127.0.0.1")
                    .setHostname("127.0.0.1")
                    .setBindPort(port)
                    .setPath("/iotsim")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)
                    .addTokenPolicies(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
                    .build();

            OpcUaServerConfig config = OpcUaServerConfig.builder()
                    .setApplicationUri("urn:iotsim:opcua:worker")
                    .setApplicationName(LocalizedText.english("IoT Simulator OPC UA Worker"))
                    .setProductUri("urn:iotsim:opcua")
                    .setEndpoints(Set.of(endpoint))
                    .setCertificateManager(new DefaultCertificateManager())
                    .setTrustListManager(trustList)
                    .setCertificateValidator(new DefaultServerCertificateValidator(trustList))
                    .build();

            this.server = new OpcUaServer(config);
            this.namespace = new SchemaNamespace(server, variables);
            this.endpointUrl = "opc.tcp://127.0.0.1:" + port + "/iotsim";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare OPC UA server", e);
        }
    }

    void start() {
        namespace.startup();
        await(server.startup());
    }

    void stop() {
        await(server.shutdown());
        namespace.shutdown();
    }

    void updateValue(String nodeId, Object opcUaValue) {
        namespace.updateValue(nodeId, opcUaValue);
    }

    String endpointUrl() {
        return endpointUrl;
    }

    /** NodeId a client uses to address a variable (namespace index + node id). */
    NodeId variableNodeId(String nodeId) {
        return new NodeId(server.getNamespaceTable().getIndex(SchemaNamespace.URI), nodeId);
    }

    private static void await(java.util.concurrent.CompletableFuture<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("OPC UA server operation failed", e.getCause());
        }
    }
}
