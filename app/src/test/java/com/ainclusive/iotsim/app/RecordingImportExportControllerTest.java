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

import com.ainclusive.iotsim.api.recording.RecordingImportExportController;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingBundle;
import com.ainclusive.iotsim.domain.recording.RecordingImportExportService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Unit tests for {@link RecordingImportExportController} (IS-070). */
@WebMvcTest(controllers = RecordingImportExportController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
class RecordingImportExportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RecordingImportExportService service;

    private static final String PROJECT = "p1";
    private static final String REC_ID = "rec-1";

    private static Recording recording() {
        return new Recording(REC_ID, PROJECT, "ds-1", 1, "SCAN_RECORD", "SCHEMA_AND_DATA", null, 5, 0L,
                Instant.parse("2026-01-01T00:00:00Z"), "local", 0, null, false);
    }

    private static RecordingBundle bundle() {
        byte[] zip = "PK fake-zip".getBytes(StandardCharsets.UTF_8);
        return new RecordingBundle(new ByteArrayInputStream(zip), "application/zip",
                "recording-" + REC_ID + ".zip");
    }

    @Test
    void exportBuildsAndStreamsZip() throws Exception {
        given(service.export(PROJECT, REC_ID)).willReturn(bundle());

        mvc.perform(post("/api/v1/projects/" + PROJECT + "/recordings/" + REC_ID + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"recording-" + REC_ID + ".zip\""));
    }

    @Test
    void exportReturns404ForMissingRecording() throws Exception {
        given(service.export(PROJECT, "missing"))
                .willThrow(new ResourceNotFoundException("Recording", "missing"));

        mvc.perform(post("/api/v1/projects/" + PROJECT + "/recordings/missing/export"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadServesStoredBundle() throws Exception {
        given(service.openBundle(PROJECT, REC_ID)).willReturn(Optional.of(bundle()));

        mvc.perform(get("/api/v1/projects/" + PROJECT + "/recordings/" + REC_ID + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"recording-" + REC_ID + ".zip\""));
    }

    @Test
    void downloadReturns404WhenNotExported() throws Exception {
        given(service.openBundle(eq(PROJECT), eq(REC_ID))).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/projects/" + PROJECT + "/recordings/" + REC_ID + "/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importCreates201WithRecordingBody() throws Exception {
        given(service.importRecording(eq(PROJECT), any(), eq("local"))).willReturn(recording());

        MockMultipartFile file = new MockMultipartFile(
                "file", "recording.zip", "application/zip",
                "PK fake-zip".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mvc.perform(multipart(
                        "/api/v1/projects/" + PROJECT + "/recordings/import").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"id\":\"" + REC_ID + "\"");
        assertThat(body).contains("\"origin\":\"SCAN_RECORD\"");
        assertThat(result.getResponse().getHeader("Location"))
                .endsWith("/api/v1/projects/" + PROJECT + "/recordings/" + REC_ID);
    }

    @Test
    void importWithEmptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "recording.zip", "application/zip", new byte[0]);

        mvc.perform(multipart("/api/v1/projects/" + PROJECT + "/recordings/import").file(file))
                .andExpect(status().isBadRequest());
    }
}
