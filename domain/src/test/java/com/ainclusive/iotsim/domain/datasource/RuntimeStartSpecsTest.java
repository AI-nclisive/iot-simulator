package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RuntimeStartSpecsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void listenPortParsedFromRuntimeConfig() {
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":4840}", json)).isEqualTo(4840);
    }

    @Test
    void listenPortDefaultsToZeroWhenAbsentBlankOrInvalid() {
        assertThat(RuntimeStartSpecs.listenPort("{}", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort(null, json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("   ", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("not-json", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":0}", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":-5}", json)).isZero();
    }
}
