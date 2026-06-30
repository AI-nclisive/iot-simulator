package com.ainclusive.iotsim.domain.evidence;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Default {@link EvidenceArtifactWriter}: a ZIP bundle of {@code manifest.json} plus
 * one JSON file per section (IS-057). Runtime-event payloads are embedded as nested
 * objects, never JSON-encoded strings. The {@code scenario.json} entry is written
 * only when the run had a scenario. IS-058 layers the JSON-summary subset and
 * version-compatibility rules onto this seam.
 */
@Component
public class ZipEvidenceArtifactWriter implements EvidenceArtifactWriter {

    private static final String FORMAT_VERSION = "1.0.0";

    private final ObjectMapper json;

    public ZipEvidenceArtifactWriter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void write(EvidenceContent content, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "manifest.json", json.writeValueAsBytes(content.manifest()));
            entry(zip, "value-timeline.json", json.writeValueAsBytes(content.valueTimeline()));
            entry(zip, "runtime-events.json", json.writeValueAsBytes(runtimeEvents(content)));
            entry(zip, "clients.json", json.writeValueAsBytes(content.clients()));
            entry(zip, "errors.json", json.writeValueAsBytes(content.errors()));
            entry(zip, "faults.json", json.writeValueAsBytes(content.faults()));
            if (content.scenario() != null) {
                entry(zip, "scenario.json", json.writeValueAsBytes(content.scenario()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write evidence bundle", e);
        }
    }

    @Override
    public String formatVersion() {
        return FORMAT_VERSION;
    }

    @Override
    public String contentType() {
        return "application/zip";
    }

    /** Renders runtime events with the payload as a nested object rather than a string. */
    private ArrayNode runtimeEvents(EvidenceContent content) {
        ArrayNode array = json.createArrayNode();
        for (RuntimeEventRecord event : content.runtimeEvents()) {
            ObjectNode node = json.createObjectNode();
            node.put("type", event.type());
            node.put("at", event.at() == null ? null : event.at().toString());
            node.put("dataSourceId", event.dataSourceId());
            JsonNode payload = json.readTree(event.payloadJson() != null ? event.payloadJson() : "{}");
            node.set("payload", payload);
            array.add(node);
        }
        return array;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
