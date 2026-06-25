package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RestartPolicyTest {

    @Test
    void backoffGrowsExponentiallyThenCaps() {
        RestartPolicy policy =
                new RestartPolicy(Duration.ofMillis(100), 2.0, Duration.ofMillis(500), 5);

        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofMillis(400));
        // 800ms would exceed the 500ms cap.
        assertThat(policy.backoffFor(4)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.backoffFor(5)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void firstAttemptRequired() {
        assertThatThrownBy(() -> RestartPolicy.DEFAULT.backoffFor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() ->
                new RestartPolicy(Duration.ofMillis(-1), 2.0, Duration.ofSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new RestartPolicy(Duration.ofMillis(100), 0.5, Duration.ofSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new RestartPolicy(Duration.ofSeconds(2), 2.0, Duration.ofSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new RestartPolicy(Duration.ofMillis(100), 2.0, Duration.ofSeconds(1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultPolicyIsSane() {
        assertThat(RestartPolicy.DEFAULT.maxRestarts()).isPositive();
        assertThat(RestartPolicy.DEFAULT.backoffFor(1))
                .isLessThanOrEqualTo(RestartPolicy.DEFAULT.maxBackoff());
    }
}
