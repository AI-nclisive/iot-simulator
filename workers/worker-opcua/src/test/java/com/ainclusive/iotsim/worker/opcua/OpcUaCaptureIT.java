package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.ConnectionConfigMsg;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Drives {@link OpcUaCapture} as an OPC UA client against a real embedded Milo
 * server (the same projection the runtime serves) standing in for a real source.
 * The core IS-045 check: a subscription observes value changes and forwards them
 * as neutral values, until the capture is stopped.
 */
class OpcUaCaptureIT {

    @Test
    void encodesNativeEnumValuesAsIntegerLiterals() {
        Value captured = OpcUaCapture.toProtoValue(
                new OpcUaCapture.NodeSpec("ns=2;s=counter", "INT32"),
                new DataValue(new Variant(Unsigned.uint(42))));

        assertThat(captured.getNodeId()).isEqualTo("ns=2;s=counter");
        assertThat(ValueCodec.decode(ValueCodec.Kind.INT, captured.getValueEnc().toByteArray()))
                .isEqualTo(42L);
    }

    @Test
    void rejectsOpaqueNativeValueInsteadOfGuessingAnEncoding() {
        assertThatIllegalArgumentException().isThrownBy(() -> OpcUaCapture.toProtoValue(
                new OpcUaCapture.NodeSpec("ns=2;s=opaque", null),
                new DataValue(new Variant(Unsigned.uint(42)))))
                .withMessageContaining("without an executable encoding")
                .withMessageContaining("ns=2;s=opaque");
    }

    @Test
    void capturesAbstractValuesWithATypeDiscriminatorTree() {
        Value captured = OpcUaCapture.toProtoValue(
                new OpcUaCapture.NodeSpec("ns=2;s=any", "ABSTRACT"),
                new DataValue(new Variant(Unsigned.uint(42))));

        assertThat(captured.getValueKind()).isEqualTo("TREE");
        @SuppressWarnings("unchecked")
        Map<String, Object> tree = (Map<String, Object>)
                ValueCodec.decode(ValueCodec.Kind.TREE, captured.getValueEnc().toByteArray());
        assertThat(tree).containsEntry("type", "UINT32").containsEntry("value", 42L);
    }

