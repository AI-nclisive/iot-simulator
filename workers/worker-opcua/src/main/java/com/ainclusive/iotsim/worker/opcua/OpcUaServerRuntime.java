package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;

/**
 * A real OPC UA server (Eclipse Milo) with an address space built from the
 * neutral schema. Message security is {@code None}; the accepted user tokens
 * (Anonymous and/or UserName/password) come from the {@link AuthConfig} — an
 * empty config keeps the historical None/Anonymous behaviour (IS-131). Transport
 * message security (Sign/Encrypt) is a later phase (IS-132).
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
final class OpcUaServerRuntime {

    private final OpcUaServer server;
    private final SchemaNamespace namespace;
    private final String endpointUrl;
    private final Consumer<RuntimeEvent> runtimeEventSink;
    private final int port;

    OpcUaServerRuntime(int port, List<VarDef> variables) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), event -> {}, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), clientEventSink, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), clientEventSink, runtimeEventSink);
    }

    OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables,
            Consumer<ClientEvent> clientEventSink, Consumer<RuntimeEvent> runtimeEventSink) {
        this(port, bindAddress, advertisedHost, variables, AuthConfig.anonymous(), clientEventSink, runtimeEventSink);
    }

    OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables,
            AuthConfig auth, Consumer<ClientEvent> clientEventSink, Consumer<RuntimeEvent> runtimeEventSink) {
        this.runtimeEventSink = runtimeEventSink;
        this.port = port;
        try {
            File pki = Files.createTempDirectory("iotsim-pki").toFile();
            DefaultTrustListManager trustList = new DefaultTrustListManager(pki);

            // USERNAME policy with SecurityPolicy.None: passwords travel in the clear over the
            // SecurityPolicy.None channel (consistent with None channel security). The built-in
            // USER_TOKEN_POLICY_USERNAME uses Basic256 which requires a server certificate the
            // worker does not provision — causing Bad_ConfigurationError on the client side.
            UserTokenPolicy usernamePolicy = new UserTokenPolicy(
                    "username",
                    UserTokenType.UserName,
                    null,
                    null,
                    SecurityPolicy.None.getUri());

            List<UserTokenPolicy> tokenPolicies = new ArrayList<>();
            if (auth.anonymousAllowed()) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }
            if (auth.usernameEnabled()) {
                tokenPolicies.add(usernamePolicy);
            }
            if (tokenPolicies.isEmpty()) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }

            EndpointConfiguration endpoint = EndpointConfiguration.newBuilder()
                    .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                    .setBindAddress(bindAddress)
                    .setHostname(advertisedHost)
                    .setBindPort(port)
                    .setPath("/iotsim")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None)
                    .addTokenPolicies(tokenPolicies.toArray(new UserTokenPolicy[0]))
                    .build();

            OpcUaServerConfig config = OpcUaServerConfig.builder()
                    .setApplicationUri("urn:iotsim:opcua:worker")
                    .setApplicationName(LocalizedText.english("IoT Simulator OPC UA Worker"))
                    .setProductUri("urn:iotsim:opcua")
                    .setIdentityValidator(new UsernameIdentityValidator(
                            auth.anonymousAllowed(), challenge -> authenticate(auth, challenge)))
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
            this.endpointUrl = "opc.tcp://" + advertisedHost + ":" + port + "/iotsim";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare OPC UA server", e);
        }
    }

    void start() {
        namespace.startup();
        await(server.startup());
        // Milo swallows bind failures silently (exceptionally → Unit.VALUE); detect them
        // by checking that the endpoint was actually registered after startup.
        if (server.getStackServer().getBoundEndpoints().isEmpty()) {
            runtimeEventSink.accept(runtimeEvent("ERROR", "port " + port + " bind failed"));
            throw new BindFailedException("port " + port + " bind failed", null);
        }
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

    /** True when the challenge names a configured user whose password hash matches. */
    private static boolean authenticate(AuthConfig auth,
            UsernameIdentityValidator.AuthenticationChallenge challenge) {
        if (!auth.usernameEnabled()) {
            return false;
        }
        String hash = auth.userPasswordHashes().get(challenge.getUsername());
        return hash != null && PasswordHash.matches(challenge.getPassword(), hash);
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
