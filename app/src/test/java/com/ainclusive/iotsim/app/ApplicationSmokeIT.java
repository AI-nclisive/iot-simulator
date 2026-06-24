package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole application against a real Postgres and exercises it over HTTP:
 * Flyway migrate, jOOQ, controllers, springdoc. Skipped without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationSmokeIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Test
    void openApiDocIsServedAndDescribesProjects() throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(url("/openapi.json")).GET().build());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("/api/v1/projects");
        assertThat(resp.body()).contains("IoT Data Source Simulator API");
    }

    @Test
    void createAndListProjectEndToEnd() throws Exception {
        HttpResponse<String> created = send(HttpRequest.newBuilder(url("/api/v1/projects"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"E2E\",\"description\":\"smoke\"}"))
                .build());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("E2E");
        assertThat(created.headers().firstValue("ETag")).hasValue("\"0\"");

        HttpResponse<String> list = send(HttpRequest.newBuilder(url("/api/v1/projects")).GET().build());
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("E2E");
    }
}
