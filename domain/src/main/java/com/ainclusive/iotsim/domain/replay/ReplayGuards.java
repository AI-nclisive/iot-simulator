package com.ainclusive.iotsim.domain.replay;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;

/** Shared replay-time compatibility checks (IS-160). */
final class ReplayGuards {

    private ReplayGuards() {
    }

    /**
     * A recording is scoped to the protocol type it was captured under (IS-160), not to the
     * specific data source instance — it can be replayed against any data source of the same
     * protocol type.
     */
    static void requireProtocolCompatible(RecordingRow recording, DataSourceRow source) {
        if (!recording.protocol().equals(source.protocol())) {
            throw new IllegalArgumentException(
                    "recording " + recording.id() + " was captured under protocol " + recording.protocol()
                    + " and cannot be replayed against a " + source.protocol() + " data source");
        }
    }
}
