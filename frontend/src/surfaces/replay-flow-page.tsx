import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { useActiveRuns } from "../shell/use-active-runs";
import { apiFetch, ApiError } from "../api";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import { DeterministicRunSettings, type DeterministicSettings } from "./deterministic-run-settings";

// Backend response for POST /api/v1/projects/{pid}/data-sources/{dsId}/replay
type ReplayResponse = {
  recordingId: string;
  dataSourceId: string;
  valueCount: number;
  runId: string;
  evidenceId: string;
};

type ReplayUiState = "idle" | "running" | "completed" | "failed";

function replayTone(state: ReplayUiState): StatusTone {
  if (state === "running" || state === "completed") {
    return "accent";
  }

  if (state === "failed") {
    return "danger";
  }

  return "neutral";
}

function replayLabel(state: ReplayUiState): string {
  if (state === "idle") return "Ready";
  if (state === "running") return "Running";
  if (state === "completed") return "Done";
  if (state === "failed") return "Failed";
  return state;
}

function evidenceTone(status: "Ready" | "Assembling" | "Retry needed"): StatusTone {
  if (status === "Retry needed") {
    return "danger";
  }

  if (status === "Assembling") {
    return "warning";
  }

  return "accent";
}

function MetricCard({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <p className="mt-3 text-base font-semibold text-shell-ink">{value}</p>
    </div>
  );
}

