package com.epam.iotsim.domain.datasource;

import com.epam.iotsim.persistence.datasource.DataSourceRow;
import com.epam.iotsim.persistence.schema.SchemaRepository;
import com.epam.iotsim.persistence.schema.SchemaWithNodes;
import com.epam.iotsim.platform.runtime.RuntimeStartSpec;
import java.util.List;

/** Builds a {@link RuntimeStartSpec} from a data-source and its current schema. */
public final class RuntimeStartSpecs {

    private RuntimeStartSpecs() {}

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                0); // listen port: ephemeral for now (TODO: from endpoint config)
    }
}
