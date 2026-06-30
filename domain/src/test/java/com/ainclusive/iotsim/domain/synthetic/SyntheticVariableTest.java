package com.ainclusive.iotsim.domain.synthetic;

import static com.ainclusive.iotsim.protocolmodel.DataType.BYTES;
import static com.ainclusive.iotsim.protocolmodel.DataType.DATETIME;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT64;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Constant;
import org.junit.jupiter.api.Test;

class SyntheticVariableTest {

    private static final Constant PATTERN = new Constant(1.0);

    @Test
    void rejectsNonPositiveUpdateRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SyntheticVariable("n", FLOAT64, PATTERN, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SyntheticVariable("n", FLOAT64, PATTERN, -10));
    }

    @Test
    void rejectsTypesNotGenerableInV1() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SyntheticVariable("n", BYTES, PATTERN, 100))
                .withMessageContaining("BYTES");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SyntheticVariable("n", DATETIME, PATTERN, 100))
                .withMessageContaining("DATETIME");
    }

    @Test
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable(null, FLOAT64, PATTERN, 100));
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable("n", null, PATTERN, 100));
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable("n", FLOAT64, null, 100));
    }
}
