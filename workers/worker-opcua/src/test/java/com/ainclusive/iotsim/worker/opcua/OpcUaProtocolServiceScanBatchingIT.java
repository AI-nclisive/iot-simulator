package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.ScanEvent;
import com.ainclusive.iotsim.workercontract.v1.ScanRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@code Scan} RPC over a real loopback gRPC connection against an
 * address space large enough to require several {@code NodeBatch} chunks
 * (IS-165). Verifies discovered nodes arrive split across multiple bounded
 * events instead of one oversized terminal message, and that the terminal
 * {@code ScanResponse} carries no nodes of its own.
 */
class OpcUaProtocolServiceScanBatchingIT {

    private static final int NODE_COUNT = 1_200; // > 2x SCAN_NODE_BATCH_SIZE (500)

    @Test
    void scanStreamsNodesAcrossMultipleBatchesAndMetadataOnlyTerminal() throws Exception {
        int targetPort = freePort();
        List<VarDef> vars = IntStream.range(0, NODE_COUNT)
                .mapToObj(i -> new VarDef("v" + i, "V" + i, "INT32"))
                .toList();
        OpcUaServerRuntime target = new OpcUaServerRuntime(targetPort, vars);
        target.start();

        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer server = new WorkerServer(0, service).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port())
                .usePlaintext()
                .build();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceStub asyncStub = ProtocolDataSourceGrpc.newStub(channel);

            List<ScanEvent> events = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);
            asyncStub.scan(ScanRequest.newBuilder().setEndpointUrl(target.endpointUrl()).setMaxNodes(0).build(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(ScanEvent event) {
                            events.add(event);
                        }

                        @Override
                        public void onError(Throwable t) {
                            done.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            done.countDown();
                        }
                    });
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            List<ScanEvent> batches = events.stream().filter(ScanEvent::hasNodeBatch).toList();
            // 1200 nodes / 500 per batch -> 3 batches, none of them the whole list at once.
            assertThat(batches).hasSizeGreaterThan(1);
            int totalBatched = batches.stream().mapToInt(e -> e.getNodeBatch().getNodesCount()).sum();
            assertThat(totalBatched).isEqualTo(NODE_COUNT);
            assertThat(batches).allSatisfy(e -> assertThat(e.getNodeBatch().getNodesCount()).isLessThanOrEqualTo(500));

            ScanEvent terminal = events.get(events.size() - 1);
            assertThat(terminal.hasResult()).isTrue();
            assertThat(terminal.getResult().getStatus()).isEqualTo("OK");
            assertThat(terminal.getResult().getDiscoveredCount()).isEqualTo(NODE_COUNT);
            assertThat(terminal.getResult().getSerializedSize())
                    .as("terminal result must stay tiny now that nodes travel via NodeBatch events")
                    .isLessThan(1024);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.stop();
            target.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
