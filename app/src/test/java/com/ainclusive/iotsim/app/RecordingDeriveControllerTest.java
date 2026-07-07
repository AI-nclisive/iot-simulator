package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.recording.RecordingDeriveController;
import com.ainclusive.iotsim.domain.synthetic.PatternSpec;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile.MeasurementProfile;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile.ProfileStats;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfiler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Controller-level test for {@link RecordingDeriveController} (IS-146) — mirrors
 * {@code SyntheticSourceControllerTest}: verifies the HTTP handler delegates to
 * {@link RecordingProfiler} and returns the derived profile as its response body. Authorization
 * ({@code @PreAuthorize(SOURCE_EDIT)}) is declarative and identical to the accepted
 * {@code SyntheticSourceController}; the repo tests security enforcement separately (web-layer
 * MockMvc tests exclude security by convention — see {@code ScenarioControllerMvcTest}).
 */
class RecordingDeriveControllerTest {

    private static final String PROJECT = "p1";
    private static final String RECORDING = "r1";

    private RecordingProfiler profiler;
    private RecordingDeriveController controller;

    @BeforeEach
    void setUp() {
        profiler = mock(RecordingProfiler.class);
        controller = new RecordingDeriveController(profiler);
    }

    @Test
    void deriveReturnsTheProfilerResultForTheGivenRecording() {
        RecordingProfile profile = new RecordingProfile(List.of(new MeasurementProfile(
                "temp",
                "FLOAT64",
                1000,
                new ProfileStats(7, 0.8, 89.4, 56.0, 33.9),
                Map.of("RANDOM_WALK", new PatternSpec("RANDOM_WALK", null, 0.8, 89.4, null, 35.5, null, null)),
                "RANDOM_WALK")));
        given(profiler.deriveProfile(PROJECT, RECORDING)).willReturn(profile);

        RecordingProfile result = controller.derive(PROJECT, RECORDING);

        assertThat(result).isSameAs(profile);
        assertThat(result.measurements()).singleElement().satisfies(m -> {
            assertThat(m.nodeId()).isEqualTo("temp");
            assertThat(m.recommended()).isEqualTo("RANDOM_WALK");
            assertThat(m.suggestions()).containsKey("RANDOM_WALK");
        });
    }
}
