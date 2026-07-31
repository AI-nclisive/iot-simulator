package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.junit.jupiter.api.Test;

class OpcUaArrayValuesTest {

    @Test
    void roundTripsOneDimensionalPrimitiveArrayThroughTree() {
        List<Object> captured = OpcUaArrayValues.captureNeutral("INT32", new Integer[] {3, 5}, List.of(2));
        ValueCodec.Encoded encoded = ValueCodec.encode(captured);

        Object replay = OpcUaArrayValues.replayNeutral("INT32",
                ValueCodec.decode(encoded.kind(), encoded.bytes()), List.of(2));

        assertThat(replay).isInstanceOf(Integer[].class);
        assertThat((Integer[]) replay).containsExactly(3, 5);
    }

    @Test
    void roundTripsMatrixWithItsDeclaredDimensions() {
        Matrix source = new Matrix(new Double[] {1.0, 2.0, 3.0, 4.0}, new int[] {2, 2});
        Object replay = OpcUaArrayValues.replayNeutral("FLOAT64",
                OpcUaArrayValues.captureNeutral("FLOAT64", source, List.of(2, 2)), List.of(2, 2));

        assertThat(replay).isInstanceOf(Matrix.class);
        Matrix matrix = (Matrix) replay;
        assertThat(matrix.getDimensions()).containsExactly(2, 2);
        assertThat((Double[]) matrix.getElements()).containsExactly(1.0, 2.0, 3.0, 4.0);
    }

    @Test
    void rejectsValuesThatDoNotMatchDeclaredDimensions() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                OpcUaArrayValues.captureNeutral("INT32", new Integer[] {1, 2}, List.of(3)))
                .withMessageContaining("shape mismatch");
    }
}
