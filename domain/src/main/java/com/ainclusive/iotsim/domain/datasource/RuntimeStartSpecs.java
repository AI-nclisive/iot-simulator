package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Builds a {@link RuntimeStartSpec} from a data-source and its current schema. */
public final class RuntimeStartSpecs {

    private RuntimeStartSpecs() {}

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source, ObjectMapper json) {
        return of(schemas, source, null, json);
    }

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source,
            DeterministicSettings deterministicSettings, ObjectMapper json) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                listenPort(source.runtimeConfig(), json),
                deterministicSettings);
    }

    /** Desired protocol listen port from {@code runtimeConfig.listenPort}; 0 (ephemeral) on
     *  null/blank/unparseable/≤0. Never throws. */
    public static int listenPort(String runtimeConfig, ObjectMapper json) {
        if (runtimeConfig == null || runtimeConfig.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = json.readTree(runtimeConfig);
            JsonNode port = root.isObject() ? root.get("listenPort") : null;
            int value = port != null && port.isNumber() ? port.asInt() : 0;
            return Math.max(value, 0);
        } catch (JacksonException e) {
            return 0;
        }
    }
}
