package com.ainclusive.iotsim.persistence.datasource;

import static com.ainclusive.iotsim.persistence.jooq.tables.DataSources.DATA_SOURCES;

import com.ainclusive.iotsim.persistence.jooq.tables.records.DataSourcesRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class JooqDataSourceRepository implements DataSourceRepository {

    private final DSLContext dsl;

    public JooqDataSourceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson, String createdBy) {
        DataSourcesRecord record = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, projectId)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.PROTOCOL, protocol)
                .set(DATA_SOURCES.BASIS, basis)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public List<DataSourceRow> findAll() {
        return dsl.selectFrom(DATA_SOURCES)
                .orderBy(DATA_SOURCES.CREATED_AT.desc(), DATA_SOURCES.ID.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<DataSourceRow> findByProject(String projectId) {
        return dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.PROJECT_ID.eq(projectId))
                .orderBy(DATA_SOURCES.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
            OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(DATA_SOURCES).where(DATA_SOURCES.PROJECT_ID.eq(projectId));
        if (protocol != null) {
            q = q.and(DATA_SOURCES.PROTOCOL.eq(protocol));
        }
        if (afterAt != null) {
            q = q.and(DATA_SOURCES.CREATED_AT.lt(afterAt)
                    .or(DATA_SOURCES.CREATED_AT.eq(afterAt).and(DATA_SOURCES.ID.lt(afterId))));
        }
        return q.orderBy(DATA_SOURCES.CREATED_AT.desc(), DATA_SOURCES.ID.desc())
                .limit(limit)
                .fetch()
                .map(this::map);
    }

    @Override
    public Optional<DataSourceRow> findById(String id) {
        return dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, boolean enabled, long expectedVersion) {
        DataSourcesRecord record = dsl.update(DATA_SOURCES)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.ENABLED, enabled)
                .set(DATA_SOURCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(DATA_SOURCES.VERSION, DATA_SOURCES.VERSION.plus(1))
                .where(DATA_SOURCES.ID.eq(id).and(DATA_SOURCES.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
        DataSourcesRecord source = dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.ID.eq(sourceId))
                .fetchOne();
        if (source == null) {
            return Optional.empty();
        }
        DataSourcesRecord copy = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, source.getProjectId())
                .set(DATA_SOURCES.NAME, newName)
                .set(DATA_SOURCES.PROTOCOL, source.getProtocol())
                .set(DATA_SOURCES.BASIS, source.getBasis())
                .set(DATA_SOURCES.SIMULATOR_PORT, source.getSimulatorPort())
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, source.getRealDeviceEndpoint())
                .set(DATA_SOURCES.RUNTIME_CONFIG, source.getRuntimeConfig())
                .set(DATA_SOURCES.ENABLED, false)
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return Optional.ofNullable(copy).map(this::map);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(DATA_SOURCES).where(DATA_SOURCES.ID.eq(id)).execute() > 0;
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }

    private static String jsonString(JSONB value) {
        return value == null ? null : value.data();
    }

    /**
     * Stores the real device endpoint — a plain connection URL such as {@code opc.tcp://host:4840} — as a
     * JSON string scalar so it round-trips through the {@code real_device_endpoint} jsonb column.
     * A bare URL is not valid JSON on its own, so it is escaped and quoted;
     * {@code null} becomes the JSON null literal.
     */
    private static JSONB endpointToJsonb(String endpoint) {
        return endpoint == null ? JSONB.valueOf("null") : JSONB.valueOf(quoteJson(endpoint));
    }

    /** Inverse of {@link #endpointToJsonb}: decodes the stored JSON string scalar back to a plain URL. */
    private static String endpointFromJsonb(JSONB value) {
        String data = value == null ? null : value.data();
        if (data == null || "null".equals(data)) {
            return null;
        }
        String trimmed = data.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
            // Legacy non-string jsonb (e.g. the historical "{}" default) carries no usable URL.
            return null;
        }
        return unquoteJson(trimmed.substring(1, trimmed.length() - 1));
    }

    private static String quoteJson(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String unquoteJson(String escaped) {
        StringBuilder sb = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c != '\\' || i + 1 >= escaped.length()) {
                sb.append(c);
                continue;
            }
            char next = escaped.charAt(++i);
            switch (next) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 5 > escaped.length()) { sb.append('u'); break; }
                    try {
                        sb.append((char) Integer.parseInt(escaped.substring(i + 1, i + 5), 16));
                        i += 4;
                    } catch (NumberFormatException ignored) {
                        sb.append('u');
                    }
                }
                default -> sb.append(next);
            }
        }
        return sb.toString();
    }

    private DataSourceRow map(DataSourcesRecord r) {
        return new DataSourceRow(
                r.getId(),
                r.getProjectId(),
                r.getName(),
                r.getProtocol(),
                r.getBasis(),
                r.getSchemaId(),
                r.getSchemaVersion(),
                r.getSimulatorPort(),
                endpointFromJsonb(r.getRealDeviceEndpoint()),
                jsonString(r.getRuntimeConfig()),
                Boolean.TRUE.equals(r.getEnabled()),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
}
