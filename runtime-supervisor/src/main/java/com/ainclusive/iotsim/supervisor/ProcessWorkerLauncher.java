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

    /**
     * Tears down the worker <em>and its descendant processes</em>, then force-kills
     * anything that overstays the grace period.
     *
     * <p>The worker is launched through a wrapper script (e.g. installDist's
     * {@code worker-opcua.bat} → {@code cmd.exe} → the worker JVM), so the real
     * worker is a <em>descendant</em>, not the direct child. {@link Process#destroy()}
     * does not propagate to descendants on Windows, so destroying only the wrapper
     * orphans the worker JVM — and an orphan that inherited our I/O can keep a pipe
     * open and stall the spawning JVM. We therefore snapshot the process tree and
     * tear the whole tree down.
     *
     * <p>Package-private only so the teardown is testable
     * ({@code ProcessWorkerLauncherTerminateTest}); it is not an intended API surface.
     */
    static void terminate(Process process) {
        // Snapshot before destroy(): once the wrapper exits the parent/child links are
        // gone and descendants() would return nothing.
        List<ProcessHandle> descendants = process.descendants().toList();
        process.destroy();
        descendants.forEach(ProcessHandle::destroy);
        try {
            process.waitFor(TERMINATE_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Force-kill the wrapper and any descendant (notably the worker JVM, which
        // destroy() does not reach on Windows) that ignored the graceful request.
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
}
