package com.ainclusive.iotsim.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.supervisor.ResourceGovernancePolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePropertiesTest {

    @Test
    void governanceDefaultsWhenUnset() {
        RuntimeProperties props = new RuntimeProperties("supervisor", Map.of(), null, null);
        assertThat(props.governancePolicy().maxConcurrentWorkers())
                .isEqualTo(ResourceGovernancePolicy.DEFAULT.maxConcurrentWorkers());
    }

    @Test
    void governanceCapBinds() {
        RuntimeProperties props = new RuntimeProperties(
                "supervisor", Map.of(), null, new RuntimeProperties.Governance(8));
        assertThat(props.governancePolicy().maxConcurrentWorkers()).isEqualTo(8);
        assertThat(props.governancePolicy().isLimited()).isTrue();
    }

    @Test
    void nonPositiveCapMeansUnlimited() {
        RuntimeProperties props = new RuntimeProperties(
                "supervisor", Map.of(), null, new RuntimeProperties.Governance(0));
        assertThat(props.governancePolicy().isLimited()).isFalse();
    }
}
