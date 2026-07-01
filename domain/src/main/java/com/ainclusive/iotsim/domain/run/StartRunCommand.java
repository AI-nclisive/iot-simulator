package com.ainclusive.iotsim.domain.run;

/**
 * Automation-facing command to start a new run (IS-089).
 *
 * <p>Field usage by kind:
 * <ul>
 *   <li>REPLAY: {@code dataSourceId}, {@code recordingId}, {@code seed}, {@code startTime},
 *       {@code compatibilityAck}
 *   <li>SYNTHETIC: {@code dataSourceId}, {@code durationMs}
 *   <li>SCENARIO: {@code scenarioId}
 * </ul>
 */
public record StartRunCommand(
        String kind,
        String initiator,
        String dataSourceId,
        String recordingId,
        Long durationMs,
        String scenarioId,
        Long seed,
        String startTime,
        Boolean compatibilityAck) {
}
