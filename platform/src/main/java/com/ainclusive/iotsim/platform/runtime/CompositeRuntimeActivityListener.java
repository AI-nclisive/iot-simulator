package com.ainclusive.iotsim.platform.runtime;

import java.util.List;

/**
 * Fans one {@link RuntimeActivityEvent} to several listeners in order, so a single
 * supervisor runtime listener can drive both persistence (IS-049) and live SSE
 * (IS-046). Delegates must stay cheap/non-blocking — this runs on the IPC thread.
 */
public final class CompositeRuntimeActivityListener implements RuntimeActivityListener {

    private final List<RuntimeActivityListener> delegates;

    public CompositeRuntimeActivityListener(RuntimeActivityListener... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        for (RuntimeActivityListener delegate : delegates) {
            delegate.onRuntimeActivity(event);
        }
    }
}
