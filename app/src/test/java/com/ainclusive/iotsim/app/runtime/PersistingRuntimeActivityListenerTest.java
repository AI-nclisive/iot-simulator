package com.ainclusive.iotsim.app.runtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PersistingRuntimeActivityListenerTest {

    private final DataSourceRepository sources = mock(DataSourceRepository.class);
    private final RuntimeEventRepository events = mock(RuntimeEventRepository.class);
    private final PersistingRuntimeActivityListener listener = new PersistingRuntimeActivityListener(
            sources, events, new ObjectMapper(), Runnable::run);

    private static DataSourceRow row(String id, String projectId) {
        return new DataSourceRow(id, projectId, "name", "OPC_UA", "SCAN", null, null, null, null,
                true, OffsetDateTime.now(), OffsetDateTime.now(), null, 1L);
    }

    @Test
    void appendsRowWithResolvedProjectEmptyPayloadAndNullRun() {
        when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null));

        verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("SOURCE_START"),
                eq(Instant.ofEpochSecond(5).atOffset(ZoneOffset.UTC)), eq("{}"));
    }

    @Test
    void wrapsDetailIntoJsonPayload() {
        when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("ds1", "ERROR", Instant.ofEpochSecond(7), "boom"));

        verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("ERROR"),
                eq(Instant.ofEpochSecond(7).atOffset(ZoneOffset.UTC)), eq("{\"detail\":\"boom\"}"));
    }

    @Test
    void dropsEventForUnknownDataSource() {
        when(sources.findById("gone")).thenReturn(Optional.empty());

        listener.onRuntimeActivity(
                new RuntimeActivityEvent("gone", "SOURCE_STOP", Instant.ofEpochSecond(9), null));

        verifyNoInteractions(events);
    }

    @Test
    void includesOriginInPayloadWhenPresent() {
        when(sources.findById("ds1")).thenReturn(Optional.of(row("ds1", "p1")));

        listener.onRuntimeActivity(new RuntimeActivityEvent(
                "ds1", "SOURCE_STALE", Instant.ofEpochSecond(8), "no health response",
                HealthOrigin.SIMULATOR));

        verify(events).append(eq("p1"), eq("ds1"), isNull(), eq("SOURCE_STALE"),
                eq(Instant.ofEpochSecond(8).atOffset(ZoneOffset.UTC)),
                eq("{\"detail\":\"no health response\",\"origin\":\"SIMULATOR\"}"));
    }
}
