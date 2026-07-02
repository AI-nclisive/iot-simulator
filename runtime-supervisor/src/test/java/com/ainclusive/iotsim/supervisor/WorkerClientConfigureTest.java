package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import org.junit.jupiter.api.Test;

class WorkerClientConfigureTest {

    @Test
    void configureRequestCarriesBindAndAdvertisedOptions() {
        ConfigureRequest req = WorkerClient.buildConfigureRequest(
                Schema.newBuilder().build(), 4840, "0.0.0.0", "plant.local");
        assertThat(req.getListenPort()).isEqualTo(4840);
        assertThat(req.getOptionsMap()).containsEntry("bindAddress", "0.0.0.0");
        assertThat(req.getOptionsMap()).containsEntry("advertisedHost", "plant.local");
    }
}
