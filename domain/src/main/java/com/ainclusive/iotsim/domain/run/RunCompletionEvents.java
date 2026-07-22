package com.ainclusive.iotsim.domain.run;

import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import java.time.OffsetDateTime;

/**
 * Advisory helper (IS-182): appends a {@code RUN_COMPLETED}/{@code RUN_STOPPED}/
 * {@code RUN_FAILED} runtime event alongside a run's terminal {@code runs.end(...)} write,
 * so the per-source Events tab (which reads only {@code runtime_events}) shows something
 * about a run finishing. {@code dataSourceId} may be {@code null} for multi-source runs
 * (e.g. scenarios), matching {@link RuntimeEventRepository#append}'s nullable contract.
 *
 * <p>Failures are swallowed — a transient event-store error must never block or mask the
 * run's own terminal-state write, matching the existing evidence-stamping convention.
 */
public final class RunCompletionEvents {

    private RunCompletionEvents() {}

    public static void appendTerminal(RuntimeEventRepository events, String projectId,
            String dataSourceId, String runId, String terminalState, OffsetDateTime at) {
        try {
            events.append(projectId, dataSourceId, runId, "RUN_" + terminalState, at, "{}");
        } catch (RuntimeException ignored) {
            // advisory only; must not block or mask the run's terminal-state write
        }
    }
}
