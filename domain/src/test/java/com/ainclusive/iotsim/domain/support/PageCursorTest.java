package com.ainclusive.iotsim.domain.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PageCursorTest {

    @Test
    void encodeDecodeRoundTrip() {
        OffsetDateTime at = OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 123_456_000, ZoneOffset.UTC);
        String cursor = PageCursor.encode(at, "proj-abc-123");
        PageCursor.Parts parts = PageCursor.decode(cursor);
        assertThat(parts.at()).isEqualTo(at.truncatedTo(ChronoUnit.MICROS));
        assertThat(parts.id()).isEqualTo("proj-abc-123");
    }

    @Test
    void encodeDecodePreservesIdWithColons() {
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        String idWithColon = "some:id:with:colons";
        String cursor = PageCursor.encode(at, idWithColon);
        assertThat(PageCursor.decode(cursor).id()).isEqualTo(idWithColon);
    }

    @Test
    void decodeNullReturnsNull() {
        assertThat(PageCursor.decode(null)).isNull();
    }

    @Test
    void decodeBlankReturnsNull() {
        assertThat(PageCursor.decode("   ")).isNull();
    }

    @Test
    void decodeMalformedBase64ThrowsIllegalArgument() {
        assertThatThrownBy(() -> PageCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeMissingSeparatorThrowsIllegalArgument() {
        String noColon = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1234567890".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PageCursor.decode(noColon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator");
    }

    @Test
    void decodeNonNumericTimestampThrowsIllegalArgument() {
        String bad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("notanumber:some-id".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> PageCursor.decode(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampNullReturnsDefault() {
        assertThat(PageCursor.clamp(null)).isEqualTo(PageCursor.DEFAULT_LIMIT);
    }

    @Test
    void clampZeroReturnsDefault() {
        assertThat(PageCursor.clamp(0)).isEqualTo(PageCursor.DEFAULT_LIMIT);
    }

    @Test
    void clampNegativeReturnsDefault() {
        assertThat(PageCursor.clamp(-5)).isEqualTo(PageCursor.DEFAULT_LIMIT);
    }

    @Test
    void clampInRangePassesThrough() {
        assertThat(PageCursor.clamp(1)).isEqualTo(1);
        assertThat(PageCursor.clamp(PageCursor.MAX_LIMIT)).isEqualTo(PageCursor.MAX_LIMIT);
    }

    @Test
    void clampAboveMaxCapsToMax() {
        assertThat(PageCursor.clamp(PageCursor.MAX_LIMIT + 1)).isEqualTo(PageCursor.MAX_LIMIT);
        assertThat(PageCursor.clamp(Integer.MAX_VALUE)).isEqualTo(PageCursor.MAX_LIMIT);
    }
}
