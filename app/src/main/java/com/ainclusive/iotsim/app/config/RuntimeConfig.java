package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the runtime controller: in-memory by default (local/dev, no workers),
 * or the real out-of-process {@link Supervisor} when
 * {@code iotsim.runtime.mode=supervisor}. Worker launch commands are supplied via
 * {@link RuntimeProperties} in real deployments.
 */
@Configuration
@EnableConfigurationProperties(RuntimeProperties.class)
public class RuntimeConfig {

    @Bean
    public RuntimeController runtimeController(RuntimeProperties props) {
        if (props.isSupervisorMode()) {
            return new Supervisor(new ProcessWorkerLauncher(props.workers()));
        }
        return new InMemoryRuntimeController();
    }
}
