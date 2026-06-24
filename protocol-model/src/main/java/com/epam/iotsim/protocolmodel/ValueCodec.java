package com.epam.iotsim.protocolmodel;

import java.nio.charset.StandardCharsets;

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
        BYTES
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
        return new Encoded(Kind.TEXT, text(value.toString()));
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
        };
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
