package com.epam.iotsim.supervisor;

/**
 * Launches a protocol worker bound to a loopback control port. The supervisor
 * stays protocol-agnostic; concrete launchers know how to start each worker.
 */
public interface WorkerLauncher {

    LaunchedWorker launch(String protocol, int controlPort) throws Exception;
}
