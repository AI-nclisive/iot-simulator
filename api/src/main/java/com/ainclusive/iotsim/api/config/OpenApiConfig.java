package com.ainclusive.iotsim.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata. Served at /v3/api-docs with Swagger UI at /swagger-ui. */
@Configuration
public class OpenApiConfig {

    /**
     * The API is grouped into nine Swagger tags, one per domain noun, declared here in workflow
     * order (platform → workspace → sources → data → authoring → execution → output → observation).
     * Controllers reference these names via {@code @Tag(name = ...)}; the descriptions live here so
     * each group has a single authoritative summary. See API_INVENTORY.md.
     */
    @Bean
    public OpenAPI iotSimulatorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT Data Source Simulator API")
                        .version("v1")
                        .description("Backend API for the IoT Data Source Simulator. "
                                + "See backend-specs/05_API_CONTRACT.md."))
                .tags(List.of(
                        new Tag().name("Platform")
                                .description("API nameplate and liveness/build probes — the versioned metadata"
                                        + " endpoint plus Spring Actuator health/info. No domain entity."),
                        new Tag().name("Projects")
                                .description("The workspace container itself: create, read, update, duplicate,"
                                        + " archive, and delete projects, plus dashboard overview counts and"
                                        + " whole-project ZIP import/export."),
                        new Tag().name("Data Sources")
                                .description("Define and control simulated data sources: CRUD, synthetic-backed"
                                        + " creation, endpoint discovery/scan, value schema, runtime stop, and"
                                        + " credentials."),
                        new Tag().name("Recordings")
                                .description("Captured timelines of real source values: CRUD, live capture"
                                        + " start/stop, captured schema and value browsing, and ZIP"
                                        + " import/export."),
                        new Tag().name("Samples")
                                .description("Reusable value samples used to seed source values and scenarios:"
                                        + " CRUD and ZIP import/export."),
                        new Tag().name("Scenarios")
                                .description("Authored scripts of value changes over time: create, read, update,"
                                        + " duplicate, validate, and run."),
                        new Tag().name("Runs")
                                .description("Executions of any kind: list and inspect runs, read live run state,"
                                        + " stop a run, view the active-runs dashboard, and start replay or"
                                        + " synthetic runs."),
                        new Tag().name("Evidence")
                                .description("Run output artifacts: list and read run evidence, trigger an export,"
                                        + " and download the bundle."),
                        new Tag().name("Monitoring")
                                .description("Read-only \"what's happening now\": source health, connected-client"
                                        + " observations, the historical runtime event log, and Server-Sent"
                                        + " Events (SSE) push streams.")));
    }
}
