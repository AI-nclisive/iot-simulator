package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.sample.SampleImportExportController;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleBundle;
import com.ainclusive.iotsim.domain.sample.SampleImportExportService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Unit tests for {@link SampleImportExportController} (IS-070). */
@WebMvcTest(controllers = SampleImportExportController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
class SampleImportExportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SampleImportExportService service;

    private static final String PROJECT = "p1";
    private static final String SAMPLE_ID = "smp-1";

    private static Sample sample() {
        return new Sample(SAMPLE_ID, PROJECT, "rec-1", "Baseline", "{}",
                List.of("env"), Instant.parse("2026-01-01T00:00:00Z"), "local", 0);
    }

    private static SampleBundle bundle() {
        byte[] zip = "PK fake-zip".getBytes(StandardCharsets.UTF_8);
        return new SampleBundle(new ByteArrayInputStream(zip), "application/zip",
                "sample-" + SAMPLE_ID + ".zip");
    }

    @Test
    void exportBuildsAndStreamsZip() throws Exception {
        given(service.export(PROJECT, SAMPLE_ID)).willReturn(bundle());

        mvc.perform(post("/api/v1/projects/" + PROJECT + "/samples/" + SAMPLE_ID + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"sample-" + SAMPLE_ID + ".zip\""));
    }

    @Test
    void exportReturns404ForMissingSample() throws Exception {
        given(service.export(PROJECT, "missing"))
                .willThrow(new ResourceNotFoundException("Sample", "missing"));

        mvc.perform(post("/api/v1/projects/" + PROJECT + "/samples/missing/export"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadServesStoredBundle() throws Exception {
        given(service.openBundle(PROJECT, SAMPLE_ID)).willReturn(Optional.of(bundle()));

        mvc.perform(get("/api/v1/projects/" + PROJECT + "/samples/" + SAMPLE_ID + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"sample-" + SAMPLE_ID + ".zip\""));
    }

    @Test
    void downloadReturns404WhenNotExported() throws Exception {
        given(service.openBundle(eq(PROJECT), eq(SAMPLE_ID))).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/projects/" + PROJECT + "/samples/" + SAMPLE_ID + "/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importCreates201WithSampleBody() throws Exception {
        given(service.importSample(eq(PROJECT), any(), eq("local"))).willReturn(sample());

        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.zip", "application/zip",
                "PK fake-zip".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mvc.perform(multipart(
                        "/api/v1/projects/" + PROJECT + "/samples/import").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"id\":\"" + SAMPLE_ID + "\"");
        assertThat(body).contains("\"name\":\"Baseline\"");
        assertThat(result.getResponse().getHeader("Location"))
                .endsWith("/api/v1/projects/" + PROJECT + "/samples/" + SAMPLE_ID);
    }

    @Test
    void importWithEmptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.zip", "application/zip", new byte[0]);

        mvc.perform(multipart("/api/v1/projects/" + PROJECT + "/samples/import").file(file))
                .andExpect(status().isBadRequest());
    }
}
