package com.ainclusive.iotsim.domain.runtimeevent;

import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventQuery;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reads the runtime-event history (IS-055) backing {@code GET .../runtime-events}.
 * Translates an API-level {@link RuntimeEventHistoryRequest} (instant times, opaque
 * cursor, requested page size) into the persistence {@link RuntimeEventQuery}
 * (UTC offsets, keyset bounds, fixed limit): it clamps the page size, decodes the
 * cursor, over-fetches one row to know whether a further page exists, and hands
 * back the next opaque cursor. See backend-specs/05_API_CONTRACT.md.
 */
@Service
public class RuntimeEventHistoryService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final RuntimeEventRepository events;

    public RuntimeEventHistoryService(RuntimeEventRepository events) {
        this.events = events;
    }

    public RuntimeEventHistoryPage history(RuntimeEventHistoryRequest request) {
        int pageSize = clamp(request.limit());
        RuntimeEventQuery.Builder query = RuntimeEventQuery.forProject(request.projectId())
                .dataSourceId(request.dataSourceId())
                .runId(request.runId())
                .type(request.type())
                .from(toOffset(request.from()))
                .to(toOffset(request.to()))
                .limit(pageSize + 1); // over-fetch one to detect a further page
        Cursor cursor = decode(request.cursor());
        if (cursor != null) {
            query.before(cursor.at(), cursor.id());
        }

        List<RuntimeEventRow> rows = events.query(query.build());
        boolean hasMore = rows.size() > pageSize;
        List<RuntimeEventRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<RuntimeEventView> views = pageRows.stream().map(RuntimeEventHistoryService::toView).toList();
        String nextCursor = hasMore ? encode(pageRows.get(pageRows.size() - 1)) : null;
        return new RuntimeEventHistoryPage(views, nextCursor);
    }

    private static int clamp(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static RuntimeEventView toView(RuntimeEventRow row) {
        return new RuntimeEventView(
                row.id(), row.type(), row.at().toInstant(),
                row.dataSourceId(), row.runId(), row.payloadJson());
    }

    // --- opaque keyset cursor: base64url("<atEpochMicros>:<id>") ---

    private record Cursor(OffsetDateTime at, long id) {}

    private static String encode(RuntimeEventRow row) {
        String raw = toMicros(row.at().toInstant()) + ":" + row.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            if (sep < 0) {
                throw new IllegalArgumentException("cursor missing separator");
            }
            long micros = Long.parseLong(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            Instant at = Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
            return new Cursor(at.atOffset(ZoneOffset.UTC), id);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid runtime-event cursor", e);
        }
    }

    private static long toMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000 + instant.getNano() / 1_000;
    }
}
