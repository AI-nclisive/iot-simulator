package com.ainclusive.iotsim.worker.opcua;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/** Canonical array conversion at the OPC UA boundary. Timeline values are TREE lists. */
final class OpcUaArrayValues {

    private OpcUaArrayValues() {}

    static List<Object> captureNeutral(String dataType, Object raw, List<Integer> dimensions) {
        return capture(raw, dimensions, value -> OpcUaTypes.fromOpcUaValue(dataType, value));
    }

    static List<Object> captureStructure(Object raw, NodeId expectedEncoding, List<Integer> dimensions) {
        return capture(raw, dimensions, value -> {
            if (!(value instanceof ExtensionObject extension)
                    || !(extension.getBody() instanceof org.eclipse.milo.opcua.stack.core.types.builtin.ByteString body)) {
                throw new IllegalArgumentException("capture expected binary ExtensionObject array elements");
            }
            if (!expectedEncoding.equals(extension.getEncodingOrTypeId())) {
                throw new IllegalArgumentException("capture received ExtensionObject array element with encoding "
                        + extension.getEncodingOrTypeId() + " but schema declares " + expectedEncoding);
            }
            return body.bytes();
        });
    }

    static Object replayNeutral(String dataType, Object canonical, List<Integer> dimensions) {
        List<?> values = canonicalValues(canonical);
        validateSize(values.size(), dimensions);
        List<Object> converted = values.stream().map(value -> OpcUaTypes.toOpcUaValue(dataType, value)).toList();
        return arrayOrMatrix(converted, dimensions);
    }

    static Object replayStructure(Object canonical, NodeId encoding, List<Integer> dimensions) {
        List<?> values = canonicalValues(canonical);
        validateSize(values.size(), dimensions);
        List<ExtensionObject> converted = values.stream().map(value -> {
            if (!(value instanceof byte[] body)) {
                throw new IllegalArgumentException("native structure array value must contain binary bodies");
            }
            return OpcUaProtocolService.structureValue(encoding, body);
        }).toList();
        return arrayOrMatrix(converted, dimensions);
    }

    private static List<Object> capture(Object raw, List<Integer> dimensions,
            java.util.function.Function<Object, Object> converter) {
        Object elements = raw instanceof Matrix matrix ? matrix.getElements() : raw;
        int[] actualDimensions = raw instanceof Matrix matrix ? matrix.getDimensions() : null;
        if (actualDimensions != null) {
            validateDimensions(actualDimensions, dimensions);
        }
        List<?> values = values(elements);
        validateSize(values.size(), dimensions);
        return values.stream().map(converter).toList();
    }

    private static List<?> values(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("capture expected an OPC UA array value, received null");
        }
        if (raw instanceof List<?> list) {
            return flatten(list);
        }
        if (!raw.getClass().isArray()) {
            throw new IllegalArgumentException("capture expected an OPC UA array value, received "
                    + raw.getClass().getName());
        }
        int length = Array.getLength(raw);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object value = Array.get(raw, i);
            if (value != null && value.getClass().isArray()) {
                values.addAll(flattenArray(value));
            } else {
                values.add(value);
            }
        }
        return values;
    }

    private static List<Object> flatten(List<?> values) {
        List<Object> flattened = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof List<?> nested) {
                flattened.addAll(flatten(nested));
            } else if (value != null && value.getClass().isArray()) {
                flattened.addAll(flattenArray(value));
            } else {
                flattened.add(value);
            }
        }
        return flattened;
    }

    /** TREE array leaves may themselves be byte[] (ByteString/ExtensionObject bodies). */
    private static List<Object> canonicalValues(Object canonical) {
        if (!(canonical instanceof List<?> list)) {
            throw new IllegalArgumentException("recorded OPC UA array value must be a value tree list");
        }
        return flattenCanonical(list);
    }

    private static List<Object> flattenCanonical(List<?> values) {
        List<Object> flattened = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof List<?> nested) {
                flattened.addAll(flattenCanonical(nested));
            } else {
                flattened.add(value);
            }
        }
        return flattened;
    }

    private static List<Object> flattenArray(Object values) {
        return new ArrayList<>(values(values));
    }

    private static Object arrayOrMatrix(List<?> values, List<Integer> dimensions) {
        Object[] array = typedArray(values);
        if (dimensions != null && dimensions.size() > 1) {
            return new Matrix(array, dimensions.stream().mapToInt(Integer::intValue).toArray());
        }
        return array;
    }

    private static Object[] typedArray(List<?> values) {
        Class<?> component = values.stream().filter(java.util.Objects::nonNull)
                .findFirst().map(Object::getClass).orElse(Object.class);
        Object[] array = (Object[]) Array.newInstance(component, values.size());
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static void validateDimensions(int[] actual, List<Integer> expected) {
        if (expected == null || expected.isEmpty()) {
            return;
        }
        if (actual.length != expected.size()) {
            throw new IllegalArgumentException("OPC UA array rank mismatch: expected " + expected.size()
                    + " dimensions but received " + actual.length);
        }
        for (int i = 0; i < actual.length; i++) {
            if (expected.get(i) > 0 && expected.get(i) != actual[i]) {
                throw new IllegalArgumentException("OPC UA array dimension " + i + " mismatch: expected "
                        + expected.get(i) + " but received " + actual[i]);
            }
        }
    }

    private static void validateSize(int size, List<Integer> dimensions) {
        if (dimensions == null || dimensions.isEmpty() || dimensions.stream().anyMatch(d -> d == null || d <= 0)) {
            return;
        }
        long expected = 1;
        for (int dimension : dimensions) {
            expected *= dimension;
        }
        if (expected != size) {
            throw new IllegalArgumentException("OPC UA array value shape mismatch: expected " + expected
                    + " elements but received " + size);
        }
    }
}
