package com.ainclusive.iotsim.domain.run;

import java.util.List;

/** Aggregated run state: the run's own state plus per-source runtime health (IS-089). */
public record RunState(String runState, List<SourceState> sources) {
}
