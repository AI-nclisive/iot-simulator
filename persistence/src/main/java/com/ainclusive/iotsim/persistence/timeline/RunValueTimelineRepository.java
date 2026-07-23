package com.ainclusive.iotsim.persistence.timeline;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;

/**
 * Value timeline for a run with no backing Recording (SYNTHETIC/SCENARIO): the values
 * a live/one-shot generation run produced, kept for evidence export (IS-185). Mirrors
 * {@link ValueTimelineRepository} but keyed by {@code runId}.
 */
public interface RunValueTimelineRepository {

    /** Appends a batch of values for a run. Returns the number written. */
    long append(String runId, List<NeutralValue> values);

    /** All values for a run, time-ordered. */
    List<NeutralValue> readAll(String runId);

    /** Deletes every value row for a run (run/evidence retention). */
    void deleteByRun(String runId);
}
