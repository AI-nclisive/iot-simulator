package com.ainclusive.iotsim.domain.synthetic;

import java.util.List;
import java.util.Map;

/**
 * A statistics-derived profile of a recording (IS-146): one entry per numeric measurement, each
 * carrying the observed statistics plus a suggested {@link PatternSpec} for <em>every</em> pattern
 * type (ranges filled from the stats) and a {@code recommended} default. The synthetic-authoring
 * UI applies the recommended suggestion on prefill and swaps to another type's suggestion when the
 * user changes the pattern.
 */
public record RecordingProfile(List<MeasurementProfile> measurements) {

    /**
     * Profile of one measurement.
     *
     * @param suggestions ready-to-apply patterns keyed by pattern type (CONSTANT, RANDOM_UNIFORM,
     *                    RANDOM_WALK, SINE, RAMP, SQUARE)
     * @param recommended the pattern type the fit judges the best default
     */
    public record MeasurementProfile(
            String nodeId,
            String dataType,
            long updateRateMs,
            ProfileStats stats,
            Map<String, PatternSpec> suggestions,
            String recommended) {}

    /** Observed statistics for one measurement over the recording. */
    public record ProfileStats(long count, double min, double max, double mean, double stdDev) {}
}
