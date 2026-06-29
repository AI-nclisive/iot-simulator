package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StreamValueTest {

    @Test
    void mapsAllFieldsFromNeutralValue() {
        StreamValue sv = StreamValue.from(
                new NeutralValue("n1", Instant.EPOCH, 42, Quality.GOOD, null));
        assertThat(sv.nodeId()).isEqualTo("n1");
        assertThat(sv.value()).isEqualTo(42);
        assertThat(sv.quality()).isEqualTo("GOOD");
        assertThat(sv.qualityReason()).isNull();
        assertThat(sv.sourceTime()).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    void allowsNullValueForMissing() {
        StreamValue sv = StreamValue.from(
                new NeutralValue("n2", Instant.EPOCH, null, Quality.BAD, "COMM_FAILURE"));
        assertThat(sv.value()).isNull();
        assertThat(sv.quality()).isEqualTo("BAD");
        assertThat(sv.qualityReason()).isEqualTo("COMM_FAILURE");
    }
}
