package com.ainclusive.iotsim.domain.io;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.util.List;
import java.util.Map;

/**
 * In-memory snapshot of all project content to be exported (IS-073).
 *
 * <p>Contains only non-secret data: project metadata, data-source config (no
 * credentials), schemas, recordings metadata, and samples. Connection secrets
 * and PKI material are excluded by design.
 */
public record ProjectExportContent(
        Project project,
        List<DataSource> dataSources,
        /** Schema nodes keyed by data-source id. */
        Map<String, SchemaWithNodes> schemas,
        List<Recording> recordings,
        List<Sample> samples) {}
