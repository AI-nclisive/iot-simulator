package com.ainclusive.iotsim.domain.common;

/** A source cannot start because its listen port is already bound by another running source (→ 409). */
public class PortInUseException extends RuntimeException {
    public PortInUseException(int port, String conflictingSourceId) {
        super("listen port " + port + " is already in use by running source " + conflictingSourceId);
    }
}
