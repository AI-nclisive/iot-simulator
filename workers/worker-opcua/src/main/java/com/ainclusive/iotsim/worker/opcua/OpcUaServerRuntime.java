package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
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
    private final Consumer<RuntimeEvent> runtimeEventSink;

    OpcUaServerRuntime(int port, List<VarDef> variables) {
        this(port, variables, event -> {}, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink) {
        this(port, variables, clientEventSink, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this.runtimeEventSink = runtimeEventSink;
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
            // Surface protocol-client connect/disconnect as ClientEvents (IS-047). Milo
            // fires session created/closed on the server's session manager; map each to a
            // neutral event for the supervisor stream.
            server.getSessionManager().addSessionListener(new SessionListener() {
                @Override
                public void onSessionCreated(Session session) {
                    clientEventSink.accept(clientEvent(ClientEvent.Kind.CONNECTED, session));
                }

                @Override
                public void onSessionClosed(Session session) {
                    clientEventSink.accept(clientEvent(ClientEvent.Kind.DISCONNECTED, session));
                }
            });
            this.namespace = new SchemaNamespace(server, variables);
            this.endpointUrl = "opc.tcp://127.0.0.1:" + port + "/iotsim";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare OPC UA server", e);
        }
    }

    void start() {
        namespace.startup();
        await(server.startup());
        // Server is now listening: surface SOURCE_START on the runtime stream (IS-048).
        runtimeEventSink.accept(runtimeEvent("SOURCE_START", ""));
    }

    void stop() {
        // Emit before tearing down so the supervisor sees SOURCE_STOP while the stream
        // is still open (best-effort on teardown).
        runtimeEventSink.accept(runtimeEvent("SOURCE_STOP", ""));
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

    /** Builds a neutral runtime event with the current wall-clock time in micros. */
    private static RuntimeEvent runtimeEvent(String type, String detail) {
        return RuntimeEvent.newBuilder()
                .setType(type)
                .setAtMicros(System.currentTimeMillis() * 1_000L)
                .setDetail(detail == null ? "" : detail)
                .build();
    }

    /** Builds a neutral client event from a Milo session, preferring the client-supplied session name. */
    private static ClientEvent clientEvent(ClientEvent.Kind kind, Session session) {
        String clientId = session.getSessionName();
        if (clientId == null || clientId.isBlank()) {
            clientId = session.getSessionId().toParseableString();
        }
        return ClientEvent.newBuilder()
                .setKind(kind)
                .setClientId(clientId)
                .setAtMicros(System.currentTimeMillis() * 1_000L)
                .build();
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
