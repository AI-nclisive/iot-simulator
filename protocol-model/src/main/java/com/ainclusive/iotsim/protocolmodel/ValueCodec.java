package com.ainclusive.iotsim.protocolmodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact, protocol-neutral encoding of a value into a (kind, bytes) pair for the
 * value timeline and IPC. Numeric/boolean/text values are stored as UTF-8 text;
 * raw byte values are stored verbatim. See backend-specs/04_DB_SCHEMA.md.
 */
public final class ValueCodec {

    public enum Kind {
        NUM,
        INT,
        BOOL,
        TEXT,
        BYTES,
        /** Canonical recursive value tree for native structures, unions and arrays. */
        TREE
    }

    public record Encoded(Kind kind, byte[] bytes) {}

    private ValueCodec() {}

    public static Encoded encode(Object value) {
        if (value == null) {
            return new Encoded(Kind.TEXT, new byte[0]);
        }
        if (value instanceof Boolean b) {
            return new Encoded(Kind.BOOL, text(b.toString()));
        }
        if (value instanceof byte[] raw) {
            return new Encoded(Kind.BYTES, raw.clone());
        }
        if (value instanceof Float || value instanceof Double) {
            return new Encoded(Kind.NUM, text(value.toString()));
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return new Encoded(Kind.INT, text(value.toString()));
        }
        if (value instanceof Number) {
            return new Encoded(Kind.NUM, text(value.toString()));
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return new Encoded(Kind.TREE, encodeTree(value));
        }
        return new Encoded(Kind.TEXT, text(value.toString()));
    }

    /**
     * The {@link Kind} a value of the given neutral {@link DataType} encodes to.
     * Lets a caller that holds a node's schema type (rather than a live value)
     * decode timeline/IPC bytes — e.g. the supervisor decoding captured values
     * (IS-045). Consistent with {@link #encode} for every type.
     */
    public static Kind kindOf(DataType type) {
        return switch (type) {
            case BOOL -> Kind.BOOL;
            case STRING, LOCALIZED_TEXT, GUID, QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT ->
                    Kind.TEXT;
            case BYTES -> Kind.BYTES;
            case FLOAT32, FLOAT64 -> Kind.NUM;
            case INT8, UINT8, INT16, UINT16, INT32, UINT32, INT64, UINT64, DATETIME, STATUS_CODE ->
                    Kind.INT;
        };
    }

    public static Object decode(Kind kind, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (kind == Kind.BYTES) {
            return bytes;
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        return switch (kind) {
            case BOOL -> Boolean.valueOf(s);
            case INT -> Long.valueOf(s);
            case NUM -> Double.valueOf(s);
            case TEXT -> s;
            case BYTES -> bytes; // unreachable; handled above
            case TREE -> decodeTree(bytes);
        };
    }

    private static final int NULL = 0;
    private static final int BOOLEAN = 1;
    private static final int INTEGER = 2;
    private static final int NUMBER = 3;
    private static final int STRING = 4;
    private static final int RAW_BYTES = 5;
    private static final int ARRAY = 6;
    private static final int OBJECT = 7;

    private static byte[] encodeTree(Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeTree(out, value);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to encode native value tree", e);
        }
    }

    private static void writeTree(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte(NULL);
        } else if (value instanceof Boolean b) {
            out.writeByte(BOOLEAN);
            out.writeBoolean(b);
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            out.writeByte(INTEGER);
            out.writeLong(((Number) value).longValue());
        } else if (value instanceof Number n) {
            out.writeByte(NUMBER);
            out.writeDouble(n.doubleValue());
        } else if (value instanceof String s) {
            out.writeByte(STRING);
            writeBytes(out, text(s));
        } else if (value instanceof byte[] raw) {
            out.writeByte(RAW_BYTES);
            writeBytes(out, raw);
        } else if (value instanceof List<?> list) {
            out.writeByte(ARRAY);
            out.writeInt(list.size());
            for (Object item : list) {
                writeTree(out, item);
            }
        } else if (value instanceof Map<?, ?> map) {
            out.writeByte(OBJECT);
            List<Map.Entry<String, Object>> fields = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("native value tree keys must be strings");
                }
                fields.add(Map.entry(key, entry.getValue()));
            }
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            out.writeInt(fields.size());
            for (Map.Entry<String, Object> field : fields) {
                writeBytes(out, text(field.getKey()));
                writeTree(out, field.getValue());
            }
        } else {
            throw new IllegalArgumentException("unsupported native value tree member: " + value.getClass().getName());
        }
    }

    private static Object decodeTree(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Object result = readTree(in);
            if (in.available() != 0) {
                throw new IllegalArgumentException("trailing native value tree bytes");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid native value tree", e);
        }
    }

    private static Object readTree(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case NULL -> null;
            case BOOLEAN -> in.readBoolean();
            case INTEGER -> in.readLong();
            case NUMBER -> in.readDouble();
            case STRING -> new String(readBytes(in), StandardCharsets.UTF_8);
            case RAW_BYTES -> readBytes(in);
            case ARRAY -> {
                int count = in.readInt();
                if (count < 0) {
                    throw new IllegalArgumentException("negative native value array size");
                }
                List<Object> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    values.add(readTree(in));
                }
                yield values;
            }
            case OBJECT -> {
                int count = in.readInt();
                if (count < 0) {
                    throw new IllegalArgumentException("negative native value object size");
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    fields.put(new String(readBytes(in), StandardCharsets.UTF_8), readTree(in));
                }
                yield fields;
            }
            default -> throw new IllegalArgumentException("unknown native value tree tag");
        };
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("negative native value byte length");
        }
        return in.readNBytes(length);
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
