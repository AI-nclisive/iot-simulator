package com.ainclusive.iotsim.domain.runtimeevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventQuery;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeEventHistoryServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void defaultsLimitAndOverFetchesByOneToDetectNextPage() {
        CapturingRepo repo = new CapturingRepo(List.of());
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        service.history(new RuntimeEventHistoryRequest("p1", null, null, null, null, null, null, null));

        // Default page is 50; the service over-fetches one row to know if more exist.
        assertThat(repo.lastQuery.limit()).isEqualTo(51);
        assertThat(repo.lastQuery.projectId()).isEqualTo("p1");
    }

    @Test
    void clampsLimitToMaximum() {
        CapturingRepo repo = new CapturingRepo(List.of());
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        service.history(new RuntimeEventHistoryRequest("p1", null, null, null, null, null, null, 10_000));

        assertThat(repo.lastQuery.limit()).isEqualTo(201); // max 200 + 1
    }

    @Test
    void returnsNextCursorWhenMorePagesExistAndTrimsToPageSize() {
        // Repo returns 3 rows for an effective page of 2 (limit 2 → over-fetch 3).
        List<RuntimeEventRow> rows = List.of(
                row(3, "E3", T0.plusSeconds(20)),
                row(2, "E2", T0.plusSeconds(10)),
                row(1, "E1", T0));
        CapturingRepo repo = new CapturingRepo(rows);
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        RuntimeEventHistoryPage page = service.history(
                new RuntimeEventHistoryRequest("p1", null, null, null, null, null, null, 2));

        assertThat(page.events()).extracting(RuntimeEventView::type).containsExactly("E3", "E2");
        assertThat(page.nextCursor()).isNotNull();

        // Feeding the cursor back resolves to the last returned row's keyset position.
        service.history(new RuntimeEventHistoryRequest("p1", null, null, null, null, null, page.nextCursor(), 2));
        assertThat(repo.lastQuery.beforeId()).isEqualTo(2L);
        assertThat(repo.lastQuery.beforeAt()).isEqualTo(T0.plusSeconds(10).atOffset(ZoneOffset.UTC));
    }

    @Test
    void noNextCursorWhenRepoReturnsAtMostPageSize() {
        List<RuntimeEventRow> rows = List.of(row(2, "E2", T0.plusSeconds(10)), row(1, "E1", T0));
        CapturingRepo repo = new CapturingRepo(rows);
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        RuntimeEventHistoryPage page = service.history(
                new RuntimeEventHistoryRequest("p1", null, null, null, null, null, null, 2));

        assertThat(page.events()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void passesFiltersThroughAndConvertsInstantsToUtcOffsets() {
        CapturingRepo repo = new CapturingRepo(List.of());
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        Instant from = T0;
        Instant to = T0.plusSeconds(60);
        service.history(new RuntimeEventHistoryRequest(
                "p1", "pump", "runX", "ERROR", from, to, null, 5));

        assertThat(repo.lastQuery.dataSourceId()).isEqualTo("pump");
        assertThat(repo.lastQuery.runId()).isEqualTo("runX");
        assertThat(repo.lastQuery.type()).isEqualTo("ERROR");
        assertThat(repo.lastQuery.from()).isEqualTo(from.atOffset(ZoneOffset.UTC));
        assertThat(repo.lastQuery.to()).isEqualTo(to.atOffset(ZoneOffset.UTC));
    }

    @Test
    void mapsRowToViewWithInstantTimeAndRawPayload() {
        CapturingRepo repo = new CapturingRepo(List.of(
                new RuntimeEventRow(7, "p1", "pump", "runX", "ERROR",
                        T0.atOffset(ZoneOffset.UTC), "{\"reason\":\"timeout\"}")));
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(repo);

        RuntimeEventView view = service.history(
                new RuntimeEventHistoryRequest("p1", null, null, null, null, null, null, 10)).events().get(0);

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.type()).isEqualTo("ERROR");
        assertThat(view.at()).isEqualTo(T0);
        assertThat(view.dataSourceId()).isEqualTo("pump");
        assertThat(view.runId()).isEqualTo("runX");
        assertThat(view.payloadJson()).isEqualTo("{\"reason\":\"timeout\"}");
    }

    @Test
    void rejectsMalformedCursor() {
        RuntimeEventHistoryService service = new RuntimeEventHistoryService(new CapturingRepo(List.of()));

        assertThatThrownBy(() -> service.history(
                new RuntimeEventHistoryRequest("p1", null, null, null, null, null, "not-a-cursor", 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RuntimeEventRow row(long id, String type, Instant at) {
        return new RuntimeEventRow(id, "p1", null, null, type, at.atOffset(ZoneOffset.UTC), "{}");
    }

    /** Captures the query it was handed and replays a fixed result list. */
    private static final class CapturingRepo implements RuntimeEventRepository {
        private final List<RuntimeEventRow> result;
        private RuntimeEventQuery lastQuery;

        private CapturingRepo(List<RuntimeEventRow> result) {
            this.result = result;
        }

        @Override
        public List<RuntimeEventRow> query(RuntimeEventQuery filter) {
            this.lastQuery = filter;
            return result;
        }

        @Override
        public RuntimeEventRow append(String p, String d, String r, String t, OffsetDateTime a, String j) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RuntimeEventRow> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RuntimeEventRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RuntimeEventRow> findByRun(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RuntimeEventRow> findByDataSource(String dataSourceId) {
            throw new UnsupportedOperationException();
        }
    }
}
