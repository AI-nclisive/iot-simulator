package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.app.runtime.PersistingRuntimeActivityListener;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.capture.UnsupportedSourceCapturer;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.scan.UnsupportedSourceScanner;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents, ObjectMapper json) {
        if (props.isSupervisorMode()) {
            // Persist runtime events off the IPC delivery thread (IS-048): the listener
            // is called on a gRPC thread that must stay non-blocking.
            Executor executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "runtime-event-persist");
                t.setDaemon(true);
                return t;
            });
            RuntimeActivityListener listener = new PersistingRuntimeActivityListener(
                    dataSources, runtimeEvents, json, executor);
            return new Supervisor(
                    new ProcessWorkerLauncher(props.workers()), props.restartPolicy(), listener);
        }
        return new InMemoryRuntimeController();
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
