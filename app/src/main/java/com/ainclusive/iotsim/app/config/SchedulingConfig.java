package com.ainclusive.iotsim.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} task execution for the application.
 *
 * <p>Required by {@link com.ainclusive.iotsim.domain.auth.EditLeaseService#cleanupExpired()}
 * which runs on a fixed-delay schedule to purge stale edit leases (IS-080).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
