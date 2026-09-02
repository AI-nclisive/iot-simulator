package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the Modbus TCP record-to-replay boundary entirely over loopback: a
 * local worker endpoint is captured as if it were a real device, then its
 * captured sequence is applied to a second local worker endpoint and read by
 * a real Modbus master. No physical device is required.
 */
class ModbusRecordReplayLifecycleIT {

    private static final String NODE_ID = "hr:0";
    private static final long INITIAL_VALUE = 1_200L;
    private static final long CHANGED_VALUE = 4_321L;

    private final ModbusProtocolService source = new ModbusProtocolService();
    private final ModbusProtocolService replay = new ModbusProtocolService();
    private ModbusCapture capture;
    private ModbusTCPMaster replayClient;

    @AfterEach
    void tearDown() {
        if (capture != null) {
            capture.stop();
        }
        if (replayClient != null) {
            replayClient.disconnect();
        }
        stop(source);
        stop(replay);
    }

    @Test
    void capturesAChangedValueAndReplaysTheOrderedSequenceToALocalEndpoint() throws Exception {
        int sourcePort = freeLoopbackPort();
        int replayPort = freeLoopbackPort();
        configureAndStart(source, sourcePort);
        apply(source, value(INITIAL_VALUE));

        List<Value> captured = new CopyOnWriteArrayList<>();
        capture = ModbusCapture.start("127.0.0.1:" + sourcePort,
                List.of(new ModbusCapture.NodeSpec(NODE_ID, "UINT16")), captured::addAll);
        awaitUntil(() -> values(captured).contains(INITIAL_VALUE));

        apply(source, value(CHANGED_VALUE));
        awaitUntil(() -> values(captured).contains(CHANGED_VALUE));
        capture.stop();
        capture = null;
        stop(source);

        assertThat(values(captured)).containsExactly(INITIAL_VALUE, CHANGED_VALUE);

        configureAndStart(replay, replayPort);
        apply(replay, captured);

        replayClient = new ModbusTCPMaster("127.0.0.1", replayPort, 2_000, false);
        replayClient.connect();
        assertThat(unsigned(replayClient.readMultipleRegisters(1, 0, 1))[0]).isEqualTo((int) CHANGED_VALUE);
    }

    private static void configureAndStart(ModbusProtocolService service, int port) {
        service.configure(ConfigureRequest.newBuilder()
                .setListenPort(port)
                .putOptions("bindAddress", "127.0.0.1")
                .putOptions("unitId", "1")
                .setSchema(Schema.newBuilder().setVersion(1)
                        .addNodes(SchemaNodeMsg.newBuilder()
                                .setNodeId(NODE_ID)
                                .setPath("HoldingRegister0")
                                .setName("Holding register 0")
                                .setKind("VARIABLE")
                                .setDataType("UINT16")
                                .setAccess("READ_WRITE")))
                .build(), ackObserver());
        service.start(StartRequest.getDefaultInstance(), ackObserver());
    }

    private static void apply(ModbusProtocolService service, Value value) {
        apply(service, List.of(value));
    }

    private static void apply(ModbusProtocolService service, List<Value> values) {
        StreamObserver<ValueBatch> stream = service.applyValues(ackObserver());
        stream.onNext(ValueBatch.newBuilder().addAllValues(values).build());
        stream.onCompleted();
    }

    private static Value value(long value) {
        ValueCodec.Encoded encoded = ValueCodec.encode(value);
        return Value.newBuilder()
                .setNodeId(NODE_ID)
                .setValueKind(encoded.kind().name())
                .setValueEnc(com.google.protobuf.ByteString.copyFrom(encoded.bytes()))
                .build();
    }

    private static List<Long> values(List<Value> captured) {
        return captured.stream()
                .map(value -> (Long) ValueCodec.decode(ValueCodec.Kind.INT, value.getValueEnc().toByteArray()))
                .toList();
    }

    private static StreamObserver<Ack> ackObserver() {
        return new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError("Modbus worker RPC failed", error);
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private static void stop(ModbusProtocolService service) {
        service.stop(StopRequest.getDefaultInstance(), ackObserver());
    }

    private static int freeLoopbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static int[] unsigned(InputRegister[] registers) {
        int[] raw = new int[registers.length];
        for (int i = 0; i < registers.length; i++) {
            raw[i] = registers[i].toUnsignedShort();
        }
        return raw;
    }
}
