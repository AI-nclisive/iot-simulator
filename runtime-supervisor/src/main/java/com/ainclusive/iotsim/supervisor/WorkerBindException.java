package com.ainclusive.iotsim.supervisor;

/** The worker reported it could not bind its listen port (Ack ok=false on Start). */
public class WorkerBindException extends RuntimeException {

    public WorkerBindException(String message) {
        super(message);
    }
}
