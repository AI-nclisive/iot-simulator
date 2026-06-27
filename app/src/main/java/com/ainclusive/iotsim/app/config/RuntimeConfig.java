package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.scan.UnsupportedSourceScanner;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RuntimeController runtimeController(RuntimeProperties props) {
        if (props.isSupervisorMode()) {
            return new Supervisor(new ProcessWorkerLauncher(props.workers()), props.restartPolicy());
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
}
