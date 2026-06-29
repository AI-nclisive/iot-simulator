package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RuntimeStreamControllerTest {

    record Sub(StreamKey key, String lastEventId, List<LiveEvent> initial) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId) {
            return subscribe(key, lastEventId, List.of());
        }
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial) {
            calls.add(new Sub(key, lastEventId, initial));
            return new SseEmitter(0L);
        }
    }

    // Real RuntimeStateSnapshot with hand fakes (RuntimeController iface + ProjectSources lambda).
    private static RuntimeStreamController controller(RecordingSubscriptions subs) {
        com.ainclusive.iotsim.platform.runtime.RuntimeController rc =
                new com.ainclusive.iotsim.platform.runtime.RuntimeController() {
                    public String start(String id, com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec s) { return "RUNNING"; }
                    public String stop(String id) { return "STOPPED"; }
                    public String state(String id) { return "RUNNING"; }
                    public long applyValues(String id, List<com.ainclusive.iotsim.protocolmodel.NeutralValue> v) { return 0; }
                };
        ProjectSources sources = pid -> List.of("d1");
        return new RuntimeStreamController(subs, new RuntimeStateSnapshot(rc, sources));
    }

    @Test
    void freshConnectGetsRuntimeStateSnapshot() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        controller(subs).streamRuntime("p1", null);
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.key()).isEqualTo(StreamKey.runtime("p1"));
            assertThat(c.lastEventId()).isNull();
            assertThat(c.initial()).singleElement().satisfies(ev ->
                    assertThat(ev.type()).isEqualTo("runtime-state"));
        });
    }

    @Test
    void reconnectSkipsSnapshotAndKeepsLastEventId() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        controller(subs).streamRuntime("p1", "42");
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.lastEventId()).isEqualTo("42");
            assertThat(c.initial()).isEmpty();
        });
    }
}
