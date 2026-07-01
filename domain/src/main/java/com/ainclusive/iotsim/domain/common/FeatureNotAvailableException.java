package com.ainclusive.iotsim.domain.common;

/** A structurally valid request targets a capability not yet implemented (→ HTTP 501). */
public class FeatureNotAvailableException extends RuntimeException {
    public FeatureNotAvailableException(String message) {
        super(message);
    }
}