    @Test
    void capturesInitialValueAndChangesFromRunningServer() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, List.of(new VarDef("temp", "Temperature", "FLOAT64")));
        runtime.start();
        String nodeId = runtime.variableNodeId("temp").toParseableString();
        List<Value> received = new CopyOnWriteArrayList<>();
        OpcUaCapture capture = null;
        try {
            capture = OpcUaCapture.start(runtime.endpointUrl(), "ANONYMOUS", null, null,
                    List.of(new OpcUaCapture.NodeSpec(nodeId, "FLOAT64")), received::addAll);

            // A static value must be captured immediately, without waiting for a
            // data-change notification from the real server.
            awaitUntil(() -> !received.isEmpty());
            assertThat(received.getFirst().getNodeId()).isEqualTo(nodeId);

            // Subsequent changes must still surface as captured neutral values.
            runtime.updateValue("temp", 42.5d);
            awaitUntil(() -> decodedDoubles(received).contains(42.5d));

            assertThat(decodedDoubles(received)).contains(42.5d);
            Value last = received.get(received.size() - 1);
            assertThat(last.getNodeId()).isEqualTo(nodeId);
            assertThat(last.getQuality()).isEqualTo(Quality.GOOD);
        } finally {
            if (capture != null) {
                capture.stop();
            }
            runtime.stop();
        }
    }

    @Test
    void capturesInitialValuesAcrossMultipleMonitoredItemBatches() throws Exception {
        int port = freePort();
        List<VarDef> variables = IntStream.range(0, 101)
                .mapToObj(index -> new VarDef("reading-" + index, "Reading " + index, "FLOAT64"))
                .toList();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, variables);
        runtime.start();
        List<Value> received = new CopyOnWriteArrayList<>();
        OpcUaCapture capture = null;
        try {
            List<OpcUaCapture.NodeSpec> nodes = variables.stream()
                    .map(variable -> new OpcUaCapture.NodeSpec(
                            runtime.variableNodeId(variable.nodeId()).toParseableString(), "FLOAT64"))
                    .toList();

            capture = OpcUaCapture.start(runtime.endpointUrl(), "ANONYMOUS", null, null, nodes, received::addAll);

            awaitUntil(() -> received.size() >= variables.size());
            assertThat(received).extracting(Value::getNodeId)
                    .contains(runtime.variableNodeId("reading-0").toParseableString())
                    .contains(runtime.variableNodeId("reading-100").toParseableString());
        } finally {
            if (capture != null) {
                capture.stop();
            }
            runtime.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "iotsim.public-opcua-e2e", matches = "true")
    void capturesInitialValueFromPublicDemoServer() throws Exception {
        List<Value> received = new CopyOnWriteArrayList<>();
        OpcUaCapture capture = null;
        try {
            capture = OpcUaCapture.start(
                    "opc.tcp://opcua.demo-this.com:51210/UA/SampleServer", "ANONYMOUS", null, null,
                    List.of(
                            new OpcUaCapture.NodeSpec("ns=2;i=10160", "BOOL"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10164", "BYTES"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10165", "NODE_ID"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10167", "STRING"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10171", "LOCALIZED_TEXT"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10172", "UINT16"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10181", "STATUS_CODE"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10217", "INT8"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10218", "UINT8"),
                            new OpcUaCapture.NodeSpec("ns=2;i=10657", "LOCALIZED_TEXT")), received::addAll);

            awaitUntil(() -> !received.isEmpty());
            assertThat(received).extracting(Value::getNodeId).contains("ns=2;i=10657");
        } finally {
            if (capture != null) {
                capture.stop();
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "iotsim.public-opcua-e2e", matches = "true")
    void capturesInitialValuesFromPublicDemoFullSchema() throws Exception {
        String endpoint = "opc.tcp://opcua.demo-this.com:51210/UA/SampleServer";
        OpcUaDiscovery.ScanOutcome scan = OpcUaDiscovery.scan(
                endpoint, new OpcUaDiscovery.Credentials("ANONYMOUS", null, null), 0, () -> { }, soFar -> { });
        List<OpcUaCapture.NodeSpec> nodes = scan.nodes().stream()
                .filter(node -> "VARIABLE".equals(node.getKind()) && !node.getDataType().isBlank())
                .map(node -> new OpcUaCapture.NodeSpec(node.getNodeId(), node.getDataType()))
                .toList();
        List<Value> received = new CopyOnWriteArrayList<>();
        OpcUaCapture capture = null;
        try {
            capture = OpcUaCapture.start(endpoint, "ANONYMOUS", null, null, nodes, received::addAll);

            awaitUntil(() -> !received.isEmpty());
            assertThat(received).isNotEmpty();
            received.forEach(value -> {
                Object decoded = ValueCodec.decode(
                        ValueCodec.Kind.valueOf(value.getValueKind()), value.getValueEnc().toByteArray());
                assertThat(ValueCodec.encode(decoded)).isNotNull();
            });
        } finally {
            if (capture != null) {
                capture.stop();
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "iotsim.public-opcua-e2e", matches = "true")
    void streamsFullPublicSchemaOverGrpc() throws Exception {
        String endpoint = "opc.tcp://opcua.demo-this.com:51210/UA/SampleServer";
        OpcUaDiscovery.ScanOutcome scan = OpcUaDiscovery.scan(
                endpoint, new OpcUaDiscovery.Credentials("ANONYMOUS", null, null), 0, () -> { }, soFar -> { });
        WorkerServer worker = new WorkerServer(0, new OpcUaProtocolService()).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", worker.port()).usePlaintext().build();
        CountDownLatch received = new CountDownLatch(1);
        try {
            ProtocolDataSourceGrpc.newStub(channel).capture(CaptureRequest.newBuilder()
                    .setEndpointUrl(endpoint)
                    .setCredentials(ConnectionConfigMsg.newBuilder().setMode("ANONYMOUS"))
                    .setSchema(Schema.newBuilder().addAllNodes(scan.nodes()))
                    .build(), new StreamObserver<>() {
                        @Override public void onNext(ValueBatch ignored) { received.countDown(); }
                        @Override public void onError(Throwable error) { throw new AssertionError(error); }
                        @Override public void onCompleted() { }
                    });

            assertThat(received.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            channel.shutdownNow();
            worker.stop();
        }
    }

    @Test
    void capturesNothingAfterStop() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, List.of(new VarDef("temp", "Temperature", "FLOAT64")));
        runtime.start();
        String nodeId = runtime.variableNodeId("temp").toParseableString();
        List<Value> received = new CopyOnWriteArrayList<>();
        try {
            OpcUaCapture capture = OpcUaCapture.start(runtime.endpointUrl(), "ANONYMOUS", null, null,
                    List.of(new OpcUaCapture.NodeSpec(nodeId, "FLOAT64")), received::addAll);
            runtime.updateValue("temp", 1.0d);
            awaitUntil(() -> !received.isEmpty());
            capture.stop();

            int countAtStop = received.size();
            runtime.updateValue("temp", 2.0d);
            Thread.sleep(700); // longer than the publishing interval
            assertThat(received).hasSize(countAtStop);
        } finally {
            runtime.stop();
        }
    }

    @Test
    @Disabled("IS-198: OptionSet capture test - mock server integration issue (stub)")
    void capturesNativeOptionSetValuesWithBitMaskAndNames() throws Exception {
        int port = freePort();
        String typeId = "ns=4;s=AccessFlags";
        NativeDataTypeDef optionSet = new NativeDataTypeDef(
                typeId,
                "AccessFlags",
                List.of(),
                List.of(
                        com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg.newBuilder()
                                .setName("Read").setValue(0).build(),
                        com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg.newBuilder()
                                .setName("Write").setValue(1).build()),
                null, "OPTION_SET");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("flags", null, "Flags", "VARIABLE", "", typeId,
                        null, null, null, null, null)),
                List.of(optionSet), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        String nodeId = runtime.variableNodeId("flags").toParseableString();
        List<Value> received = new CopyOnWriteArrayList<>();
        OpcUaCapture capture = null;
        try {
            capture = OpcUaCapture.start(runtime.endpointUrl(), "ANONYMOUS", null, null,
                    List.of(new OpcUaCapture.NodeSpec(nodeId, "OPTION_SET")), received::addAll);

            // A change on the real server must surface as a captured neutral value tree.
            runtime.updateValue("flags", runtime.optionSetValue(typeId, new byte[] {3}, new byte[] {3}));
            awaitUntil(() -> !received.isEmpty());

            assertThat(received).hasSize(1);
            Value captured = received.get(0);
            assertThat(captured.getNodeId()).isEqualTo(nodeId);
            assertThat(captured.getValueKind()).isEqualTo("TREE");
            assertThat(captured.getQuality()).isEqualTo(Quality.GOOD);

            // Decode the captured value tree and verify structure
            @SuppressWarnings("unchecked")
            Map<?, ?> tree = (Map<?, ?>) ValueCodec.decode(ValueCodec.Kind.TREE, captured.getValueEnc().toByteArray());
            assertThat(tree).hasSize(2);
            assertThat(tree.get("value")).isEqualTo(new byte[] {3});
            assertThat(tree.get("validBits")).isEqualTo(new byte[] {3});
        } finally {
            if (capture != null) {
                capture.stop();
            }
            runtime.stop();
        }
    }

    private static List<Double> decodedDoubles(List<Value> values) {
        return values.stream()
                .map(v -> (Double) ValueCodec.decode(ValueCodec.Kind.NUM, v.getValueEnc().toByteArray()))
                .toList();
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
