package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfigBuilder;

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
        this(port, bindAddress, advertisedHost, variables, List.of(), auth, clientEventSink, runtimeEventSink);
    }

    OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables,
            List<NativeDataTypeDef> typeDefinitions, AuthConfig auth, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this.runtimeEventSink = runtimeEventSink;
        this.port = port;
        try {
            Files.createTempDirectory("iotsim-pki");

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
                tokenPolicies.add(anonymousPolicy());
            }
            if (auth.usernameEnabled()) {
                tokenPolicies.add(usernamePolicy);
            }
            if (tokenPolicies.isEmpty()) {
                tokenPolicies.add(anonymousPolicy());
            }

            EndpointConfig endpoint = EndpointConfig.newBuilder()
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
                    .setIdentityValidator(identityValidator(auth))
                    .setEndpoints(Set.of(endpoint))
                    .setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine()))
                    .build();

            this.server = new OpcUaServer(config, profile -> {
                if (!TransportProfile.TCP_UASC_UABINARY.equals(profile)) {
                    throw new IllegalArgumentException("unsupported transport profile: " + profile);
                }
                return new OpcTcpServerTransport(new OpcTcpServerTransportConfigBuilder().build());
            });
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
            this.namespace = new SchemaNamespace(server, variables, typeDefinitions);
            this.endpointUrl = "opc.tcp://" + advertisedHost + ":" + port + "/iotsim";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare OPC UA server", e);
        }
    }

    void start() {
        namespace.startup();
        try {
            await(server.startup());
        } catch (RuntimeException e) {
            runtimeEventSink.accept(runtimeEvent("ERROR", "port " + port + " bind failed"));
            throw new BindFailedException("port " + port + " bind failed", e);
        }
        // Milo swallows bind failures silently (exceptionally → Unit.VALUE); detect them
        // by checking that the endpoint was actually registered after startup.
        if (server.getBoundEndpoints().isEmpty()) {
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

    NodeId localEncodingId(String sourceTypeId) {
        return namespace.localEncodingId(sourceTypeId);
    }

    NodeId localDataTypeId(String sourceTypeId) {
        return namespace.localDataTypeId(sourceTypeId);
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

    private static org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator identityValidator(AuthConfig auth) {
        var username = new UsernameIdentityValidator(challenge -> authenticate(auth, challenge));
        return auth.anonymousAllowed()
                ? new CompositeValidator(AnonymousIdentityValidator.INSTANCE, username)
                : username;
    }

    private static UserTokenPolicy anonymousPolicy() {
        return new UserTokenPolicy("anonymous", UserTokenType.Anonymous, null, null, SecurityPolicy.None.getUri());
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
