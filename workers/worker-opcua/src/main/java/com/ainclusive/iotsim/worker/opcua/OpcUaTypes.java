package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

/**
 * Maps the protocol-neutral data types onto OPC UA built-in types and converts
 * decoded values to the matching Milo Java representation.
 * See backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md §5.
 */
final class OpcUaTypes {

    private OpcUaTypes() {}

    /** The 21 concrete OPC UA built-in types this worker maps 1:1 to a neutral type. */
    private static final Map<NodeId, String> BUILTIN_TYPES = Map.ofEntries(
            Map.entry(Identifiers.Boolean, "BOOL"),
            Map.entry(Identifiers.SByte, "INT8"),
            Map.entry(Identifiers.Byte, "UINT8"),
            Map.entry(Identifiers.Int16, "INT16"),
            Map.entry(Identifiers.UInt16, "UINT16"),
            Map.entry(Identifiers.Int32, "INT32"),
            Map.entry(Identifiers.UInt32, "UINT32"),
            Map.entry(Identifiers.Int64, "INT64"),
            Map.entry(Identifiers.UInt64, "UINT64"),
            Map.entry(Identifiers.Float, "FLOAT32"),
            Map.entry(Identifiers.Double, "FLOAT64"),
            Map.entry(Identifiers.String, "STRING"),
            Map.entry(Identifiers.ByteString, "BYTES"),
            Map.entry(Identifiers.DateTime, "DATETIME"),
            Map.entry(Identifiers.LocalizedText, "LOCALIZED_TEXT"),
            Map.entry(Identifiers.Guid, "GUID"),
            Map.entry(Identifiers.StatusCode, "STATUS_CODE"),
            Map.entry(Identifiers.QualifiedName, "QUALIFIED_NAME"),
            Map.entry(Identifiers.NodeId, "NODE_ID"),
            Map.entry(Identifiers.ExpandedNodeId, "EXPANDED_NODE_ID"),
            Map.entry(Identifiers.XmlElement, "XML_ELEMENT"));

    /**
     * Reverse mapping used by scan/discovery: an OPC UA DataType node id back to a
     * protocol-neutral data type, or {@code null} when the declaration has a
     * distinct native NodeId. The caller preserves that NodeId exactly; a named
     * subtype must not be silently replaced by its primitive parent.
     */
    static String neutralTypeOf(NodeId dataTypeId) {
        if (dataTypeId == null) {
            return null;
        }
        return BUILTIN_TYPES.get(dataTypeId);
    }

    static NodeId dataTypeId(String dataType) {
        return switch (dataType) {
            case "BOOL" -> Identifiers.Boolean;
            case "INT8" -> Identifiers.SByte;
            case "UINT8" -> Identifiers.Byte;
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
            case "LOCALIZED_TEXT" -> Identifiers.LocalizedText;
            case "GUID" -> Identifiers.Guid;
            case "STATUS_CODE" -> Identifiers.StatusCode;
            case "QUALIFIED_NAME" -> Identifiers.QualifiedName;
            case "NODE_ID" -> Identifiers.NodeId;
            case "EXPANDED_NODE_ID" -> Identifiers.ExpandedNodeId;
            case "XML_ELEMENT" -> Identifiers.XmlElement;
            default -> Identifiers.String;
        };
    }

    static ValueCodec.Kind codecKind(String dataType) {
        if ("ABSTRACT".equals(dataType)) {
            return ValueCodec.Kind.TREE;
        }
        try {
            return ValueCodec.kindOf(DataType.valueOf(dataType));
        } catch (IllegalArgumentException unknownOrEmpty) {
            return ValueCodec.Kind.INT; // unknown/empty -> default (integers + DATETIME)
        }
    }

    /**
     * Captures a value read from a variable declared with an abstract OPC UA DataType
     * (BaseDataType or UInteger, IS-197): the concrete Milo type varies value-to-value, so the
     * neutral capture must carry its own type discriminator alongside the value — a canonical
     * TREE with {@code type} (the concrete neutral {@link DataType} name, or {@code null} for a
     * missing value) and {@code value} (that type's neutral value, converted via
     * {@link #fromOpcUaValue}).
     */
    static Map<String, Object> fromOpcUaVariant(Object raw) {
        Map<String, Object> tree = new HashMap<>();
        if (raw == null) {
            tree.put("type", null);
            tree.put("value", null);
            return tree;
        }
        String type = discriminatorOf(raw);
        tree.put("type", type);
        tree.put("value", fromOpcUaValue(type, raw));
        return tree;
    }

    /** The inverse of {@link #fromOpcUaVariant}: rebuilds the concrete Milo value from its
     * discriminated TREE for replay into an abstractly-typed variable. */
    static Object toOpcUaVariant(Object decoded) {
        if (!(decoded instanceof Map<?, ?> tree)) {
            throw new IllegalArgumentException("abstract OPC UA value must decode to a tree");
        }
        Object type = tree.get("type");
        if (type == null) {
            return null;
        }
        return toOpcUaValue((String) type, tree.get("value"));
    }

