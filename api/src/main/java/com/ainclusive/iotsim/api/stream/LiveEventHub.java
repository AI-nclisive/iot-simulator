package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.ClientActivityListener;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bridges supervisor activity listeners (IS-047/IS-048) to the live streams.
 * Called on the IPC delivery thread, so each method only hands off to a dispatch
 * executor; project resolution and publishing happen off that thread. Positive
 * {@code dataSourceId -> projectId} results are cached (a source may be unknown
 * now but appear later, so misses are not cached).
 */
@Component
public final class LiveEventHub implements ClientActivityListener, RuntimeActivityListener {

    private final LiveEventPublisher publisher;
    private final DataSourceProjectResolver resolver;
    private final Executor dispatch;
    private final Map<String, String> projectByDataSource = new ConcurrentHashMap<>();

    @Autowired
    public LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver) {
        this(publisher, resolver, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-hub-dispatch");
            t.setDaemon(true);
            return t;
        }));
    }

    LiveEventHub(LiveEventPublisher publisher, DataSourceProjectResolver resolver,
            Executor dispatch) {
        this.publisher = publisher;
        this.resolver = resolver;
        this.dispatch = dispatch;
    }

    @Override
    public void onRuntimeActivity(RuntimeActivityEvent event) {
        dispatch.execute(() -> {
            String projectId = projectByDataSource.computeIfAbsent(
                    event.dataSourceId(), ds -> resolver.projectOf(ds).orElse(null));
            if (projectId == null) {
                return; // unknown source: drop (do not cache the miss)
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dataSourceId", event.dataSourceId());
            data.put("type", event.type());
            data.put("at", event.at().toString());
            data.put("detail", event.detail() == null ? "" : event.detail());
            publisher.publish(StreamKey.runtime(projectId), event.type(), data, event.at());
        });
    }

    @Override
    public void onClientActivity(ClientActivityEvent event) {
        dispatch.execute(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dataSourceId", event.dataSourceId());
            data.put("clientId", event.clientId());
            data.put("kind", event.kind().name());
            data.put("at", event.at().toString());
            publisher.publish(
                    StreamKey.clients(event.dataSourceId()), event.kind().name(), data, event.at());
        });
    }
}
