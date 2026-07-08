package com.ainclusive.iotsim.domain.activityevent;

import com.ainclusive.iotsim.persistence.activityevent.ActivityEventQuery;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRepository;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Records user-initiated activity events (IS-083) and serves the paginated activity
 * history backing {@code GET .../activity}. Other services call {@link #emit} after
 * mutating system state to write one event per action. Paging is keyset via an opaque
 * base64url cursor; the service over-fetches one row to know whether a further page exists.
 */
@Service
public class ActivityEventService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final ActivityEventRepository events;

    public ActivityEventService(ActivityEventRepository events) {
        this.events = events;
    }

    /**
     * Records one activity event. {@code projectId} and {@code objectId} may be
     * {@code null} for admin-level actions not tied to a project or object.
     */
    public void emit(String projectId, String actor, String action,
            String objectType, String objectId, String detailJson) {
        events.append(projectId, actor, action, objectType, objectId, detailJson);
    }

    /** Convenience overload without detail. */
    public void emit(String projectId, String actor, String action,
            String objectType, String objectId) {
        emit(projectId, actor, action, objectType, objectId, null);
    }

    public ActivityEventHistoryPage history(ActivityEventHistoryRequest request) {
        int pageSize = clamp(request.limit());
        ActivityEventQuery.Builder qb = ActivityEventQuery.builder()
                .projectId(request.projectId())
                .actor(request.actor())
                .action(request.action())
                .objectType(request.objectType())
                .from(toOffset(request.from()))
                .to(toOffset(request.to()))
                .limit(pageSize + 1);

        Cursor cursor = decode(request.cursor());
        if (cursor != null) {
            qb.before(cursor.at(), cursor.id());
        }
        ActivityEventQuery query = qb.build();

        List<ActivityEventRow> rows = events.query(query);
        boolean hasMore = rows.size() > pageSize;
        List<ActivityEventRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<ActivityEventView> views = pageRows.stream().map(ActivityEventService::toView).toList();
        String nextCursor = hasMore ? encode(pageRows.get(pageRows.size() - 1)) : null;
        return new ActivityEventHistoryPage(views, nextCursor);
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

    private static ActivityEventView toView(ActivityEventRow row) {
        return new ActivityEventView(
                row.id(), row.projectId(), row.actor(), row.action(),
                row.objectType(), row.objectId(), row.at().toInstant(), row.detailJson());
    }

    // --- opaque keyset cursor: base64url("<atEpochMicros>:<id>") ---

    private record Cursor(OffsetDateTime at, long id) {}

    private static String encode(ActivityEventRow row) {
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
            throw new IllegalArgumentException("invalid activity-event cursor", e);
        }
    }

    private static long toMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000 + instant.getNano() / 1_000;
    }
}
