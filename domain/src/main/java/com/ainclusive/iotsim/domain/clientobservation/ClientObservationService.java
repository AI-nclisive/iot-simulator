package com.ainclusive.iotsim.domain.clientobservation;

import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Connected-client observation per data source (IS-052): the currently-connected
 * clients and the full connection history, projected from the {@code
 * client_connections} log (backend-specs/04 & 05). Backs both the REST
 * {@code GET .../clients} endpoint and the SSE clients-stream snapshot.
 */
@Service
public class ClientObservationService {

    private final ClientConnectionRepository clients;

    public ClientObservationService(ClientConnectionRepository clients) {
        this.clients = clients;
    }

    /** Clients currently connected to the source, newest first. */
    public List<ClientConnectionView> current(String dataSourceId) {
        return clients.findCurrent(dataSourceId).stream().map(ClientObservationService::toView).toList();
    }

    /** Full connection log for the source (connected and disconnected), newest first. */
    public List<ClientConnectionView> history(String dataSourceId) {
        return clients.findByDataSource(dataSourceId).stream().map(ClientObservationService::toView).toList();
    }

    private static ClientConnectionView toView(ClientConnectionRow row) {
        OffsetDateTime disconnectedAt = row.disconnectedAt();
        return new ClientConnectionView(
                row.clientId(),
                row.connectedAt() == null ? null : row.connectedAt().toInstant(),
                disconnectedAt == null ? null : disconnectedAt.toInstant(),
                disconnectedAt == null);
    }
}
