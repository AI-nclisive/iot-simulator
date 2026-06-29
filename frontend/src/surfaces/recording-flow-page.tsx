import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";

type RecordingUiState =
  | "ready"
  | "recording"
  | "no-values-yet"
  | "disconnected"
  | "save-ready"
  | "partial-save";

function formatDuration(durationSeconds: number) {
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function recordingTone(state: RecordingUiState): StatusTone {
  if (state === "recording" || state === "save-ready") {
    return "accent";
  }

  if (state === "disconnected" || state === "partial-save") {
    return "warning";
  }

  return "neutral";
}

function recordingLabel(state: RecordingUiState) {
  if (state === "no-values-yet") {
    return "No values yet";
  }

  if (state === "save-ready") {
    return "Save ready";
  }

  if (state === "partial-save") {
    return "Partial save";
  }

  return state[0].toUpperCase() + state.slice(1);
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

export function RecordingFlowPage() {
  const navigate = useNavigate();
  const { sourceId } = useParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const createRecording = useArtifactsStore((state) => state.createRecording);
  const finishRecording = useDataSourcesStore((state) => state.finishRecording);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const startRecording = useDataSourcesStore((state) => state.startRecording);
  const access = resolveAccess(accessMode, sharedRole);
  const captureAllowed = access.canRecordSource;
  const [recordingState, setRecordingState] = useState<RecordingUiState>(
    source?.process === "Recording" ? "recording" : "ready",
  );
  const [durationSeconds, setDurationSeconds] = useState(
    source?.process === "Recording" ? 46 : 0,
  );
  const [valueCount, setValueCount] = useState(source?.process === "Recording" ? 124 : 0);
  const [lastReceivedHint, setLastReceivedHint] = useState(
    source?.process === "Recording" ? "Just now" : "No values received yet",
  );
  const [recordingName, setRecordingName] = useState(
    source ? `${source.name} capture` : "New recording",
  );
  const [savedArtifactId, setSavedArtifactId] = useState<string | null>(null);

  const captureActive =
    recordingState === "recording" || recordingState === "no-values-yet";
  const replayBlockedByProcess =
    source?.process === "Replay" && recordingState === "ready";

  useEffect(() => {
    if (!source) {
      return;
    }

    setRecordingName(`${source.name} capture`);
  }, [source?.id]);

  useEffect(() => {
    if (!captureActive) {
      return;
    }

    const intervalId = window.setInterval(() => {
      setDurationSeconds((currentDuration) => {
        const nextDuration = currentDuration + 1;

        if (nextDuration < 3) {
          setLastReceivedHint("Waiting for the first value");
          setRecordingState("no-values-yet");
          return nextDuration;
        }

        setRecordingState("recording");
        setLastReceivedHint("Just now");
        setValueCount((currentCount) => currentCount + 6);

        return nextDuration;
      });
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [captureActive]);

  const saveSummary = useMemo(() => {
    if (!source) {
      return null;
    }

    return {
      createdBy: "You",
      duration: formatDuration(durationSeconds),
      protocol: source.protocol,
      sourceName: source.name,
      valueCount,
    };
  }, [durationSeconds, source, valueCount]);

  if (!source) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Return to Data Sources and open a valid source before starting capture."
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

  const activeSource = source;

  function resetCapture() {
    finishRecording(activeSource.id);
    setDurationSeconds(0);
    setValueCount(0);
    setLastReceivedHint("No values received yet");
    setRecordingState("ready");
    setSavedArtifactId(null);
  }

  function handleStartRecording() {
    if (!captureAllowed || replayBlockedByProcess) {
      return;
    }

    startRecording(activeSource.id, "You");
    setDurationSeconds(0);
    setValueCount(0);
    setLastReceivedHint("Waiting for the first value");
    setRecordingState("no-values-yet");
    setSavedArtifactId(null);
  }

  function handleStopRecording() {
    finishRecording(activeSource.id, "You");

    if (valueCount === 0) {
      setRecordingState("ready");
      setLastReceivedHint("No values were captured");
      return;
    }

    setRecordingState("save-ready");
    setLastReceivedHint("Capture stopped and ready to save");
  }

  function handleDisconnect() {
    finishRecording(activeSource.id, "You");
    setRecordingState("disconnected");
    setLastReceivedHint("Connection dropped before capture finished");
  }

  function saveReadyRecording() {
    const artifactId = createRecording({
      createdBy: "You",
      sourceId: activeSource.id,
      valueCount,
    });

    navigate(`/data-sources/${activeSource.id}/replay?artifactId=${artifactId}`);
  }

  function savePartialRecording() {
    const artifactId = createRecording({
      createdBy: "You",
      sourceId: activeSource.id,
      valueCount,
    });

    setSavedArtifactId(artifactId);
    setRecordingState("partial-save");
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
            <StatusBadge label={source.protocol} tone="neutral" />
            <StatusBadge
              label={recordingLabel(recordingState)}
              tone={recordingTone(recordingState)}
            />
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <MetricCard label="Capture state" value={recordingLabel(recordingState)} />
          <MetricCard
            label="Parameters"
            value={source.parameterCount.toLocaleString()}
          />
          <MetricCard label="Duration" value={formatDuration(durationSeconds)} />
          <MetricCard label="Captured values" value={valueCount.toLocaleString()} />
          <MetricCard label="Last received" value={lastReceivedHint} />
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {!captureActive ? (
            <button
              className="shell-action"
              disabled={!captureAllowed || replayBlockedByProcess}
              type="button"
              onClick={handleStartRecording}
            >
              Start recording
            </button>
          ) : (
            <>
              <button className="shell-action" type="button" onClick={handleStopRecording}>
                Stop recording
              </button>
              <button className="shell-action" type="button" onClick={handleDisconnect}>
                Simulate disconnect
              </button>
            </>
          )}

          <button
            className="shell-action"
            disabled={captureActive && valueCount > 0}
            type="button"
            onClick={resetCapture}
          >
            Discard
          </button>

          <Link className="shell-text-action" to={`/data-sources/${source.id}`}>
            Back to source
          </Link>
        </div>

        {replayBlockedByProcess ? (
          <div className="mt-6">
            <SharedStatePanel
              message="This source is already busy with replay. Stop replay first, then start a new recording."
              state="warning"
              title="Recording is blocked while replay is active."
            />
          </div>
        ) : null}

        {!captureAllowed ? (
          <div className="mt-6">
            <SharedStatePanel
              message="Shared User can inspect capture state, but starting or saving recordings is restricted to Admin."
              state="locked"
              title="Recording is read-only in this role."
            />
          </div>
        ) : null}
      </section>

      {recordingState === "save-ready" && saveSummary ? (
        <section className="shell-panel px-5 py-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0 max-w-3xl">
              <h3 className="text-lg font-semibold text-shell-ink">Save recording</h3>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Capture completed. Review the reusable artifact name, then continue into
                replay.
              </p>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                This recording represents activity across{" "}
                {source.parameterCount.toLocaleString()} parameters in the source.
              </p>
            </div>
            <StatusBadge label="Save ready" tone="accent" />
          </div>

          <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Recording name
              <input
                className="shell-field"
                type="text"
                value={recordingName}
                onChange={(event) => setRecordingName(event.target.value)}
              />
            </label>

            <div className="rounded-md border border-shell-line bg-white px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Save result
              </p>
              <dl className="mt-3 space-y-3 text-sm text-shell-muted">
                <div className="flex items-center justify-between gap-3">
                  <dt>Author</dt>
                  <dd className="font-medium text-shell-ink">{saveSummary.createdBy}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Duration</dt>
                  <dd className="font-medium text-shell-ink">{saveSummary.duration}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt>Values</dt>
                  <dd className="font-medium text-shell-ink">{saveSummary.valueCount}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap items-center gap-2">
            <button className="shell-action" type="button" onClick={saveReadyRecording}>
              Save recording
            </button>
            <button className="shell-action" type="button" onClick={resetCapture}>
              Discard capture
            </button>
          </div>
        </section>
      ) : null}

      {recordingState === "disconnected" && saveSummary ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message={
              valueCount > 0
                ? "Values were captured before the disconnect. Save this take as a partial recording or discard it."
                : "No values arrived before the disconnect. Discard this take and try again."
            }
            state="warning"
            title="Capture ended because the source disconnected."
          />

          {valueCount > 0 ? (
            <>
              <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Partial recording name
                  <input
                    className="shell-field"
                    type="text"
                    value={recordingName}
                    onChange={(event) => setRecordingName(event.target.value)}
                  />
                </label>

                <div className="rounded-md border border-shell-line bg-white px-4 py-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                    Partial result
                  </p>
                  <dl className="mt-3 space-y-3 text-sm text-shell-muted">
                    <div className="flex items-center justify-between gap-3">
                      <dt>Duration</dt>
                      <dd className="font-medium text-shell-ink">{saveSummary.duration}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt>Values</dt>
                      <dd className="font-medium text-shell-ink">{saveSummary.valueCount}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt>Status</dt>
                      <dd className="font-medium text-shell-ink">Partial</dd>
                    </div>
                  </dl>
                </div>
              </div>

              <div className="mt-5 flex flex-wrap items-center gap-2">
                <button className="shell-action" type="button" onClick={savePartialRecording}>
                  Save partial result
                </button>
                <button className="shell-action" type="button" onClick={resetCapture}>
                  Discard capture
                </button>
              </div>
            </>
          ) : (
            <div className="mt-5 flex flex-wrap items-center gap-2">
              <button className="shell-action" type="button" onClick={resetCapture}>
                Reset recording
              </button>
            </div>
          )}
        </section>
      ) : null}

      {recordingState === "partial-save" && savedArtifactId ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="The disconnected capture has been saved as a reusable partial recording. You can continue into replay or return to the source."
            state="warning"
            title="Partial recording saved."
          />
          <div className="mt-5 flex flex-wrap items-center gap-2">
            <button
              className="shell-action"
              type="button"
              onClick={() =>
                navigate(
                  `/data-sources/${activeSource.id}/replay?artifactId=${savedArtifactId}`,
                )
              }
            >
              Open replay
            </button>
            <Link className="shell-text-action" to={`/data-sources/${activeSource.id}`}>
              Back to source
            </Link>
          </div>
        </section>
      ) : null}
    </div>
  );
}
