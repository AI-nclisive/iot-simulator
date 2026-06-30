package com.ainclusive.iotsim.domain.evidence;

import java.util.List;

/**
 * The assembled, in-memory content of an evidence artifact (IS-057), before it is
 * serialized by an {@link EvidenceArtifactWriter}. Carries the manifest plus the
 * gathered sections (value timelines, runtime events, client history, faults,
 * errors) and optional scenario metadata. Contains no secrets/PKI by construction —
 * it is built only from timeline/event/client data, never from source config.
 */
public record EvidenceContent(
        EvidenceManifest manifest,
        List<ValueSample> valueTimeline,
        List<RuntimeEventRecord> runtimeEvents,
        List<ClientConnectionRecord> clients,
        ScenarioMetadata scenario,
        List<FaultRecord> faults,
        List<ErrorRecord> errors) {
}
