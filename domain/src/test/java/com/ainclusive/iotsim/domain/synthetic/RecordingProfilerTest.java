package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordingProfilerTest {

    private static final String PROJECT = "p1";
    private static final String RECORDING = "r1";
    private static final String SOURCE = "ds1";
    private static final Instant T0 = Instant.parse("2026-07-06T22:00:00Z");

    private RecordingRepository recordings;
    private SchemaRepository schemas;
    private ValueTimelineRepository timeline;
    private RecordingProfiler profiler;

    @BeforeEach
    void setUp() {
        recordings = mock(RecordingRepository.class);
        schemas = mock(SchemaRepository.class);
        timeline = mock(ValueTimelineRepository.class);
        profiler = new RecordingProfiler(recordings, schemas, timeline);
    }

    private static SchemaNode variable(String nodeId) {
        return new SchemaNode(nodeId, null, "/" + nodeId, nodeId,
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, null, null);
    }

    private void givenRecordingWithNodes(SchemaNode... nodes) {
        OffsetDateTime now = OffsetDateTime.ofInstant(T0, ZoneOffset.UTC);
        given(recordings.findById(RECORDING)).willReturn(Optional.of(new RecordingRow(
                RECORDING, PROJECT, SOURCE, 1, "SCAN_RECORD", "SCHEMA_AND_DATA", null,
                now, now, 0, 0, now, now, "local", 1)));
        given(schemas.findByVersion(SOURCE, 1)).willReturn(Optional.of(
                new SchemaWithNodes("sc1", SOURCE, 1, now, List.of(nodes))));
    }

    private static NeutralValue at(String nodeId, long secondsAfterT0, double value) {
        return NeutralValue.good(nodeId, T0.plusSeconds(secondsAfterT0), value);
    }

    @Test
    void profilesVaryingSeriesWithStatsAndPerPatternSuggestions() {
        givenRecordingWithNodes(variable("temp"));
        given(timeline.readAll(RECORDING)).willReturn(List.of(
                at("temp", 0, 10.0), at("temp", 1, 12.0), at("temp", 2, 11.0), at("temp", 3, 15.0)));

        RecordingProfile profile = profiler.deriveProfile(PROJECT, RECORDING);

        assertThat(profile.measurements()).hasSize(1);
        var m = profile.measurements().get(0);
        assertThat(m.nodeId()).isEqualTo("temp");
        assertThat(m.dataType()).isEqualTo("FLOAT64");
        assertThat(m.updateRateMs()).isEqualTo(1000);
        assertThat(m.recommended()).isEqualTo("RANDOM_WALK");
        // Stats reflect the observed series.
        assertThat(m.stats().min()).isEqualTo(10.0);
        assertThat(m.stats().max()).isEqualTo(15.0);
        assertThat(m.stats().count()).isEqualTo(4);
        // Every pattern type has a suggestion, ranges drawn from the stats.
        assertThat(m.suggestions().keySet())
                .containsExactlyInAnyOrder("CONSTANT", "RANDOM_UNIFORM", "RANDOM_WALK", "SINE", "RAMP", "SQUARE");
        assertThat(m.suggestions().get("RANDOM_UNIFORM").min()).isEqualTo(10.0);
        assertThat(m.suggestions().get("RANDOM_UNIFORM").max()).isEqualTo(15.0);
        assertThat(m.suggestions().get("RANDOM_WALK").volatility()).isNotNull().isGreaterThan(0.0);
        assertThat(m.suggestions().get("SINE").periodMs()).isEqualTo(10_000L);
        assertThat(m.suggestions().get("CONSTANT").value()).isEqualTo(m.stats().mean());
    }

    @Test
    void recommendsConstantForFlatSeries() {
        givenRecordingWithNodes(variable("press"));
        given(timeline.readAll(RECORDING)).willReturn(List.of(
                at("press", 0, 5.0), at("press", 1, 5.0), at("press", 2, 5.0)));

        RecordingProfile profile = profiler.deriveProfile(PROJECT, RECORDING);

        var m = profile.measurements().get(0);
        assertThat(m.recommended()).isEqualTo("CONSTANT");
        assertThat(m.suggestions().get("CONSTANT").value()).isEqualTo(5.0);
    }

    @Test
    void skipsNodesWithNoObservedValues() {
        givenRecordingWithNodes(variable("temp"), variable("ghost"));
        given(timeline.readAll(RECORDING)).willReturn(List.of(
                at("temp", 0, 1.0), at("temp", 1, 2.0)));

        RecordingProfile profile = profiler.deriveProfile(PROJECT, RECORDING);

        assertThat(profile.measurements())
                .extracting(RecordingProfile.MeasurementProfile::nodeId).containsExactly("temp");
    }

    @Test
    void skipsFolderNodes() {
        SchemaNode folder = new SchemaNode("f1", null, "/Reactor", "Reactor",
                NodeKind.FOLDER, null, ValueRank.SCALAR, Access.READ, null, null);
        givenRecordingWithNodes(folder, variable("temp"));
        given(timeline.readAll(RECORDING)).willReturn(List.of(at("temp", 0, 1.0), at("temp", 1, 2.0)));

        RecordingProfile profile = profiler.deriveProfile(PROJECT, RECORDING);

        assertThat(profile.measurements()).hasSize(1);
        assertThat(profile.measurements().get(0).nodeId()).isEqualTo("temp");
    }

    @Test
    void unknownRecordingRejected() {
        given(recordings.findById("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> profiler.deriveProfile(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
