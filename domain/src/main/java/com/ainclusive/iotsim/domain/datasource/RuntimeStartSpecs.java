package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.util.List;

/** Builds a {@link RuntimeStartSpec} from a data-source and its current schema. */
public final class RuntimeStartSpecs {

    private RuntimeStartSpecs() {}

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source) {
        return of(schemas, source, null);
    }

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source,
            DeterministicSettings deterministicSettings) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                source.simulatorPort(),
                deterministicSettings,
                EndpointSecurityCodec.toModel(source.securityConfig()));
    }
}