    /** Maps a Milo Java runtime value to the neutral {@link DataType} name that describes it. */
    private static String discriminatorOf(Object value) {
        if (value instanceof Boolean) {
            return "BOOL";
        } else if (value instanceof Byte) {
            return "INT8";
        } else if (value instanceof UByte) {
            return "UINT8";
        } else if (value instanceof Short) {
            return "INT16";
        } else if (value instanceof UShort) {
            return "UINT16";
        } else if (value instanceof Integer) {
            return "INT32";
        } else if (value instanceof UInteger) {
            return "UINT32";
        } else if (value instanceof Long) {
            return "INT64";
        } else if (value instanceof ULong) {
            return "UINT64";
        } else if (value instanceof Float) {
            return "FLOAT32";
        } else if (value instanceof Double) {
            return "FLOAT64";
        } else if (value instanceof String) {
            return "STRING";
        } else if (value instanceof ByteString) {
            return "BYTES";
        } else if (value instanceof DateTime) {
            return "DATETIME";
        } else if (value instanceof LocalizedText) {
            return "LOCALIZED_TEXT";
        } else if (value instanceof UUID) {
            return "GUID";
        } else if (value instanceof StatusCode) {
            return "STATUS_CODE";
        } else if (value instanceof QualifiedName) {
            return "QUALIFIED_NAME";
        } else if (value instanceof NodeId) {
            return "NODE_ID";
        } else if (value instanceof ExpandedNodeId) {
            return "EXPANDED_NODE_ID";
        } else if (value instanceof XmlElement) {
            return "XML_ELEMENT";
        }
        throw new IllegalArgumentException(
                "unsupported concrete type for abstract OPC UA value: " + value.getClass().getName());
    }

    static Object defaultValue(String dataType) {
        return switch (dataType) {
            case "BOOL" -> Boolean.FALSE;
            case "INT8" -> (byte) 0;
            case "UINT8" -> UByte.valueOf(0);
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
            case "LOCALIZED_TEXT" -> LocalizedText.english("");
            case "GUID" -> new UUID(0L, 0L);
            case "STATUS_CODE" -> StatusCode.GOOD;
            case "QUALIFIED_NAME" -> new QualifiedName(0, "");
            case "NODE_ID" -> NodeId.NULL_VALUE;
            case "EXPANDED_NODE_ID" -> ExpandedNodeId.NULL_VALUE;
            case "XML_ELEMENT" -> new XmlElement("");
            default -> "";
        };
    }

    /**
     * Converts a value read from a real OPC UA source (Milo Java type) into the
     * protocol-neutral Java value that {@link ValueCodec#encode} expects for the
     * node's data type — the reverse of {@link #toOpcUaValue}, used by live capture
     * (IS-045). Unsigned/integer types collapse to {@code Long}, floats to
     * {@code Double}, {@code DateTime} to epoch millis, so the encoded
     * {@link ValueCodec.Kind} matches {@link #codecKind}. Returns {@code null} for a
     * null/absent value (recorded as a missing value, never coerced to a wrong type).
     */
    static Object fromOpcUaValue(String dataType, Object value) {
        if (value == null) {
            return null;
        }
        return switch (codecKind(dataType)) {
            case BOOL -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
            case NUM -> ((Number) value).doubleValue();
            case INT -> value instanceof DateTime dt ? dt.getJavaTime()
                    : value instanceof StatusCode sc ? sc.getValue()
                    : ((Number) value).longValue();
            case BYTES -> value instanceof ByteString bs
                    ? (bs.bytes() == null ? new byte[0] : bs.bytes())
                    : (byte[]) value;
            case TEXT -> textOf(value);
            case TREE -> throw new IllegalArgumentException(
                    "TREE is only valid for native structured OPC UA values");
        };
    }

    /**
     * {@link ValueCodec.Kind#TEXT} values whose Milo {@code toString()} isn't
     * round-trippable get their canonical parseable form here instead
     * ({@code toOpcUaValue}'s matching cases parse it back).
     */
    private static String textOf(Object value) {
        if (value instanceof LocalizedText lt) {
            return lt.getText() == null ? "" : lt.getText();
        } else if (value instanceof QualifiedName qn) {
            return qn.toParseableString();
        } else if (value instanceof NodeId nodeId) {
            return nodeId.toParseableString();
        } else if (value instanceof ExpandedNodeId expandedNodeId) {
            return expandedNodeId.toParseableString();
        } else if (value instanceof XmlElement xml) {
            return xml.getFragmentOrEmpty();
        }
        return value.toString();
    }


    /** Coerces a value decoded by {@link ValueCodec} to the OPC UA Java type. */
    static Object toOpcUaValue(String dataType, Object decoded) {
        if (decoded == null) {
            return defaultValue(dataType);
        }
        return switch (dataType) {
            case "BOOL" -> (Boolean) decoded;
            case "INT8" -> ((Number) decoded).byteValue();
            case "UINT8" -> UByte.valueOf(((Number) decoded).shortValue());
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
            case "LOCALIZED_TEXT" -> LocalizedText.english(decoded.toString());
            case "GUID" -> UUID.fromString(decoded.toString());
            case "STATUS_CODE" -> new StatusCode(((Number) decoded).longValue());
            case "QUALIFIED_NAME" -> QualifiedName.parse(decoded.toString());
            case "NODE_ID" -> NodeId.parse(decoded.toString());
            case "EXPANDED_NODE_ID" -> ExpandedNodeId.parse(decoded.toString());
            case "XML_ELEMENT" -> new XmlElement(decoded.toString());
            default -> decoded.toString();
        };
    }
}
