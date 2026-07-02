package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.api.stream.LiveEventHub;
import com.ainclusive.iotsim.api.stream.LiveValuesHub;
import com.ainclusive.iotsim.app.runtime.PersistingClientActivityListener;
import com.ainclusive.iotsim.app.runtime.PersistingRuntimeActivityListener;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.capture.UnsupportedSourceCapturer;
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.CompositeClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.CompositeRuntimeActivityListener;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.scan.UnsupportedSourceScanner;
import com.ainclusive.iotsim.supervisor.HealthPolicy;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import com.ainclusive.iotsim.supervisor.WorkerNetwork;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

/**
 * Selects the runtime controller and source scanner: in-memory/unsupported by
 * default (local/dev, no workers), or the real out-of-process {@link Supervisor}
 * when {@code iotsim.runtime.mode=supervisor}. The supervisor implements both
 * ports, so a single instance backs runtime control and create-from-scan
 * discovery. Worker launch commands come from {@link RuntimeProperties}.
 */
@Configuration
@EnableConfigurationProperties(RuntimeProperties.class)
public class RuntimeConfig {

    @Bean
    @Primary
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents,
            ClientConnectionRepository clientConnections, ObjectMapper json,
            ExecutorService runtimeEventExecutor, LiveEventHub liveEventHub,
            LiveValuesHub liveValuesHub,
            @org.springframework.beans.factory.annotation.Value("${iotsim.simulator.bind-address:0.0.0.0}")
            String bindAddress,
            @org.springframework.beans.factory.annotation.Value("${iotsim.simulator.advertised-host:localhost}")
            String advertisedHost) {
        if (props.isSupervisorMode()) {
            // Persist runtime events off the IPC delivery thread (IS-048), and fan the
            // same events to the live SSE hub (IS-046).
            RuntimeActivityListener persister = new PersistingRuntimeActivityListener(
                    dataSources, runtimeEvents, json, runtimeEventExecutor);
            RuntimeActivityListener runtimeListener =
                    new CompositeRuntimeActivityListener(persister, liveEventHub);
            // Client connect/disconnect: persist into the connection log (IS-052) and
            // fan to the live SSE hub (IS-046), both off the IPC delivery thread.
            ClientActivityListener clientPersister =
                    new PersistingClientActivityListener(clientConnections, runtimeEventExecutor);
            ClientActivityListener clientListener =
                    new CompositeClientActivityListener(clientPersister, liveEventHub);
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(),
                    HealthPolicy.DEFAULT, clientListener, runtimeListener, liveValuesHub,
                    props.governancePolicy(),
                    new WorkerNetwork(bindAddress, advertisedHost));
        }
        return new InMemoryRuntimeController();
    }

    /**
     * Single-thread executor that drains the {@link PersistingRuntimeActivityListener}'s
     * persist queue off the IPC delivery thread (IS-048). Spring owns its lifecycle and
     * calls {@code close()} on context shutdown, which performs an orderly drain
     * (awaits in-flight inserts) so queued events are not silently dropped. Daemon
     * thread so it never blocks JVM exit if a drain is interrupted.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService runtimeEventExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "runtime-event-persist");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Reuses the supervisor as the scanner when in supervisor mode (it also drives
     * workers in client mode for discovery); otherwise real-source scanning is
     * unsupported. Depends on the runtime-controller bean so exactly one supervisor
     * is created.
     */
    @Bean
    public SourceScanner sourceScanner(RuntimeController runtimeController) {
        if (runtimeController instanceof SourceScanner scanner) {
            return scanner;
        }
        return new UnsupportedSourceScanner();
    }

    /**
     * Reuses the supervisor as the live capturer when in supervisor mode (it drives a
     * worker in client mode to record a real source); otherwise capture is
     * unsupported. Depends on the runtime-controller bean so exactly one supervisor
     * is created.
     */
    @Bean
    public SourceCapturer sourceCapturer(RuntimeController runtimeController) {
        if (runtimeController instanceof SourceCapturer capturer) {
            return capturer;
        }
        return new UnsupportedSourceCapturer();
    }
}
