package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.util.Date;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

/**
 * Maps the protocol-neutral data types onto OPC UA built-in types and converts
 * decoded values to the matching Milo Java representation.
 * See backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md §5.
 */
final class OpcUaTypes {

    private OpcUaTypes() {}

    /**
     * Reverse mapping used by scan/discovery: an OPC UA built-in DataType node id
     * back to a protocol-neutral data type, or {@code null} when the type is
     * outside the neutral intersection (surfaced as "unknown" in scan results, per
     * backend-specs/01 §2). Only exact built-in matches map; subtypes/structs are
     * left unknown for the user to resolve (IS-044).
     */
    static String neutralTypeOf(NodeId dataTypeId) {
        if (dataTypeId == null) {
            return null;
        }
        if (Identifiers.Boolean.equals(dataTypeId)) {
            return "BOOL";
        } else if (Identifiers.Int16.equals(dataTypeId)) {
            return "INT16";
        } else if (Identifiers.UInt16.equals(dataTypeId)) {
            return "UINT16";
        } else if (Identifiers.Int32.equals(dataTypeId)) {
            return "INT32";
        } else if (Identifiers.UInt32.equals(dataTypeId)) {
            return "UINT32";
        } else if (Identifiers.Int64.equals(dataTypeId)) {
            return "INT64";
        } else if (Identifiers.UInt64.equals(dataTypeId)) {
            return "UINT64";
        } else if (Identifiers.Float.equals(dataTypeId)) {
            return "FLOAT32";
        } else if (Identifiers.Double.equals(dataTypeId)) {
            return "FLOAT64";
        } else if (Identifiers.String.equals(dataTypeId)) {
            return "STRING";
        } else if (Identifiers.ByteString.equals(dataTypeId)) {
            return "BYTES";
        } else if (Identifiers.DateTime.equals(dataTypeId)) {
            return "DATETIME";
        }
        return null;
    }

    static NodeId dataTypeId(String dataType) {
        return switch (dataType) {
            case "BOOL" -> Identifiers.Boolean;
            case "INT16" -> Identifiers.Int16;
            case "UINT16" -> Identifiers.UInt16;
            case "INT32" -> Identifiers.Int32;
            case "UINT32" -> Identifiers.UInt32;
            case "INT64" -> Identifiers.Int64;
            case "UINT64" -> Identifiers.UInt64;
            case "FLOAT32" -> Identifiers.Float;
            case "FLOAT64" -> Identifiers.Double;
            case "STRING" -> Identifiers.String;
            case "BYTES" -> Identifiers.ByteString;
            case "DATETIME" -> Identifiers.DateTime;
            default -> Identifiers.String;
        };
    }

    static ValueCodec.Kind codecKind(String dataType) {
        return switch (dataType) {
            case "BOOL" -> ValueCodec.Kind.BOOL;
            case "STRING" -> ValueCodec.Kind.TEXT;
            case "BYTES" -> ValueCodec.Kind.BYTES;
            case "FLOAT32", "FLOAT64" -> ValueCodec.Kind.NUM;
            default -> ValueCodec.Kind.INT; // integers + DATETIME (epoch millis)
        };
    }

    static Object defaultValue(String dataType) {
        return switch (dataType) {
            case "BOOL" -> Boolean.FALSE;
            case "INT16" -> (short) 0;
            case "UINT16" -> Unsigned.ushort(0);
            case "INT32" -> 0;
            case "UINT32" -> Unsigned.uint(0L);
            case "INT64" -> 0L;
            case "UINT64" -> Unsigned.ulong(0L);
            case "FLOAT32" -> 0.0f;
            case "FLOAT64" -> 0.0d;
            case "STRING" -> "";
            case "BYTES" -> ByteString.of(new byte[0]);
            case "DATETIME" -> new DateTime();
            default -> "";
        };
    }

    /** Coerces a value decoded by {@link ValueCodec} to the OPC UA Java type. */
    static Object toOpcUaValue(String dataType, Object decoded) {
        if (decoded == null) {
            return defaultValue(dataType);
        }
        return switch (dataType) {
            case "BOOL" -> (Boolean) decoded;
            case "INT16" -> ((Number) decoded).shortValue();
            case "UINT16" -> Unsigned.ushort(((Number) decoded).intValue());
            case "INT32" -> ((Number) decoded).intValue();
            case "UINT32" -> Unsigned.uint(((Number) decoded).longValue());
            case "INT64" -> ((Number) decoded).longValue();
            case "UINT64" -> Unsigned.ulong(((Number) decoded).longValue());
            case "FLOAT32" -> ((Number) decoded).floatValue();
            case "FLOAT64" -> ((Number) decoded).doubleValue();
            case "STRING" -> decoded.toString();
            case "BYTES" -> ByteString.of((byte[]) decoded);
            case "DATETIME" -> new DateTime(new Date(((Number) decoded).longValue()));
            default -> decoded.toString();
        };
    }
}