export function ReplayFlowPage() {
  const { sourceId } = useParams();
  const [searchParams] = useSearchParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const artifacts = useArtifactsStore((state) => state.artifacts);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const push = useNotificationStore((state) => state.push);
  const access = resolveAccess(accessMode, sharedRole);
  const { runs: activeRuns } = useActiveRuns(currentProjectId);
  const [selectedArtifactId, setSelectedArtifactId] = useState(
    searchParams.get("artifactId") ?? artifacts[0]?.id ?? "",
  );
  const [speed, setSpeed] = useState("1x");
  const [replayState, setReplayState] = useState<ReplayUiState>("idle");
  const [runId, setRunId] = useState<string | null>(null);
  const runSeenRef = useRef(false);
  const [progress, setProgress] = useState(0);
  const [evidenceState, setEvidenceState] = useState<"Ready" | "Assembling" | "Retry needed">(
    "Ready",
  );
  const [runStartedAt, setRunStartedAt] = useState("Not started");
  const [deterministicSettings, setDeterministicSettings] = useState<DeterministicSettings | null>(null);
  const [deterministicOpen, setDeterministicOpen] = useState(false);
  const [schemaMismatch, setSchemaMismatch] = useState(false);

  const selectedArtifact = useMemo(
    () => artifacts.find((artifact) => artifact.id === selectedArtifactId),
    [artifacts, selectedArtifactId],
  );

  const compatibleArtifact = Boolean(selectedArtifact && source);

  const canConfigureReplay = access.canConfigureReplay;
  const replayReady = Boolean(selectedArtifact) && compatibleArtifact;

  useEffect(() => {
    if (replayState === "completed") {
      setProgress(100);
    }
  }, [replayState]);

  // Detect run completion via active-runs poll: once the run has been seen in the
  // list and then disappears, the server auto-completed it (all values dripped).
  useEffect(() => {
    if (!runId || replayState !== "running") {
      runSeenRef.current = false;
      return;
    }
    const found = activeRuns.some((r) => r.id === runId);
    if (found) {
      runSeenRef.current = true;
    } else if (runSeenRef.current) {
      setReplayState("completed");
      setEvidenceState("Ready");
      setRunId(null);
      runSeenRef.current = false;
    }
  }, [activeRuns, runId, replayState]);

  const runtimeEvents = useMemo(() => {
    if (!selectedArtifact) {
      return ["Choose a recording to prepare replay."];
    }

    const events = [
      `Artifact ${selectedArtifact.id} selected`,
      compatibleArtifact
        ? `Protocol matches ${source?.protocol}.`
        : "Protocol mismatch blocks replay on this source.",
    ];

    if (replayState === "running") {
      events.push(`Replay is running at ${speed}.`);
      events.push(`Progress is ${progress}%.`);
    }

    if (replayState === "completed") {
      events.push("Replay completed and evidence is ready.");
    }

    if (replayState === "failed") {
      events.push("Replay ended early and evidence needs another pass.");
    }

    return events;
  }, [
    compatibleArtifact,
    progress,
    replayState,
    selectedArtifact,
    source?.protocol,
    speed,
  ]);

  if (!source) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Return to Data Sources and open a valid source before starting replay."
            state="error"
            title="This source could not be found."
          />
          <div className="mt-4">
            <Link className="shell-text-action" to="/data-sources">
              Back to sources
            </Link>
          </div>
        </section>
      </div>
    );
  }

  if (artifacts.length === 0) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Save a recording first, then come back here to attach it to this source."
            state="empty"
            title="No recordings are available yet."
          />
          <div className="mt-4">
            <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
              Open recording
            </Link>
          </div>
        </section>
      </div>
    );
  }

  const activeSource = source;

  async function startReplay(compatibilityAck = false) {
    if (!selectedArtifact || !replayReady || !canConfigureReplay || !currentProjectId || !sourceId) {
      return;
    }

    setSchemaMismatch(false);
    setEvidenceState("Assembling");
    setProgress(0);
    setReplayState("running");
    setRunStartedAt(
      new Intl.DateTimeFormat("en-US", {
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date()),
    );

    try {
      const response = await apiFetch<ReplayResponse>(
        `/api/v1/projects/${currentProjectId}/data-sources/${sourceId}/replay`,
        {
          method: "POST",
          body: JSON.stringify({ recordingId: selectedArtifact.id, compatibilityAck }),
        },
      );
      setRunId(response.runId);
      runSeenRef.current = true;
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setReplayState("idle");
        setEvidenceState("Ready");
        setSchemaMismatch(true);
        return;
      }
      const title = err instanceof Error ? err.message : "Replay failed";
      push({ tone: "error", title });
      setReplayState("failed");
      setEvidenceState("Retry needed");
    }
  }

  function handleStartReplay() {
    return startReplay(false);
  }

  async function handleStopReplay() {
    if (!runId || !currentProjectId) return;
    try {
      await apiFetch(`/api/v1/projects/${currentProjectId}/runs/${runId}/stop`, { method: "POST" });
    } catch (err) {
      const title = err instanceof Error ? err.message : "Stop failed";
      push({ tone: "error", title });
      return;
    }
    setReplayState("failed");
    setEvidenceState("Retry needed");
    setRunId(null);
    runSeenRef.current = false;
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <h2 className="text-2xl font-semibold text-shell-ink">{source.name}</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">{source.endpoint}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label="Replay" tone="accent" />
            <StatusBadge label={source.protocol} tone="neutral" />
            <StatusBadge label={replayLabel(replayState)} tone={replayTone(replayState)} />
            <StatusBadge label={`Evidence: ${evidenceState}`} tone={evidenceTone(evidenceState)} />
          </div>
        </div>

        <div className="mt-6 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div className="grid gap-4">
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Recording
              <select
                className="shell-field"
                disabled={!canConfigureReplay || replayState === "running"}
                value={selectedArtifactId}
                onChange={(event) => setSelectedArtifactId(event.target.value)}
              >
                {artifacts.map((artifact) => (
                  <option key={artifact.id} value={artifact.id}>
                    Artifact {artifact.id}
                  </option>
                ))}
              </select>
            </label>

            <div className="grid gap-3 md:grid-cols-2">
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Replay speed
                <select
                  className="shell-field"
                  disabled={!canConfigureReplay || replayState === "running"}
                  value={speed}
                  onChange={(event) => setSpeed(event.target.value)}
                >
                  <option value="0.5x">0.5x</option>
                  <option value="1x">1x</option>
                  <option value="2x">2x</option>
                </select>
              </label>

              <div className="rounded-md border border-shell-line bg-white px-4 py-4">
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Compatibility
                </p>
                <p className="mt-3 text-sm font-medium text-shell-ink">
                  {selectedArtifact
                    ? compatibleArtifact
                      ? "Ready for this source"
                      : "Protocol mismatch"
                    : "Choose an artifact"}
                </p>
                <p className="mt-2 text-sm leading-6 text-shell-muted">
                  {selectedArtifact
                    ? compatibleArtifact
                      ? `Artifact ${selectedArtifact.id} can replay through ${source.protocol}.`
                      : `Source protocol does not match. Cannot replay through ${source.protocol}.`
                    : "Select a recording to review impact before starting."}
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Selected artifact
            </p>
            {selectedArtifact ? (
              <dl className="mt-3 space-y-3 text-sm text-shell-muted">
                <div className="flex items-center justify-between gap-3">
                  <dt>Author</dt>
                  <dd className="font-medium text-shell-ink">{selectedArtifact.createdBy}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Created</dt>
                  <dd className="font-medium text-shell-ink">{selectedArtifact.createdAt}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Values</dt>
                  <dd className="font-medium text-shell-ink">{selectedArtifact.valueCount}</dd>
                </div>
              </dl>
            ) : (
              <p className="mt-3 text-sm leading-6 text-shell-muted">
                No artifact is selected yet.
              </p>
            )}
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            label="Target source"
            value={source.status === "Active" ? "Run" : "Off"}
          />
          <MetricCard
            label="Parameters"
            value={source.parameterCount.toLocaleString()}
          />
          <MetricCard label="Replay progress" value={`${progress}%`} />
          <MetricCard label="Started at" value={runStartedAt} />
        </div>

        <div className="mt-6 rounded-md border border-shell-line bg-shell-base/55 px-4 py-4">
          <button
            aria-expanded={deterministicOpen}
            className="flex w-full items-center justify-between gap-2 text-left"
            type="button"
            onClick={() =>
              setDeterministicOpen((open) => {
                if (open) setDeterministicSettings(null);
                return !open;
              })
            }
          >
            <span className="text-sm font-medium text-shell-ink">Deterministic settings</span>
            <span className="text-xs text-shell-muted">{deterministicOpen ? "Hide" : "Show"}</span>
          </button>

          {deterministicOpen ? (
            <div className="mt-4">
              <DeterministicRunSettings onChange={setDeterministicSettings} />
            </div>
          ) : null}
        </div>

        {schemaMismatch && (
          <div className="mt-6 rounded-md border border-amber-200 bg-amber-50 px-4 py-4">
            <p className="text-sm font-medium text-amber-900">Schema version mismatch</p>
            <p className="mt-1 text-sm text-amber-800">
              This recording was captured with a different schema version than the source currently
              has. The replay may produce unexpected results.
            </p>
            <div className="mt-3 flex gap-3">
              <button
                className="shell-action"
                type="button"
                onClick={() => startReplay(true)}
              >
                Run anyway
              </button>
              <button
                className="shell-text-action"
                type="button"
                onClick={() => setSchemaMismatch(false)}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {replayState !== "running" ? (
            <button
              className="shell-action"
              disabled={
                !canConfigureReplay || !selectedArtifact || !replayReady || schemaMismatch
              }
              type="button"
              onClick={handleStartReplay}
            >
              Start replay
            </button>
          ) : (
            <button className="shell-action" type="button" onClick={handleStopReplay}>
              Stop replay
            </button>
          )}

          <Link className="shell-text-action" to={`/data-sources/${source.id}`}>
            Back to source
          </Link>

          {deterministicSettings ? (
            <p className="text-xs text-shell-muted">
              {deterministicSettings.mode === "seed"
                ? `Repeatability: seed ${deterministicSettings.seed}, ${deterministicSettings.ordering === "original" ? "original order" : "alphabetical order"}`
                : `Repeatability: ${deterministicSettings.preset} preset, ${deterministicSettings.ordering === "original" ? "original order" : "alphabetical order"}`}
            </p>
          ) : null}
        </div>

        {!canConfigureReplay ? (
          <div className="mt-6">
            <SharedStatePanel
              message="Shared User can observe replay setup, but only Admin can choose artifacts and start replay."
              state="locked"
              title="Replay is read-only in this role."
            />
          </div>
        ) : null}
      </section>

      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <h3 className="text-lg font-semibold text-shell-ink">Runtime activity</h3>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Replay should feel like a direct continuation of recording, with its
              impact and evidence visible before and after launch.
            </p>
          </div>
          <StatusBadge label={`Evidence: ${evidenceState}`} tone={evidenceTone(evidenceState)} />
        </div>

        <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div className="rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Runtime events
            </p>
            <ul className="mt-3 space-y-3 text-sm text-shell-muted">
              {runtimeEvents.map((event) => (
                <li key={event} className="rounded-md border border-shell-line/70 px-3 py-3">
                  {event}
                </li>
              ))}
            </ul>
          </div>

          <div className="rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Evidence result
            </p>
            <p className="mt-3 text-sm font-medium text-shell-ink">
              {evidenceState === "Ready"
                ? "Evidence can be reviewed after completion."
                : evidenceState === "Assembling"
                  ? "Evidence is assembling while replay runs."
                  : "Evidence needs another pass after failure."}
            </p>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Evidence keeps replay authorship tied to the initiating run and target
              source.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
