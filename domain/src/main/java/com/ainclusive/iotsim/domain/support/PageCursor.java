package com.ainclusive.iotsim.domain.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/** Encodes/decodes keyset pagination cursors: {@code base64url(epochMicros + ":" + id)}. */
public final class PageCursor {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private PageCursor() {}

    public record Parts(OffsetDateTime at, String id) {}

    public static String encode(OffsetDateTime createdAt, String id) {
        long micros = toMicros(createdAt.toInstant());
        String raw = micros + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Parts decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            if (sep < 0) {
                throw new IllegalArgumentException("cursor missing separator");
            }
            long micros = Long.parseLong(raw.substring(0, sep));
            String id = raw.substring(sep + 1);
            Instant at = Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
            return new Parts(at.atOffset(ZoneOffset.UTC), id);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor", e);
        }
    }

    public static int clamp(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static long toMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000;
    }
}
