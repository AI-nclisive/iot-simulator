package com.ainclusive.iotsim.app.runtime;

import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists supervisor client-activity events (IS-047) into {@code client_connections}
 * (IS-052): a {@code CONNECTED} opens a connection row, a {@code DISCONNECTED} closes
 * the matching open row. {@code SUBSCRIPTION} is subscription activity, not a
 * connection lifecycle change, so it is not recorded here. The supervisor calls this
 * on an IPC delivery thread, so the blocking insert/update is handed to {@code
 * executor}.
 */
public final class PersistingClientActivityListener implements ClientActivityListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingClientActivityListener.class);

    private final ClientConnectionRepository clients;
    private final Executor executor;

    public PersistingClientActivityListener(ClientConnectionRepository clients, Executor executor) {
        this.clients = clients;
        this.executor = executor;
    }

    @Override
    public void onClientActivity(ClientActivityEvent event) {
        executor.execute(() -> persist(event));
    }

    private void persist(ClientActivityEvent event) {
        try {
            switch (event.kind()) {
                case CONNECTED -> clients.open(
                        event.dataSourceId(), event.clientId(), event.at().atOffset(ZoneOffset.UTC));
                case DISCONNECTED -> clients.close(
                        event.dataSourceId(), event.clientId(), event.at().atOffset(ZoneOffset.UTC));
                case SUBSCRIPTION -> {
                    // Subscription activity is not a connection open/close; nothing to persist.
                }
            }
        } catch (RuntimeException e) {
            log.warn("failed to persist {} event for source {}", event.kind(), event.dataSourceId(), e);
        }
    }
}
