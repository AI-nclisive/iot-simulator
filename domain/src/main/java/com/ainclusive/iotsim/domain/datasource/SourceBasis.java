package com.ainclusive.iotsim.domain.datasource;

/** How a data-source was created (openspec/specs/domain-model/spec.md). */
public enum SourceBasis {
    SCAN,
    MANUAL,
    IMPORT,
    SYNTHETIC
}
