package com.ainclusive.iotsim.worker.opcua;

/** Raised when the OPC UA server cannot bind its configured listen port. */
public class BindFailedException extends RuntimeException {
    public BindFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
