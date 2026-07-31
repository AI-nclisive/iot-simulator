package com.ainclusive.iotsim.domain.common;

import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.SchemaNodeValidator;
import java.util.List;

/**
 * Shared pre-run/pre-save native-type validation (IS-199): detects opaque/vendor OPC UA
 * types that cannot be captured or replayed at schema save or run start time, instead of
 * failing mid-execution with an orphaned run/recording. Used by
 * {@code SchemaService}, {@code RecordingService}, {@code ReplayService}, and
 * {@code SyntheticRunService}.
 */
public final class SchemaNodeValidationUtil {
    private SchemaNodeValidationUtil() {}

    public static void validateTypes(List<SchemaNode> nodes) {
        List<String> issues = SchemaNodeValidator.validateTypes(nodes);
        if (!issues.isEmpty()) {
            throw new UnsupportedTypesException(issues);
        }
    }
}
