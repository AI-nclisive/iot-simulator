package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile.MeasurementProfile;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile.ProfileStats;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Derives a statistics-based {@link RecordingProfile} from a recording's captured values (IS-146):
 * reads the recording's value timeline, computes per-node statistics, and — for every numeric
 * measurement — suggests ranges for <em>each</em> pattern type plus a recommended default. This is
 * the "statistics-derived" tier: it turns a real capture into ready-to-tweak synthetic profiles.
 *
 * <p>Suggestions come straight from the stats: CONSTANT at the mean; RANDOM_UNIFORM/RAMP/SQUARE
 * over the observed min/max; RANDOM_WALK over min/max with a step volatility from the signal's
 * sample-to-sample variation; SINE centered at the mean with amplitude ≈ stddev·√2. Periodic
 * patterns use a fixed default period (the recording carries no reliable period). The recommended
 * default is CONSTANT for a flat signal, otherwise RANDOM_WALK. {@code updateRateMs} is the median
 * observed inter-sample gap. Non-numeric and never-observed nodes are skipped.
 */
@Service
public class RecordingProfiler {

    /** Default period for periodic suggestions; the recording carries no reliable period. */
    private static final long DEFAULT_PERIOD_MS = 10_000L;

    private static final TypeReference<List<SchemaNode>> SCHEMA_NODE_LIST = new TypeReference<>() {};

    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final ObjectMapper json;

    public RecordingProfiler(RecordingRepository recordings, ValueTimelineRepository timeline,
            ObjectMapper json) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.json = json;
    }

    public RecordingProfile deriveProfile(String projectId, String recordingId) {
        RecordingRow rec = recordings.findById(recordingId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Recording", recordingId));
        // Read the recording's own stored schema snapshot (IS-161) rather than a live
        // lookup against dataSourceId, which may no longer resolve to an existing source.
        List<SchemaNode> nodes = readSchemaNodes(rec.schemaNodesJson());
        if (nodes.isEmpty()) {
            throw new ResourceNotFoundException("Schema",
                    (rec.dataSourceId() != null ? rec.dataSourceId() : recordingId) + "@v" + rec.schemaVersion());
        }

        // Group the timeline by node, preserving time order (readAll is time-ordered).
        List<NeutralValue> all = timeline.readAll(recordingId);
        Map<String, List<NeutralValue>> byNode = new LinkedHashMap<>();
        for (NeutralValue v : all) {
            byNode.computeIfAbsent(v.nodeId(), k -> new ArrayList<>()).add(v);
        }

        List<MeasurementProfile> measurements = new ArrayList<>();
        for (SchemaNode node : nodes) {
            if (node.kind() != NodeKind.VARIABLE) {
                continue;
            }
            List<NeutralValue> series = byNode.getOrDefault(node.nodeId(), List.of());
            List<Double> numbers = new ArrayList<>();
            for (NeutralValue v : series) {
                if (v.value() instanceof Number n) {
                    numbers.add(n.doubleValue());
                }
            }
            if (numbers.isEmpty()) {
                continue; // non-numeric or never observed — nothing to profile
            }
            measurements.add(profile(node.nodeId(), node.dataType().name(), medianDeltaMs(series), numbers));
        }
        return new RecordingProfile(measurements);
    }

    private List<SchemaNode> readSchemaNodes(String schemaNodesJson) {
        if (schemaNodesJson == null || schemaNodesJson.isBlank()) {
            return List.of();
        }
        return json.readValue(schemaNodesJson, SCHEMA_NODE_LIST);
    }

    /** Build the stats + per-pattern suggestions for one numeric series. */
    private static MeasurementProfile profile(String nodeId, String dataType, long rate, List<Double> xs) {
        double min = xs.get(0);
        double max = xs.get(0);
        double sum = 0;
        for (double x : xs) {
            min = Math.min(min, x);
            max = Math.max(max, x);
            sum += x;
        }
        double mean = sum / xs.size();
        double range = max - min;
        double stdDev = stdDev(xs, mean);
        boolean flat = range == 0 || stdDev < 1e-9;

        double volatility = Math.max(stepStdDev(xs), range * 0.01);
        double amplitude = stdDev * Math.sqrt(2.0);

        Map<String, PatternSpec> suggestions = new LinkedHashMap<>();
        suggestions.put("CONSTANT",
                new PatternSpec("CONSTANT", round(mean), null, null, null, null, null, null));
        suggestions.put("RANDOM_UNIFORM",
                new PatternSpec("RANDOM_UNIFORM", null, round(min), round(max), null, null, null, null));
        suggestions.put("RANDOM_WALK",
                new PatternSpec("RANDOM_WALK", null, round(min), round(max), null, round(volatility), null, null));
        suggestions.put("SINE",
                new PatternSpec("SINE", null, round(mean - amplitude), round(mean + amplitude),
                        DEFAULT_PERIOD_MS, null, null, null));
        suggestions.put("RAMP",
                new PatternSpec("RAMP", null, round(min), round(max), DEFAULT_PERIOD_MS, null, null, null));
        suggestions.put("SQUARE",
                new PatternSpec("SQUARE", null, round(min), round(max), DEFAULT_PERIOD_MS, null, null, null));

        String recommended = flat ? "CONSTANT" : "RANDOM_WALK";
        ProfileStats stats = new ProfileStats(xs.size(), round(min), round(max), round(mean), round(stdDev));
        return new MeasurementProfile(nodeId, dataType, rate, stats, suggestions, recommended);
    }

    private static double stdDev(List<Double> xs, double mean) {
        if (xs.size() < 2) {
            return 0;
        }
        double acc = 0;
        for (double x : xs) {
            double d = x - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / (xs.size() - 1));
    }

    /** Standard deviation of sample-to-sample differences — a random-walk step-size estimate. */
    private static double stepStdDev(List<Double> xs) {
        if (xs.size() < 2) {
            return 0;
        }
        List<Double> deltas = new ArrayList<>(xs.size() - 1);
        for (int i = 1; i < xs.size(); i++) {
            deltas.add(xs.get(i) - xs.get(i - 1));
        }
        double sum = 0;
        for (double d : deltas) {
            sum += d;
        }
        return stdDev(deltas, sum / deltas.size());
    }

    /** Median gap between consecutive samples, in ms; defaults to 1000 when it can't be derived. */
    private static long medianDeltaMs(List<NeutralValue> series) {
        if (series.size() < 2) {
            return 1000;
        }
        List<Long> gaps = new ArrayList<>(series.size() - 1);
        for (int i = 1; i < series.size(); i++) {
            Instant a = series.get(i - 1).sourceTime();
            Instant b = series.get(i).sourceTime();
            long ms = b.toEpochMilli() - a.toEpochMilli();
            if (ms > 0) {
                gaps.add(ms);
            }
        }
        if (gaps.isEmpty()) {
            return 1000;
        }
        gaps.sort(Long::compareTo);
        return Math.max(1, gaps.get(gaps.size() / 2));
    }

    /** Round to 4 decimals to keep the derived config tidy without losing meaningful precision. */
    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
