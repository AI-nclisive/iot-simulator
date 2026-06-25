package com.ainclusive.iotsim.supervisor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Launches a worker as a child process. {@code commandsByProtocol} maps a
 * protocol to the base command (e.g. the worker's install-dist launcher script);
 * the control port is appended as the final argument.
 *
 * <p>Production adapter for {@link WorkerLauncher}. The supervisor orchestration
 * is covered by tests with an in-process launcher; this adapter spawns the real
 * packaged worker (installDist) and is exercised by the spawn integration tests.
 */
public final class ProcessWorkerLauncher implements WorkerLauncher {

    /** Grace period for a graceful exit before the process is force-killed. */
    private static final Duration TERMINATE_GRACE = Duration.ofSeconds(5);

    private final Map<String, List<String>> commandsByProtocol;

    public ProcessWorkerLauncher(Map<String, List<String>> commandsByProtocol) {
        this.commandsByProtocol = Map.copyOf(commandsByProtocol);
    }

    @Override
    public LaunchedWorker launch(String protocol, int controlPort) throws Exception {
        List<String> base = commandsByProtocol.get(protocol);
        if (base == null || base.isEmpty()) {
            throw new WorkerLaunchException("no worker command configured for protocol " + protocol);
        }
        List<String> command = new ArrayList<>(base);
        command.add(Integer.toString(controlPort));
        Process process = new ProcessBuilder(command).inheritIO().start();
        return new ProcessLaunchedWorker(process);
    }

    /** {@link LaunchedWorker} backed by a child {@link Process}. */
    private record ProcessLaunchedWorker(Process process) implements LaunchedWorker {

        @Override
        public void close() {
            terminate(process);
        }

        @Override
        public CompletionStage<Void> onExit() {
            // Completes whether the process crashed or we asked it to exit; the
            // supervisor decides whether the exit was unexpected.
            return process.onExit().thenAccept(p -> {});
        }
    }

    /** Asks the worker to exit, then force-kills it if it overstays the grace period. */
    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(TERMINATE_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
