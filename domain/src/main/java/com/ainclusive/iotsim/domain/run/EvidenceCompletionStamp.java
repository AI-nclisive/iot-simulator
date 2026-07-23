package com.ainclusive.iotsim.domain.run;

import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;

/**
 * Advisory helper (IS-187): flips an evidence record's {@code status} out of
 * {@code CAPTURING} alongside a run's terminal {@code runs.end(...)} write, mirroring
 * {@link RunCompletionEvents}. Without this, {@code status} is only ever written by
 * {@code EvidenceService.export()} — every completed/stopped/failed run's evidence would
 * otherwise sit as {@code CAPTURING} ("In progress" in the UI) forever unless the user
 * manually exports it, even though the run itself is long done.
 *
 * <p>Only acts while still {@code CAPTURING}, so a status an explicit export already set
 * (e.g. a live run exported mid-flight, IS-159) is never clobbered. Failures are
 * swallowed — a transient store error must not block or mask the run's own terminal write.
 */
public final class EvidenceCompletionStamp {

    private EvidenceCompletionStamp() {}

    public static void finalizeStatus(EvidenceRepository evidence, String runId, String terminalState) {
        try {
            evidence.findByRun(runId).filter(EvidenceCompletionStamp::isCapturing).ifPresent(row ->
                    evidence.updateStatus(row.id(), statusFor(terminalState), row.objectRef()));
        } catch (RuntimeException ignored) {
            // advisory only; must not block or mask the run's terminal-state write
        }
    }

    private static boolean isCapturing(EvidenceRow row) {
        return "CAPTURING".equals(row.status());
    }

    private static String statusFor(String terminalState) {
        return "COMPLETED".equals(terminalState) ? "READY" : "PARTIAL";
    }
}
