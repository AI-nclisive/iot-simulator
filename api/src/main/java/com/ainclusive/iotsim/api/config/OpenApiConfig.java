package com.ainclusive.iotsim.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata. Served at /v3/api-docs with Swagger UI at /swagger-ui. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iotSimulatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("IoT Data Source Simulator API")
                .version("v1")
                .description("Backend API for the IoT Data Source Simulator. "
                        + "See backend-specs/05_API_CONTRACT.md."));
    }
}
