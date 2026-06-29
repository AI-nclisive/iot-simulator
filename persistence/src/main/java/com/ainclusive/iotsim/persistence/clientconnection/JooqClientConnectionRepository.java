package com.ainclusive.iotsim.persistence.clientconnection;

import static com.ainclusive.iotsim.persistence.jooq.tables.ClientConnections.CLIENT_CONNECTIONS;

import com.ainclusive.iotsim.persistence.jooq.tables.ClientConnections;
import com.ainclusive.iotsim.persistence.jooq.tables.records.ClientConnectionsRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.springframework.stereotype.Repository;

/** jOOQ-backed {@link ClientConnectionRepository} (backend-specs/04). */
@Repository
public class JooqClientConnectionRepository implements ClientConnectionRepository {

    private final DSLContext dsl;

    public JooqClientConnectionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime connectedAt) {
        InsertSetMoreStep<ClientConnectionsRecord> insert = dsl.insertInto(CLIENT_CONNECTIONS)
                .set(CLIENT_CONNECTIONS.ID, Ids.newId())
                .set(CLIENT_CONNECTIONS.DATA_SOURCE_ID, dataSourceId)
                .set(CLIENT_CONNECTIONS.CLIENT_ID, clientId);
        // A null connect time falls through to the column's DB default (now()).
        if (connectedAt != null) {
            insert = insert.set(CLIENT_CONNECTIONS.CONNECTED_AT, connectedAt);
        }
        return map(insert.returning().fetchOne());
    }

    @Override
    public int close(String dataSourceId, String clientId, OffsetDateTime disconnectedAt) {
        // A client may reconnect before an earlier session was marked closed; only the
        // most-recent still-open row is the one disconnecting now.
        ClientConnections latest = CLIENT_CONNECTIONS.as("latest");
        return dsl.update(CLIENT_CONNECTIONS)
                .set(CLIENT_CONNECTIONS.DISCONNECTED_AT, disconnectedAt)
                .where(CLIENT_CONNECTIONS.ID.eq(
                        dsl.select(latest.ID)
                                .from(latest)
                                .where(latest.DATA_SOURCE_ID.eq(dataSourceId))
                                .and(latest.CLIENT_ID.eq(clientId))
                                .and(latest.DISCONNECTED_AT.isNull())
                                .orderBy(latest.CONNECTED_AT.desc(), latest.ID.desc())
                                .limit(1)))
                .execute();
    }

    @Override
    public List<ClientConnectionRow> findCurrent(String dataSourceId) {
        return dsl.selectFrom(CLIENT_CONNECTIONS)
                .where(CLIENT_CONNECTIONS.DATA_SOURCE_ID.eq(dataSourceId))
                .and(CLIENT_CONNECTIONS.DISCONNECTED_AT.isNull())
                .orderBy(CLIENT_CONNECTIONS.CONNECTED_AT.desc(), CLIENT_CONNECTIONS.ID.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
        return dsl.selectFrom(CLIENT_CONNECTIONS)
                .where(CLIENT_CONNECTIONS.DATA_SOURCE_ID.eq(dataSourceId))
                .orderBy(CLIENT_CONNECTIONS.CONNECTED_AT.desc(), CLIENT_CONNECTIONS.ID.desc())
                .fetch()
                .map(this::map);
    }

    private ClientConnectionRow map(ClientConnectionsRecord r) {
        return new ClientConnectionRow(
                r.getId(),
                r.getDataSourceId(),
                r.getClientId(),
                r.getConnectedAt(),
                r.getDisconnectedAt(),
                r.getSummary() == null ? null : r.getSummary().data());
    }
}
