package com.ainclusive.iotsim.api;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal configuration anchor for {@code @WebMvcTest} slices in :api.
 * The real app entry-point lives in :app; this stub lets the bootstrapper find a
 * {@code @SpringBootConfiguration} while the {@code @SpringBootApplication} component-scan
 * still respects the {@code WebMvcTypeExcludeFilter} used by {@code @WebMvcTest}.
 */
@SpringBootApplication
public class TestApiApplication {
}
