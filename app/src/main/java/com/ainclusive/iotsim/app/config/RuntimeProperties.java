package com.ainclusive.iotsim.app.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime controller configuration.
 *
 * <ul>
 *   <li>{@code mode} selects the controller: {@code memory} (default, no workers)
 *       or {@code supervisor} (real out-of-process workers).
 *   <li>{@code workers} maps a protocol id (e.g. {@code OPC_UA}) to the base launch
 *       command for its packaged worker — typically the installDist launcher
 *       script. The supervisor appends the loopback control port as the final
 *       argument.
 * </ul>
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
@ConfigurationProperties(prefix = "iotsim.runtime")
public record RuntimeProperties(String mode, Map<String, List<String>> workers) {

    public RuntimeProperties {
        mode = (mode == null || mode.isBlank()) ? "memory" : mode;
        workers = workers == null ? Map.of() : Map.copyOf(workers);
    }

    public boolean isSupervisorMode() {
        return "supervisor".equalsIgnoreCase(mode);
    }
}
