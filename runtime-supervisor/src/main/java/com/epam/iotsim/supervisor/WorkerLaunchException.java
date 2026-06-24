package com.epam.iotsim.supervisor;

/** A worker could not be launched or did not become ready. */
public class WorkerLaunchException extends RuntimeException {

    public WorkerLaunchException(String message) {
        super(message);
    }

    public WorkerLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
