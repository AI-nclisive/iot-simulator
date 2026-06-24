package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Selects the runtime controller: in-memory by default (local/dev, no workers),
 * or the real out-of-process {@link Supervisor} when
 * {@code iotsim.runtime.mode=supervisor}. Worker launch commands are supplied via
 * configuration in real deployments.
 */
@Configuration
public class RuntimeConfig {

    @Bean
    public RuntimeController runtimeController(Environment env) {
        String mode = env.getProperty("iotsim.runtime.mode", "memory");
        if ("supervisor".equalsIgnoreCase(mode)) {
            return new Supervisor(new ProcessWorkerLauncher(Map.of()));
        }
        return new InMemoryRuntimeController();
    }
}
