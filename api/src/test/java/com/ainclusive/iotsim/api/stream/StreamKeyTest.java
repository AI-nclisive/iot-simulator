package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StreamKeyTest {

    @Test
    void factoriesBuildTypedKeys() {
        assertThat(StreamKey.runtime("p1"))
                .isEqualTo(new StreamKey(StreamKey.Type.RUNTIME, "p1"));
        assertThat(StreamKey.clients("d9"))
                .isEqualTo(new StreamKey(StreamKey.Type.CLIENTS, "d9"));
    }

    @Test
    void keysWithSameTypeAndScopeAreEqualForMapUse() {
        assertThat(StreamKey.runtime("p1")).hasSameHashCodeAs(StreamKey.runtime("p1"));
        assertThat(StreamKey.runtime("p1")).isNotEqualTo(StreamKey.clients("p1"));
    }

    @Test
    void liveEventTracksWhetherItCarriesASeq() {
        assertThat(new LiveEvent(7L, "X", null, Instant.EPOCH).hasSeq()).isTrue();
        assertThat(new LiveEvent(LiveEvent.NO_SEQ, "heartbeat", null, Instant.EPOCH).hasSeq())
                .isFalse();
    }

    @Test
    void valuesFactoryBuildsTypedKey() {
        assertThat(StreamKey.values("d1"))
                .isEqualTo(new StreamKey(StreamKey.Type.VALUES, "d1"));
        assertThat(StreamKey.values("d1")).isNotEqualTo(StreamKey.clients("d1"));
    }
}
