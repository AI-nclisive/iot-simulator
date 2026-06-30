package com.ainclusive.iotsim.app.runtime;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists supervisor runtime-activity events (IS-048) into {@code runtime_events}
 * (IS-049). Resolves the owning project from the data source, then appends an
 * append-only row. The supervisor calls this on an IPC delivery thread, so the
 * blocking lookup + insert is handed to {@code executor}.
 */
public final class PersistingRuntimeActivityListener implements RuntimeActivityListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingRuntimeActivityListener.class);
    private static final String EMPTY_PAYLOAD = "{}";

    private final DataSourceRepository dataSources;
    private final RuntimeEventRepository runtimeEvents;
    private final ObjectMapper json;
    private final Executor executor;

    public PersistingRuntimeActivityListener(DataSourceRepository dataSources,
            RuntimeEventRepository runtimeEvents, ObjectMapper json, Executor executor) {
        this.dataSources = dataSources;
        this.runtimeEvents = runtimeEvents;
        this.json = json;
        this.executor = executor;
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        executor.execute(() -> persist(event));
    }

    private void persist(RuntimeActivityEvent event) {
        try {
            Optional<String> projectId =
                    dataSources.findById(event.dataSourceId()).map(DataSourceRow::projectId);
            if (projectId.isEmpty()) {
                log.warn("dropping runtime event {} for unknown data source {}",
                        event.type(), event.dataSourceId());
                return;
            }
            runtimeEvents.append(projectId.get(), event.dataSourceId(), null, event.type(),
                    event.at().atOffset(ZoneOffset.UTC), payload(event));
        } catch (RuntimeException e) {
            log.warn("failed to persist runtime event {} for {}",
                    event.type(), event.dataSourceId(), e);
        }
    }

    private String payload(RuntimeActivityEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (event.detail() != null && !event.detail().isBlank()) {
            map.put("detail", event.detail());
        }
        if (event.origin() != null) {
            map.put("origin", event.origin().name());
        }
        if (map.isEmpty()) {
            return EMPTY_PAYLOAD;
        }
        try {
            return json.writeValueAsString(map);
        } catch (JacksonException e) {
            return EMPTY_PAYLOAD;
        }
    }
}
