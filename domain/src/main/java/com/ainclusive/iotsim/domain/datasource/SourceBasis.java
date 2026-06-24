package com.ainclusive.iotsim.domain.datasource;

/** How a data-source was created (backend-specs/03_DOMAIN_MODEL.md). */
public enum SourceBasis {
    SCAN,
    MANUAL,
    IMPORT,
    SYNTHETIC
}
