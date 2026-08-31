package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Value;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Polls a real local slave (built via {@link ModbusServerRuntime}) end-to-end
 * through {@link ModbusCapture}, verifying the initial-value snapshot and a
 * later on-change update are both observed — the poll-based realization of
 * the {@code worker-contract} Capture requirement for a protocol with no
 * native push/subscribe mechanism.
 */
class ModbusCaptureIT {

    private static final int PORT = 48631;

    private ModbusServerRuntime runtime;
    private ModbusCapture capture;

    @AfterEach
    void tearDown() {
        if (capture != null) {
            capture.stop();
        }
        if (runtime != null) {
            runtime.stop();
        }
    }

    @Test
    void observesInitialValueAndAChange() throws Exception {
        List<ModbusServerRuntime.VarSpec> vars = List.of(new ModbusServerRuntime.VarSpec("hr1", "UINT16", "READ_WRITE"));
        runtime = new ModbusServerRuntime(vars, PORT, InetAddress.getByName("127.0.0.1"), 1, event -> {});
        runtime.start();
        runtime.updateValue("hr1", 4200L);

        List<Value> received = new CopyOnWriteArrayList<>();
        capture = ModbusCapture.start("127.0.0.1:" + PORT,
                List.of(new ModbusCapture.NodeSpec("hr:0", "UINT16")),
                received::addAll);

        awaitUntil(() -> received.stream().anyMatch(v -> decodeLong(v) == 4200L));

        runtime.updateValue("hr1", 4300L);

        awaitUntil(() -> received.stream().anyMatch(v -> decodeLong(v) == 4300L));
    }

    private static long decodeLong(Value value) {
        return (Long) ValueCodec.decode(ValueCodec.Kind.valueOf(value.getValueKind()), value.getValueEnc().toByteArray());
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
}
