package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceGovernancePolicyTest {

    @Test
    void defaultCapIs50AndLimited() {
        assertThat(ResourceGovernancePolicy.DEFAULT.maxConcurrentWorkers()).isEqualTo(50);
        assertThat(ResourceGovernancePolicy.DEFAULT.isLimited()).isTrue();
    }

    @Test
    void positiveCapIsLimited() {
        assertThat(new ResourceGovernancePolicy(3).isLimited()).isTrue();
    }

    @Test
    void zeroOrNegativeCapMeansUnlimited() {
        assertThat(new ResourceGovernancePolicy(0).isLimited()).isFalse();
        assertThat(new ResourceGovernancePolicy(-5).isLimited()).isFalse();
    }
}
