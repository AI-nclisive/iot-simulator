package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuntimeActivityEventTest {

    @Test
    void keepsFieldsIncludingNullDetail() {
        RuntimeActivityEvent event =
                new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null);

        assertThat(event.dataSourceId()).isEqualTo("ds1");
        assertThat(event.type()).isEqualTo("SOURCE_START");
        assertThat(event.at()).isEqualTo(Instant.ofEpochSecond(5));
        assertThat(event.detail()).isNull();
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(
                        () -> new RuntimeActivityEvent("ds1", "  ", Instant.ofEpochSecond(5), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new RuntimeActivityEvent(null, "T", Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RuntimeActivityEvent("ds1", "T", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fourArgConstructorDefaultsOriginToNull() {
        RuntimeActivityEvent e =
                new RuntimeActivityEvent("ds1", "SOURCE_START", Instant.ofEpochSecond(5), null);
        assertThat(e.origin()).isNull();
    }

    @Test
    void carriesExplicitOrigin() {
        RuntimeActivityEvent e = new RuntimeActivityEvent(
                "ds1", "SOURCE_STALE", Instant.ofEpochSecond(5), "no health response",
                HealthOrigin.SIMULATOR);
        assertThat(e.origin()).isEqualTo(HealthOrigin.SIMULATOR);
    }
}
