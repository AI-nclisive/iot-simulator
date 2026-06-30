package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.evidence.EvidenceController;
import com.ainclusive.iotsim.domain.evidence.EvidenceBundle;
import com.ainclusive.iotsim.domain.evidence.EvidenceFormat;
import com.ainclusive.iotsim.domain.evidence.EvidenceService;
import com.ainclusive.iotsim.domain.evidence.EvidenceView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = EvidenceController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
class EvidenceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EvidenceService service;

    private static EvidenceView view(String id, String status, String objectRef) {
        return new EvidenceView(id, "p1", "run-1", status,
                "{\"runId\":\"run-1\",\"completeness\":\"COMPLETE\"}", objectRef,
                Instant.parse("2026-06-01T00:00:00Z"), "local");
    }

    @Test
    void listReturnsEvidenceWithNestedManifest() throws Exception {
        given(service.list("p1")).willReturn(List.of(view("ev-1", "READY", "evidence/ev-1/bundle.zip")));

        MvcResult result = mvc.perform(get("/api/v1/projects/p1/evidence"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"id\":\"ev-1\"");
        assertThat(body).contains("\"status\":\"READY\"");
        assertThat(body).contains("\"manifest\":{");
        assertThat(body).contains("\"completeness\":\"COMPLETE\"");
        assertThat(body).contains("\"exported\":true");
    }

    @Test
    void getReturnsOneEvidence() throws Exception {
        given(service.find("p1", "ev-1")).willReturn(Optional.of(view("ev-1", "CAPTURING", null)));

        MvcResult result = mvc.perform(get("/api/v1/projects/p1/evidence/ev-1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"exported\":false");
    }

    @Test
    void getUnknownEvidenceReturns404() throws Exception {
        given(service.find("p1", "nope")).willReturn(Optional.empty());
        mvc.perform(get("/api/v1/projects/p1/evidence/nope")).andExpect(status().isNotFound());
    }

    @Test
    void exportDefaultsToBundleFormat() throws Exception {
        given(service.export("p1", "ev-1", EvidenceFormat.BUNDLE))
                .willReturn(view("ev-1", "READY", "evidence/ev-1/bundle.zip"));

        MvcResult result = mvc.perform(post("/api/v1/projects/p1/evidence/ev-1/export"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"READY\"");
    }

    @Test
    void exportHonorsTheFormatParameter() throws Exception {
        given(service.export("p1", "ev-1", EvidenceFormat.SUMMARY))
                .willReturn(view("ev-1", "READY", "evidence/ev-1/summary.json"));

        mvc.perform(post("/api/v1/projects/p1/evidence/ev-1/export").param("format", "SUMMARY"))
                .andExpect(status().isOk());
    }

    @Test
    void downloadStreamsTheBundleAsZipAttachment() throws Exception {
        byte[] zip = "PK fake-zip".getBytes(StandardCharsets.UTF_8);
        given(service.openBundle(eq("p1"), eq("ev-1")))
                .willReturn(Optional.of(
                        new EvidenceBundle(new ByteArrayInputStream(zip), "application/zip", "evidence-ev-1.zip")));

        MvcResult result = mvc.perform(get("/api/v1/projects/p1/evidence/ev-1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"evidence-ev-1.zip\""))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(zip);
    }

    @Test
    void downloadServesJsonContentTypeForSummaryArtifact() throws Exception {
        byte[] summary = "{\"summary\":{}}".getBytes(StandardCharsets.UTF_8);
        given(service.openBundle(eq("p1"), eq("ev-1"))).willReturn(Optional.of(
                new EvidenceBundle(new ByteArrayInputStream(summary), "application/json", "evidence-ev-1.json")));

        mvc.perform(get("/api/v1/projects/p1/evidence/ev-1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"evidence-ev-1.json\""));
    }

    @Test
    void downloadBeforeExportReturns404() throws Exception {
        given(service.openBundle(any(), any())).willReturn(Optional.empty());
        mvc.perform(get("/api/v1/projects/p1/evidence/ev-1/download")).andExpect(status().isNotFound());
    }
}
