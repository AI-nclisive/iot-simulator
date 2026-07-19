package com.ainclusive.iotsim.domain.common;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tolerant single-field reader for a jsonb blob (e.g. {@code scenario_steps.params},
 * {@code evidence.manifest}): returns {@code null} if the document is blank, unparsable,
 * not an object, or the field is absent/the wrong type — callers turn that into their own
 * validation issue or treat it as "no reference" rather than failing on malformed JSON.
 */
public final class JsonField {

    private JsonField() {
    }

    public static String text(ObjectMapper json, String rawJson, String field) {
        JsonNode n = node(json, rawJson, field);
        return n != null && n.isString() ? n.asString() : null;
    }

    public static Long longValue(ObjectMapper json, String rawJson, String field) {
        JsonNode n = node(json, rawJson, field);
        return n != null && n.isNumber() ? n.asLong() : null;
    }

    private static JsonNode node(ObjectMapper json, String rawJson, String field) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = json.readTree(rawJson);
            return root.isObject() ? root.get(field) : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
