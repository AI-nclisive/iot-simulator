package com.ainclusive.iotsim.domain.synthetic;

import static com.ainclusive.iotsim.protocolmodel.DataType.BYTES;
import static com.ainclusive.iotsim.protocolmodel.DataType.DATETIME;
import static com.ainclusive.iotsim.protocolmodel.DataType.EXPANDED_NODE_ID;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.GUID;
import static com.ainclusive.iotsim.protocolmodel.DataType.NODE_ID;
import static com.ainclusive.iotsim.protocolmodel.DataType.QUALIFIED_NAME;
import static com.ainclusive.iotsim.protocolmodel.DataType.STATUS_CODE;
import static com.ainclusive.iotsim.protocolmodel.DataType.XML_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Constant;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Ramp;
import java.time.Duration;
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
    void rejectsNonConstantPatternsForStructuralTypes() {
        Ramp dynamicPattern = new Ramp(0, 1, Duration.ofSeconds(1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SyntheticVariable("n", BYTES, dynamicPattern, 100))
                .withMessageContaining("BYTES");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new SyntheticVariable("n", DATETIME, dynamicPattern, 100))
                .withMessageContaining("DATETIME");
    }

    @Test
    void allowsConstantPatternForStructuralTypes() {
        // IS-168: a fixed value is the only sensible "signal" for structural/
        // identifier fields — a real device wouldn't vary a NodeId or GUID over
        // time either.
        assertThat(new SyntheticVariable("n", BYTES, new Constant(new byte[] {1, 2}), 100).dataType())
                .isEqualTo(BYTES);
        assertThat(new SyntheticVariable("n", DATETIME, new Constant(0L), 100).dataType())
                .isEqualTo(DATETIME);
        assertThat(new SyntheticVariable("n", GUID, new Constant("11111111-1111-1111-1111-111111111111"), 100)
                .dataType())
                .isEqualTo(GUID);
        assertThat(new SyntheticVariable("n", STATUS_CODE, new Constant(0L), 100).dataType())
                .isEqualTo(STATUS_CODE);
        assertThat(new SyntheticVariable("n", QUALIFIED_NAME, new Constant("2:Foo"), 100).dataType())
                .isEqualTo(QUALIFIED_NAME);
        assertThat(new SyntheticVariable("n", NODE_ID, new Constant("ns=2;s=Foo"), 100).dataType())
                .isEqualTo(NODE_ID);
        assertThat(new SyntheticVariable("n", EXPANDED_NODE_ID, new Constant("ns=2;s=Foo"), 100).dataType())
                .isEqualTo(EXPANDED_NODE_ID);
        assertThat(new SyntheticVariable("n", XML_ELEMENT, new Constant("<a/>"), 100).dataType())
                .isEqualTo(XML_ELEMENT);
    }

    @Test
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable(null, FLOAT64, PATTERN, 100));
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable("n", null, PATTERN, 100));
        assertThatNullPointerException().isThrownBy(() -> new SyntheticVariable("n", FLOAT64, null, 100));
    }
}
