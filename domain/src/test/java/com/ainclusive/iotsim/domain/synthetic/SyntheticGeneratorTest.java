package com.ainclusive.iotsim.domain.synthetic;

import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT16;
import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Constant;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.RandomUniform;
import com.ainclusive.iotsim.protocolmodel.DeterminismContext;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntheticGeneratorTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static DeterminismContext context() {
        return new DeterministicSettings(42L, START).newContext();
    }

    private static List<NeutralValue> take(SyntheticGenerator gen, int n) {
        List<NeutralValue> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(gen.next());
        }
        return out;
    }

    @Test
    void emitsSamplesSpacedByUpdateRateStartingAtRunStart() {
        SyntheticGenerator gen = new SyntheticVariable("temp", FLOAT64, new Constant(7.0), 250).generator(context());
        List<NeutralValue> values = take(gen, 3);

        assertThat(values).extracting(NeutralValue::sourceTime)
                .containsExactly(START, START.plusMillis(250), START.plusMillis(500));
        assertThat(values).allSatisfy(v -> {
            assertThat(v.nodeId()).isEqualTo("temp");
            assertThat(v.quality()).isEqualTo(Quality.GOOD);
            assertThat(v.value()).isEqualTo(7.0);
        });
    }

    @Test
    void coercesValueToNodeDataType() {
        SyntheticGenerator gen = new SyntheticVariable("n", INT16, new Constant(2.6), 100).generator(context());
        assertThat(gen.next().value()).isEqualTo(3L);
    }

    @Test
    void sameSettingsReproduceTheSeries() {
        DeterministicSettings settings = new DeterministicSettings(99L, START);
        SyntheticVariable var = new SyntheticVariable("n", FLOAT64, new RandomUniform(0, 100), 100);

        List<NeutralValue> a = take(var.generator(settings.newContext()), 32);
        List<NeutralValue> b = take(var.generator(settings.newContext()), 32);

        assertThat(b).isEqualTo(a);
    }

    @Test
    void differentNodesGetIndependentStreamsWithinOneRun() {
        DeterminismContext ctx = context();
        SyntheticGenerator a = new SyntheticVariable("a", FLOAT64, new RandomUniform(0, 100), 100).generator(ctx);
        SyntheticGenerator b = new SyntheticVariable("b", FLOAT64, new RandomUniform(0, 100), 100).generator(ctx);

        assertThat(take(a, 16)).extracting(NeutralValue::value)
                .isNotEqualTo(take(b, 16).stream().map(NeutralValue::value).toList());
    }
}
