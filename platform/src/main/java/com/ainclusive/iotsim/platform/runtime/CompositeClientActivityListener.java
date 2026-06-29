package com.ainclusive.iotsim.platform.runtime;

import java.util.List;

/**
 * Fans one {@link ClientActivityEvent} to several listeners in order, so a single
 * supervisor client listener can drive both persistence (the connection log, IS-052)
 * and live SSE (IS-046). Delegates must stay cheap/non-blocking — this runs on the
 * IPC thread.
 */
public final class CompositeClientActivityListener implements ClientActivityListener {

    private final List<ClientActivityListener> delegates;

    public CompositeClientActivityListener(ClientActivityListener... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void onClientActivity(ClientActivityEvent event) {
        for (ClientActivityListener delegate : delegates) {
            delegate.onClientActivity(event);
        }
    }
}
