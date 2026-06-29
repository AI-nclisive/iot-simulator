package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveValueStoreTest {

    private static NeutralValue v(String node, int val) {
        return NeutralValue.good(node, Instant.EPOCH, val);
    }

    @Test
    void latestWinsPerNodeAndSnapshotReturnsAll() {
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(v("n1", 1), v("n2", 2)));
        store.record("d1", List.of(v("n1", 9))); // n1 updated

        assertThat(store.snapshot("d1"))
                .extracting(NeutralValue::nodeId, NeutralValue::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("n1", 9),
                        org.assertj.core.groups.Tuple.tuple("n2", 2));
    }

    @Test
    void drainChangedReturnsOnlyChangedThenClears() {
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(v("n1", 1), v("n2", 2)));

        assertThat(store.drainChanged("d1"))
                .extracting(NeutralValue::nodeId).containsExactlyInAnyOrder("n1", "n2");
        // nothing changed since the drain
        assertThat(store.drainChanged("d1")).isEmpty();
        assertThat(store.dirtySources()).doesNotContain("d1");

        store.record("d1", List.of(v("n2", 5)));
        assertThat(store.dirtySources()).contains("d1");
        assertThat(store.drainChanged("d1")).extracting(NeutralValue::nodeId).containsExactly("n2");
    }

    @Test
    void snapshotEmptyForUnknownSource() {
        assertThat(new LiveValueStore().snapshot("nope")).isEmpty();
    }
}
