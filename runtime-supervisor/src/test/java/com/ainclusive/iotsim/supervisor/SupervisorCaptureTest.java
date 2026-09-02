package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.capture.CaptureSession;
import com.ainclusive.iotsim.platform.capture.CaptureSpec;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import com.ainclusive.iotsim.protocolmodel.NativeTypeKind;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies live-capture orchestration: spawn a client-mode worker, decode values, stop. */
class SupervisorCaptureTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private Supervisor supervisor;

    private Supervisor supervisor() {
        supervisor = new Supervisor(launcher);
        return supervisor;
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    private static CaptureSpec spec(ConnectionCredentials credentials) {
        return new CaptureSpec("OPC_UA", "opc.tcp://plant:4840/ua", credentials, 3,
                List.of(new SchemaNode("temp", null, "/temp", "temp", NodeKind.VARIABLE,
                        DataType.FLOAT64, ValueRank.SCALAR, Access.READ, null, null)));
    }

    @Test
    void captureForwardsRequestStreamsDecodedValuesAndStopsWorker() throws Exception {
        Supervisor supervisor = supervisor();
        List<NeutralValue> received = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        CaptureSession session = supervisor.startCapture(
                spec(ConnectionCredentials.password("operator", "s3cret")),
                values -> {
                    received.addAll(values);
                    got.countDown();
                });

        assertThat(got.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).nodeId()).isEqualTo("temp");
        assertThat(received.get(0).value()).isEqualTo(21.5); // decoded against FLOAT64 -> NUM

        // The worker received the endpoint, session credentials, and schema.
        CaptureRequest sent = launcher.last().service().lastCaptureRequest();
        assertThat(sent.getEndpointUrl()).isEqualTo("opc.tcp://plant:4840/ua");
        assertThat(sent.getCredentials().getMode()).isEqualTo("PASSWORD");
        assertThat(sent.getCredentials().getUsername()).isEqualTo("operator");
        assertThat(sent.getSchema().getNodesCount()).isEqualTo(1);

        session.stop();
        assertThat(launcher.last().service().awaitCaptureCancelled(5)).isTrue();
        assertThat(launcher.allServersShutDown()).isTrue();
    }

    @Test
    void captureAcceptsNativeStructureWithExecutableEncoding() throws Exception {
        Supervisor supervisor = supervisor();
        List<NeutralValue> received = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);
        SchemaNode range = new SchemaNode("ns=0;i=884", null, "Types/Range", "Range", NodeKind.DATA_TYPE,
                null, null, null, null, null, List.of(), null, List.of(), null,
                List.of(new DataTypeMember("low", DataType.FLOAT64, null),
                        new DataTypeMember("high", DataType.FLOAT64, null)),
                List.of(), "ns=0;i=886", NativeTypeKind.STRUCTURE, null, null, null, null, null, null, null);
        SchemaNode variable = new SchemaNode("range", null, "/range", "Range", NodeKind.VARIABLE,
                null, ValueRank.SCALAR, Access.READ, null, null, List.of(), null, List.of(), "ns=0;i=884",
                List.of(), List.of(), null, null, null, null, null, null, null, null, null);

        CaptureSession session = supervisor.startCapture(new CaptureSpec("OPC_UA", "opc.tcp://plant:4840/ua",
                ConnectionCredentials.anonymous(), 3, List.of(range, variable)), values -> {
                    received.addAll(values);
                    got.countDown();
                });

        assertThat(got.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).singleElement().satisfies(value -> {
            assertThat(value.nodeId()).isEqualTo("range");
            assertThat(value.value()).isInstanceOf(byte[].class);
        });
        assertThat(launcher.last().service().lastCaptureRequest().getSchema().getNodesList())
                .anySatisfy(node -> assertThat(node.getDataTypeDefaultEncodingId()).isEqualTo("ns=0;i=886"));

        session.stop();
    }

    @Test
    void captureStreamFailureTearsDownWorkerAndNotifiesOwner() throws Exception {
        Supervisor supervisor = supervisor();
        CountDownLatch failed = new CountDownLatch(1);

        supervisor.startCapture(spec(ConnectionCredentials.anonymous()), values -> { }, error -> failed.countDown());
        assertThat(launcher.last().service().awaitCaptureStarted(5)).isTrue();
        launcher.last().service().failCapture();

        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(launcher.allServersShutDown()).isTrue();
    }

    @Test
    void startCaptureSupportsModbusTcpAndForwardsItsEncodedEndpoint() throws Exception {
        Supervisor supervisor = supervisor();

        CaptureSession session = supervisor.startCapture(
                new CaptureSpec("MODBUS_TCP", "tcp://host:502#7", ConnectionCredentials.anonymous(), 1, List.of()),
                values -> { });

        assertThat(launcher.last().service().awaitCaptureStarted(5)).isTrue();
        assertThat(launcher.last().service().lastCaptureRequest().getEndpointUrl()).isEqualTo("tcp://host:502#7");
        session.stop();
    }

    @Test
    void startCaptureRejectsExternalRefCredentialsWithoutLaunching() {
        Supervisor supervisor = supervisor();

        assertThatThrownBy(() -> supervisor.startCapture(
                spec(ConnectionCredentials.externalRef("vault://x")), values -> { }))
                .isInstanceOf(CaptureException.class)
                .hasMessageContaining("external-ref");
        assertThat(launcher.launchCount()).isZero();
    }
}
