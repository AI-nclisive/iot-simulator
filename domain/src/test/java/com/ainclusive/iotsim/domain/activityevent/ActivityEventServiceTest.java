package com.ainclusive.iotsim.domain.activityevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.persistence.activityevent.ActivityEventQuery;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRepository;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityEventServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void defaultsLimitAndOverFetchesByOneToDetectNextPage() {
        CapturingRepo repo = new CapturingRepo(List.of());
        ActivityEventService service = new ActivityEventService(repo);

        service.history(new ActivityEventHistoryRequest(null, null, null, null, null, null, null, null));

        assertThat(repo.lastQuery.limit()).isEqualTo(51);
    }

    @Test
    void clampsLimitToMaximum() {
        CapturingRepo repo = new CapturingRepo(List.of());
        ActivityEventService service = new ActivityEventService(repo);

        service.history(new ActivityEventHistoryRequest(null, null, null, null, null, null, null, 10_000));

        assertThat(repo.lastQuery.limit()).isEqualTo(201); // max 200 + 1
    }

    @Test
    void returnsNextCursorWhenMorePagesExistAndTrimsToPageSize() {
        List<ActivityEventRow> rows = List.of(
                row(3, "alice", "create", T0.plusSeconds(20)),
                row(2, "bob", "delete", T0.plusSeconds(10)),
                row(1, "alice", "start", T0));
        CapturingRepo repo = new CapturingRepo(rows);
        ActivityEventService service = new ActivityEventService(repo);

        ActivityEventHistoryPage page = service.history(
                new ActivityEventHistoryRequest(null, null, null, null, null, null, null, 2));

        assertThat(page.events()).extracting(ActivityEventView::action).containsExactly("create", "delete");
        assertThat(page.nextCursor()).isNotNull();

        // Feeding the cursor back resolves to the last returned row's keyset position.
        service.history(new ActivityEventHistoryRequest(null, null, null, null, null, null, page.nextCursor(), 2));
        assertThat(repo.lastQuery.beforeId()).isEqualTo(2L);
        assertThat(repo.lastQuery.beforeAt()).isEqualTo(T0.plusSeconds(10).atOffset(ZoneOffset.UTC));
    }

    @Test
    void noNextCursorWhenRepoReturnsAtMostPageSize() {
        List<ActivityEventRow> rows = List.of(
                row(2, "bob", "delete", T0.plusSeconds(10)),
                row(1, "alice", "create", T0));
        CapturingRepo repo = new CapturingRepo(rows);
        ActivityEventService service = new ActivityEventService(repo);

        ActivityEventHistoryPage page = service.history(
                new ActivityEventHistoryRequest(null, null, null, null, null, null, null, 2));

        assertThat(page.events()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void passesFiltersThroughAndConvertsInstantsToUtcOffsets() {
        CapturingRepo repo = new CapturingRepo(List.of());
        ActivityEventService service = new ActivityEventService(repo);

        Instant from = T0;
        Instant to = T0.plusSeconds(60);
        service.history(new ActivityEventHistoryRequest("proj1", "alice", "create", "data_source", from, to, null, 5));

        assertThat(repo.lastQuery.projectId()).isEqualTo("proj1");
        assertThat(repo.lastQuery.actor()).isEqualTo("alice");
        assertThat(repo.lastQuery.action()).isEqualTo("create");
        assertThat(repo.lastQuery.objectType()).isEqualTo("data_source");
        assertThat(repo.lastQuery.from()).isEqualTo(from.atOffset(ZoneOffset.UTC));
        assertThat(repo.lastQuery.to()).isEqualTo(to.atOffset(ZoneOffset.UTC));
    }

    @Test
    void mapsRowToViewWithInstantTimeAndRawDetail() {
        CapturingRepo repo = new CapturingRepo(List.of(
                new ActivityEventRow(7, "p1", "alice", "create", "data_source", "ds-1",
                        T0.atOffset(ZoneOffset.UTC), "{\"name\":\"pump\"}")));
        ActivityEventService service = new ActivityEventService(repo);

        ActivityEventView view = service.history(
                new ActivityEventHistoryRequest(null, null, null, null, null, null, null, 10))
                .events().get(0);

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.actor()).isEqualTo("alice");
        assertThat(view.action()).isEqualTo("create");
        assertThat(view.objectType()).isEqualTo("data_source");
        assertThat(view.objectId()).isEqualTo("ds-1");
        assertThat(view.at()).isEqualTo(T0);
        assertThat(view.detailJson()).isEqualTo("{\"name\":\"pump\"}");
    }

    @Test
    void rejectsMalformedCursor() {
        ActivityEventService service = new ActivityEventService(new CapturingRepo(List.of()));

        assertThatThrownBy(() -> service.history(
                new ActivityEventHistoryRequest(null, null, null, null, null, null, "not-a-cursor", 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emitDelegatesToRepository() {
        CapturingRepo repo = new CapturingRepo(List.of());
        ActivityEventService service = new ActivityEventService(repo);

        service.emit("proj1", "alice", "create", "data_source", "ds-1");

        assertThat(repo.lastAppend).isNotNull();
        assertThat(repo.lastAppend.actor()).isEqualTo("alice");
        assertThat(repo.lastAppend.action()).isEqualTo("create");
        assertThat(repo.lastAppend.objectType()).isEqualTo("data_source");
        assertThat(repo.lastAppend.objectId()).isEqualTo("ds-1");
    }

    private static ActivityEventRow row(long id, String actor, String action, Instant at) {
        return new ActivityEventRow(id, "p1", actor, action, "data_source", null,
                at.atOffset(ZoneOffset.UTC), "{}");
    }

    /** Captures query/append calls and replays a fixed result list. */
    private static final class CapturingRepo implements ActivityEventRepository {
        private final List<ActivityEventRow> result;
        ActivityEventQuery lastQuery;
        ActivityEventRow lastAppend;

        private CapturingRepo(List<ActivityEventRow> result) {
            this.result = result;
        }

        @Override
        public ActivityEventRow append(String projectId, String actor, String action,
                String objectType, String objectId, String detailJson) {
            lastAppend = new ActivityEventRow(
                    1, projectId, actor, action, objectType, objectId,
                    OffsetDateTime.now(ZoneOffset.UTC), detailJson);
            return lastAppend;
        }

        @Override
        public List<ActivityEventRow> query(ActivityEventQuery filter) {
            this.lastQuery = filter;
            return result;
        }
    }
}
