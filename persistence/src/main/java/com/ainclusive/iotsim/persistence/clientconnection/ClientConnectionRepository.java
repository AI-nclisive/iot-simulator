package com.ainclusive.iotsim.persistence.clientconnection;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Store for client connections observed at running data sources (the
 * {@code ClientEvents} worker stream, IS-047). A connection is {@link #open opened}
 * on connect and {@link #close closed} on disconnect; reads are time-ordered
 * (newest first) and back connected-client observation (IS-052) and run evidence
 * (IS-057). See backend-specs/04.
 */
public interface ClientConnectionRepository {

    /**
     * Records a new open connection (generates the id). {@code connectedAt} may be
     * {@code null} to default to the insert time.
     */
    ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime connectedAt);

    /**
     * Closes the most-recent still-open connection for {@code (dataSourceId,
     * clientId)} by setting its {@code disconnected_at}. Returns the number of rows
     * updated (0 if none was open, otherwise 1).
     */
    int close(String dataSourceId, String clientId, OffsetDateTime disconnectedAt);

    /** Currently-connected clients for a source ({@code disconnected_at IS NULL}), newest first. */
    List<ClientConnectionRow> findCurrent(String dataSourceId);

    /** Full connection log for a source, newest first (index-backed: data_source_id, connected_at). */
    List<ClientConnectionRow> findByDataSource(String dataSourceId);
}
