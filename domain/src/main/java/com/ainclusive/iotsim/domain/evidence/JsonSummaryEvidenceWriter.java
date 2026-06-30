package com.ainclusive.iotsim.domain.evidence;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON {@code SUMMARY} {@link EvidenceArtifactWriter} (IS-058): a compact subset for
 * quick sharing — the full manifest (origin, initiator, completeness; no secrets)
 * plus per-section counts, without the underlying timeline/event/client rows. The
 * full data lives in the {@code BUNDLE} ({@link ZipEvidenceArtifactWriter}).
 */
@Component
public class JsonSummaryEvidenceWriter implements EvidenceArtifactWriter {

    private final ObjectMapper json;

    public JsonSummaryEvidenceWriter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public EvidenceFormat format() {
        return EvidenceFormat.SUMMARY;
    }

    @Override
    public void write(EvidenceContent content, OutputStream out) {
        ObjectNode root = json.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.set("manifest", json.valueToTree(content.manifest()));

        ObjectNode counts = json.createObjectNode();
        counts.put("valueCount", content.valueTimeline().size());
        counts.put("runtimeEventCount", content.runtimeEvents().size());
        counts.put("clientCount", content.clients().size());
        counts.put("faultCount", content.faults().size());
        counts.put("errorCount", content.errors().size());
        root.set("summary", counts);

        try {
            out.write(json.writeValueAsBytes(root));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write evidence summary", e);
        }
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String artifactFilename() {
        return "summary.json";
    }
}
