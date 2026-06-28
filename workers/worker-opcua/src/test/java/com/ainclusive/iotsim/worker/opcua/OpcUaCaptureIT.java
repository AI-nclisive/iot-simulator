package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.Value;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link OpcUaCapture} as an OPC UA client against a real embedded Milo
 * server (the same projection the runtime serves) standing in for a real source.
 * The core IS-045 check: a subscription observes value changes and forwards them
 * as neutral values, until the capture is stopped.
 */
class OpcUaCaptureIT {

    @Test
    void capturesValueChangesFromRunningServer() throws Exception {
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

            // A change on the real server must surface as a captured neutral value.
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
