package com.epam.iotsim.supervisor;

import java.util.List;
import java.util.Map;

/**
 * Launches a worker as a child process. {@code commandsByProtocol} maps a
 * protocol to the base command (e.g. the worker's install-dist launcher script);
 * the control port is appended as the final argument.
 *
 * <p>Production adapter for {@link WorkerLauncher}. The supervisor orchestration
 * is covered by tests with an in-process launcher; this adapter is exercised in
 * real deployments.
 */
public final class ProcessWorkerLauncher implements WorkerLauncher {

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
        List<String> command = new java.util.ArrayList<>(base);
        command.add(Integer.toString(controlPort));
        Process process = new ProcessBuilder(command).inheritIO().start();
        return process::destroy;
    }
}
