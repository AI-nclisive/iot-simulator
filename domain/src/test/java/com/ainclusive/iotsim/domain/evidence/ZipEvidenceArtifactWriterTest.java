package com.ainclusive.iotsim.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ZipEvidenceArtifactWriterTest {

    private final ZipEvidenceArtifactWriter writer = new ZipEvidenceArtifactWriter(new ObjectMapper());

    @Test
    void exposesZipContentTypeAndFormatVersion() {
        assertThat(writer.contentType()).isEqualTo("application/zip");
        assertThat(writer.formatVersion()).isEqualTo("1.0.0");
    }

    @Test
    void writesManifestAndOneEntryPerSection() {
        Map<String, String> zip = unzip(write(fullContent()));

        assertThat(zip).containsKeys("manifest.json", "value-timeline.json",
                "runtime-events.json", "clients.json", "errors.json", "faults.json");
        assertThat(zip.get("manifest.json"))
                .contains("\"formatVersion\":\"1.0.0\"")
                .contains("\"runId\":\"run-1\"")
                .contains("\"completeness\":\"COMPLETE\"");
        assertThat(zip.get("value-timeline.json")).contains("\"nodeId\":\"ns=2;s=Temp\"");
        assertThat(zip.get("clients.json")).contains("\"clientId\":\"edge-7\"");
    }

    @Test
    void embedsRuntimeEventPayloadAsNestedObjectNotString() {
        Map<String, String> zip = unzip(write(fullContent()));
        // payload must be a nested JSON object, never a JSON-encoded string.
        assertThat(zip.get("runtime-events.json")).contains("\"payload\":{\"reason\":\"timeout\"}");
    }

    @Test
    void omitsScenarioEntryWhenRunHasNoScenario() {
        Map<String, String> zip = unzip(write(fullContent())); // scenario == null
        assertThat(zip).doesNotContainKey("scenario.json");
    }

    @Test
    void neverSerializesSecretLikeFields() {
        String all = String.join("\n", unzip(write(fullContent())).values());
        assertThat(all.toLowerCase())
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("credential")
                .doesNotContain("privatekey");
    }

    private byte[] write(EvidenceContent content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(content, out);
        return out.toByteArray();
    }

    private static EvidenceContent fullContent() {
        Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
        EvidenceManifest manifest = new EvidenceManifest("1.0.0", "run-1", "REPLAY", "MANUAL",
                "local", t0, t0.plusSeconds(30), Completeness.COMPLETE, List.of("src-1"), null, "rec-1");
        return new EvidenceContent(
                manifest,
                List.of(new ValueSample("ns=2;s=Temp", t0, 21.5, "GOOD", null)),
                List.of(new RuntimeEventRecord("ERROR", t0.plusSeconds(5), "src-1", "{\"reason\":\"timeout\"}")),
                List.of(new ClientConnectionRecord("edge-7", "src-1", t0, t0.plusSeconds(20))),
                null, // no scenario on a replay run
                List.of(),
                List.of(new ErrorRecord("ERROR", t0.plusSeconds(5), "src-1", "timeout")));
    }

    private static Map<String, String> unzip(byte[] bytes) {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }
}
