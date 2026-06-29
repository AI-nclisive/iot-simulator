package com.ainclusive.iotsim.api.stream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** EventSink that records sent events; can simulate a write failure. */
final class RecordingSink implements EventSink {
    final List<LiveEvent> sent = new CopyOnWriteArrayList<>();
    volatile boolean completed;
    volatile java.io.IOException failWith;

    @Override
    public void send(LiveEvent event) throws java.io.IOException {
        if (failWith != null) {
            throw failWith;
        }
        sent.add(event);
    }

    @Override
    public void complete() {
        completed = true;
    }
}
