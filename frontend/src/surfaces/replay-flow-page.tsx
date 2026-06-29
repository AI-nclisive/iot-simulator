import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import { DeterministicRunSettings, type DeterministicSettings } from "./deterministic-run-settings";

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
  const artifacts = useArtifactsStore((state) => state.artifacts);
  const assignReplayArtifact = useDataSourcesStore((state) => state.assignReplayArtifact);
  const finishReplay = useDataSourcesStore((state) => state.finishReplay);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const startReplay = useDataSourcesStore((state) => state.startReplay);
  const access = resolveAccess(accessMode, sharedRole);
  const [selectedArtifactId, setSelectedArtifactId] = useState(
    searchParams.get("artifactId") ?? artifacts[0]?.id ?? "",
  );
  const [speed, setSpeed] = useState("1x");
  const [replayState, setReplayState] = useState<ReplayUiState>("idle");
  const [progress, setProgress] = useState(0);
  const [evidenceState, setEvidenceState] = useState<"Ready" | "Assembling" | "Retry needed">(
    "Ready",
  );
  const [runStartedAt, setRunStartedAt] = useState("Not started");
  const [deterministicSettings, setDeterministicSettings] = useState<DeterministicSettings | null>(null);
  const [deterministicOpen, setDeterministicOpen] = useState(false);

  const selectedArtifact = useMemo(
    () => artifacts.find((artifact) => artifact.id === selectedArtifactId),
    [artifacts, selectedArtifactId],
  );
  const assignedArtifact = useMemo(
    () =>
      artifacts.find((artifact) => artifact.id === source?.assignedReplayArtifactId) ?? null,
    [artifacts, source?.assignedReplayArtifactId],
  );

  const compatibleArtifact =
    selectedArtifact && source
      ? selectedArtifact.protocol === source.protocol
      : false;
  const sourceBusyWithAnotherProcess =
    source?.process === "Recording" ||
    (source?.process === "Replay" && replayState !== "running");
  const canConfigureReplay = access.canConfigureReplay;
  const hasPendingAssignment =
    Boolean(selectedArtifact) && selectedArtifact?.id !== source?.assignedReplayArtifactId;
  const replayReady =
    Boolean(selectedArtifact) &&
    selectedArtifact?.id === source?.assignedReplayArtifactId &&
    compatibleArtifact;

  useEffect(() => {
    if (!source || replayState !== "running") {
      return;
    }

    const intervalId = window.setInterval(() => {
      setProgress((currentProgress) => {
        const nextProgress = Math.min(currentProgress + 20, 100);

        if (nextProgress >= 100) {
          window.clearInterval(intervalId);
          finishReplay(source.id, "You");
          setReplayState("completed");
          setEvidenceState("Ready");
        }

        return nextProgress;
      });
    }, 900);

    return () => window.clearInterval(intervalId);
  }, [finishReplay, replayState, source]);

  const runtimeEvents = useMemo(() => {
    if (!selectedArtifact) {
      return ["Choose a recording or sample to prepare replay."];
    }

    const events = [
      `${selectedArtifact.type} selected: ${selectedArtifact.name}`,
      selectedArtifact.id === source?.assignedReplayArtifactId
        ? "Selected artifact is already assigned to this replay target."
        : "Selected artifact still needs explicit assignment before replay starts.",
      compatibleArtifact
        ? `Protocol matches ${source?.protocol}.`
        : "Protocol mismatch blocks replay on this source.",
    ];

    if (source?.clients === 0) {
      events.push("No active client is connected right now.");
    } else {
      events.push(
        `${source?.clients ?? 0} client${source?.clients === 1 ? "" : "s"} can receive replay.`,
      );
    }

    if (sourceBusyWithAnotherProcess) {
      events.push("Target is already busy with another runtime process.");
    }

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
    source?.assignedReplayArtifactId,
    source?.clients,
    source?.protocol,
    sourceBusyWithAnotherProcess,
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
            title="No recordings or samples are available yet."
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

  function assignArtifactToReplay() {
    if (
      !selectedArtifact ||
      !compatibleArtifact ||
      !canConfigureReplay ||
      sourceBusyWithAnotherProcess
    ) {
      return;
    }

    assignReplayArtifact(activeSource.id, selectedArtifact.id, "You");
  }

  function handleStartReplay() {
    if (
      !selectedArtifact ||
      !replayReady ||
      sourceBusyWithAnotherProcess ||
      !canConfigureReplay
    ) {
      return;
    }

    startReplay(activeSource.id, "You");
    setEvidenceState("Assembling");
    setProgress(0);
    setReplayState("running");
    setRunStartedAt(
      new Intl.DateTimeFormat("en-US", {
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date()),
    );
  }

  function handleStopReplay() {
    finishReplay(activeSource.id, "You");
    setReplayState("failed");
    setEvidenceState("Retry needed");
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
            <StatusBadge label={replayState} tone={replayTone(replayState)} />
            <StatusBadge label={`Evidence: ${evidenceState}`} tone={evidenceTone(evidenceState)} />
          </div>
        </div>

        <div className="mt-6 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div className="grid gap-4">
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Recording or sample
              <select
                className="shell-field"
                disabled={!canConfigureReplay || replayState === "running"}
                value={selectedArtifactId}
                onChange={(event) => setSelectedArtifactId(event.target.value)}
              >
                {artifacts.map((artifact) => (
                  <option key={artifact.id} value={artifact.id}>
                    {artifact.name} · {artifact.type} · {artifact.protocol}
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
                      ? `${selectedArtifact.type} can replay through ${source.protocol}.`
                      : `${selectedArtifact.protocol} cannot replay through ${source.protocol}.`
                    : "Select a recording or sample to review impact before starting."}
                </p>
              </div>
            </div>

            <div className="rounded-md border border-shell-line bg-white px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Replay assignment
              </p>
              <dl className="mt-3 space-y-3 text-sm text-shell-muted">
                <div className="flex items-center justify-between gap-3">
                  <dt>Current</dt>
                  <dd className="font-medium text-shell-ink">
                    {assignedArtifact ? assignedArtifact.name : "No artifact assigned"}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Selected</dt>
                  <dd className="font-medium text-shell-ink">
                    {selectedArtifact ? selectedArtifact.name : "Nothing selected"}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Impact</dt>
                  <dd className="max-w-[16rem] text-right font-medium text-shell-ink">
                    {!selectedArtifact
                      ? "Select an artifact first"
                      : !compatibleArtifact
                        ? "Assignment is blocked by protocol mismatch"
                        : assignedArtifact && assignedArtifact.id !== selectedArtifact.id
                          ? "This replaces the current replay assignment before runtime starts"
                          : assignedArtifact && assignedArtifact.id === selectedArtifact.id
                            ? "This artifact is already assigned to the target source"
                            : "This attaches the first replay artifact to the target source"}
                  </dd>
                </div>
              </dl>

              <p className="mt-4 text-sm leading-6 text-shell-muted">
                {activeSource.clients > 0
                  ? "Connected clients will receive values from the assigned artifact after replay starts."
                  : "No connected client is shown right now, but the assignment will be used the next time replay starts."}
              </p>

              <div className="mt-4 flex flex-wrap items-center gap-2">
                <button
                  className="shell-action"
                  disabled={
                    !selectedArtifact ||
                    !compatibleArtifact ||
                    !canConfigureReplay ||
                    sourceBusyWithAnotherProcess ||
                    !hasPendingAssignment
                  }
                  type="button"
                  onClick={assignArtifactToReplay}
                >
                  Assign artifact
                </button>
                {replayReady ? <StatusBadge label="Assigned" tone="accent" /> : null}
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
                  <dt>Type</dt>
                  <dd className="font-medium text-shell-ink">{selectedArtifact.type}</dd>
                </div>
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

        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <MetricCard
            label="Target source"
            value={sourceBusyWithAnotherProcess ? "Already active" : "Ready"}
          />
          <MetricCard
            label="Parameters"
            value={source.parameterCount.toLocaleString()}
          />
          <MetricCard label="Client activity" value={source.clients} />
          <MetricCard label="Replay progress" value={`${progress}%`} />
          <MetricCard label="Started at" value={runStartedAt} />
        </div>

        <div className="mt-6 rounded-md border border-shell-line bg-shell-base/55 px-4 py-4">
          <button
            aria-expanded={deterministicOpen}
            className="flex w-full items-center justify-between gap-2 text-left"
            type="button"
            onClick={() => setDeterministicOpen((open) => !open)}
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

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {replayState !== "running" ? (
            <button
              className="shell-action"
              disabled={
                !canConfigureReplay || !selectedArtifact || !replayReady || sourceBusyWithAnotherProcess
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

        {selectedArtifact && compatibleArtifact && hasPendingAssignment ? (
          <div className="mt-6">
            <SharedStatePanel
              message="Assign the selected recording or sample to this source first. Replay start stays disabled until the target assignment is explicit."
              state="warning"
              title="Replay assignment is still pending."
            />
          </div>
        ) : null}

        {sourceBusyWithAnotherProcess ? (
          <div className="mt-6">
            <SharedStatePanel
              message="This target is already occupied by another runtime action. Stop the current process before launching replay."
              state="warning"
              title="Target is already active."
            />
          </div>
        ) : null}

        {source.clients === 0 ? (
          <div className="mt-6">
            <SharedStatePanel
              message="Replay can still start, but there is no connected client to observe the output yet."
              state="warning"
              title="No clients are currently attached."
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
