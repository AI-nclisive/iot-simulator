package com.ainclusive.iotsim.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonSummaryEvidenceWriterTest {

    private final JsonSummaryEvidenceWriter writer = new JsonSummaryEvidenceWriter(new ObjectMapper());

    @Test
    void declaresSummaryFormatJsonContentTypeAndFilename() {
        assertThat(writer.format()).isEqualTo(EvidenceFormat.SUMMARY);
        assertThat(writer.contentType()).isEqualTo("application/json");
        assertThat(writer.artifactFilename()).isEqualTo("summary.json");
        assertThat(writer.formatVersion()).isEqualTo("1.0.0");
    }

    @Test
    void writesManifestAndSectionCountsNotFullData() {
        String json = write(fullContent());

        // origin/completeness via the full manifest
        assertThat(json).contains("\"formatVersion\":\"1.0.0\"");
        assertThat(json).contains("\"runId\":\"run-1\"");
        assertThat(json).contains("\"completeness\":\"COMPLETE\"");
        // section counts, not the underlying rows
        assertThat(json).contains("\"valueCount\":2");
        assertThat(json).contains("\"runtimeEventCount\":1");
        assertThat(json).contains("\"clientCount\":1");
        assertThat(json).contains("\"errorCount\":1");
        assertThat(json).contains("\"faultCount\":0");
        // the subset must NOT carry the full value-timeline rows
        assertThat(json).doesNotContain("ns=2;s=Temp");
    }

    @Test
    void neverSerializesSecretLikeFields() {
        assertThat(write(fullContent()).toLowerCase())
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("credential");
    }

    private String write(EvidenceContent content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(content, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static EvidenceContent fullContent() {
        Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
        EvidenceManifest manifest = new EvidenceManifest("1.0.0", "run-1", "REPLAY", "MANUAL",
                "local", t0, t0.plusSeconds(30), Completeness.COMPLETE, List.of("src-1"), null, "rec-1");
        return new EvidenceContent(
                manifest,
                List.of(new ValueSample("ns=2;s=Temp", t0, 21.5, "GOOD", null),
                        new ValueSample("ns=2;s=Temp", t0.plusSeconds(1), 21.6, "GOOD", null)),
                List.of(new RuntimeEventRecord("ERROR", t0.plusSeconds(5), "src-1", "{\"reason\":\"timeout\"}")),
                List.of(new ClientConnectionRecord("edge-7", "src-1", t0, t0.plusSeconds(20))),
                null,
                List.of(),
                List.of(new ErrorRecord("ERROR", t0.plusSeconds(5), "src-1", "timeout")));
    }
}
