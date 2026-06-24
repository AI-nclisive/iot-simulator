package com.ainclusive.iotsim.supervisor;

/** A worker reported an incompatible contract major version; it must be refused. */
public class WorkerContractMismatchException extends RuntimeException {

    public WorkerContractMismatchException(String expected, String actual) {
        super("Worker contract version mismatch: supervisor=" + expected + ", worker=" + actual);
    }
}
