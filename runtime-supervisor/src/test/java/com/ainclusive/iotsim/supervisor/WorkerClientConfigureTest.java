package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SecurityConfig;
import com.ainclusive.iotsim.workercontract.v1.UserCredential;
import org.junit.jupiter.api.Test;

class WorkerClientConfigureTest {

    @Test
    void configureRequestCarriesBindAndAdvertisedOptions() {
        ConfigureRequest req = WorkerClient.buildConfigureRequest(
                Schema.newBuilder().build(), 4840, "0.0.0.0", "plant.local",
                SecurityConfig.getDefaultInstance());
        assertThat(req.getListenPort()).isEqualTo(4840);
        assertThat(req.getOptionsMap()).containsEntry("bindAddress", "0.0.0.0");
        assertThat(req.getOptionsMap()).containsEntry("advertisedHost", "plant.local");
    }

    @Test
    void configureRequestCarriesSecurityConfig() {
        SecurityConfig sc = SecurityConfig.newBuilder()
                .setUsernameEnabled(true)
                .addUsers(UserCredential.newBuilder()
                        .setUsername("op").setPasswordHash("h"))
                .build();
        ConfigureRequest req = WorkerClient.buildConfigureRequest(
                Schema.newBuilder().build(), 4840, "0.0.0.0", "plant.local", sc);
        assertThat(req.getSecurityConfig().getUsernameEnabled()).isTrue();
        assertThat(req.getSecurityConfig().getUsers(0).getUsername()).isEqualTo("op");
    }
}
