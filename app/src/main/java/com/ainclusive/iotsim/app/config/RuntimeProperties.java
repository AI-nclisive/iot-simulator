package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.supervisor.RestartPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 *   <li>{@code restart} tunes restart-with-backoff on unexpected worker failure;
 *       unset fields fall back to {@link RestartPolicy#DEFAULT}.
 * </ul>
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
@ConfigurationProperties(prefix = "iotsim.runtime")
public record RuntimeProperties(String mode, Map<String, List<String>> workers, Restart restart) {

    public RuntimeProperties {
        mode = (mode == null || mode.isBlank()) ? "memory" : mode;
        // A YAML entry like `workers: {OPC_UA: }` binds to a null value; drop such
        // entries (and defensively deep-copy each command list) rather than letting
        // Map.copyOf throw a cryptic NPE at startup.
        workers = workers == null ? Map.of() : workers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        restart = restart == null ? new Restart(null, null, null, null) : restart;
    }

    public boolean isSupervisorMode() {
        return "supervisor".equalsIgnoreCase(mode);
    }

    /** Builds the {@link RestartPolicy}, applying {@link RestartPolicy#DEFAULT} for unset fields. */
    public RestartPolicy restartPolicy() {
        return restart.toPolicy();
    }

    /**
     * Restart-with-backoff tuning. Any unset field inherits {@link RestartPolicy#DEFAULT}.
     */
    public record Restart(
            Duration initialBackoff, Double multiplier, Duration maxBackoff, Integer maxRestarts) {

        RestartPolicy toPolicy() {
            RestartPolicy d = RestartPolicy.DEFAULT;
            return new RestartPolicy(
                    initialBackoff == null ? d.initialBackoff() : initialBackoff,
                    multiplier == null ? d.multiplier() : multiplier,
                    maxBackoff == null ? d.maxBackoff() : maxBackoff,
                    maxRestarts == null ? d.maxRestarts() : maxRestarts);
        }
    }
}
