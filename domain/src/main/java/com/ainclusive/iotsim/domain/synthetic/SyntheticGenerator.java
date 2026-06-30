package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.DeterminismContext;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Emits one variable's deterministic synthetic series as {@link NeutralValue}s.
 *
 * <p>Built from a {@link SyntheticVariable} and a run's {@link DeterminismContext}
 * (IS-063): the run-start instant comes from the context's clock and the per-node
 * RNG stream from its seeded RNG, so the same settings yield an identical series.
 * Each {@link #next()} advances one tick — sample <em>n</em> has
 * {@code sourceTime = start + n·updateRateMs}. Not thread-safe; one generator drives
 * one node within one run.
 */
public final class SyntheticGenerator {

    private final String nodeId;
    private final DataType dataType;
    private final long updateRateMs;
    private final Instant start;
    private final SyntheticPattern.Signal signal;

    private long tick;

    SyntheticGenerator(SyntheticVariable variable, DeterminismContext context) {
        Objects.requireNonNull(context, "context");
        this.nodeId = variable.nodeId();
        this.dataType = variable.dataType();
        this.updateRateMs = variable.updateRateMs();
        this.start = context.clock().instant();
        this.signal = variable.pattern().bind(context.rng().stream(nodeId));
    }

    /** Produces the next sample and advances the series by one tick. */
    public NeutralValue next() {
        Duration elapsed = Duration.ofMillis(Math.multiplyExact(tick, updateRateMs));
        Object value = SyntheticValueCoercion.coerce(signal.valueAt(tick, elapsed), dataType);
        Instant sourceTime = start.plus(elapsed);
        tick++;
        return NeutralValue.good(nodeId, sourceTime, value);
    }
}
