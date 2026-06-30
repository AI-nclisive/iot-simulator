package com.ainclusive.iotsim.platform.runtime;

/**
 * A data source could not be started because the supervisor is at its
 * concurrent-worker cap (resource governance, backend-specs/02 §5). The request
 * is valid — the system is simply at capacity — so the API maps this to
 * 503 Service Unavailable; retry after stopping a running source or raising the
 * cap.
 */
public class RuntimeCapacityException extends RuntimeException {

    public RuntimeCapacityException(String message) {
        super(message);
    }
}
