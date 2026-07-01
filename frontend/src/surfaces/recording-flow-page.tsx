import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { apiFetch } from "../api";
import { useLiveValues } from "../shell/use-live-values";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";

// Backend response for capture start/stop
type RecordingResponse = {
  id: string;
  projectId: string;
  dataSourceId: string;
  schemaVersion: number;
  origin: string;
  valueCount: number;
  createdAt: string;
  createdBy: string;
  version: number;
};

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
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const appendRecording = useArtifactsStore((state) => state.appendRecording);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const push = useNotificationStore((state) => state.push);
  const access = resolveAccess(accessMode, sharedRole);
  const captureAllowed = access.canRecordSource;
  const [recordingState, setRecordingState] = useState<RecordingUiState>("ready");
  const [durationSeconds, setDurationSeconds] = useState(0);
  const [valueCount, setValueCount] = useState(0);
  const [lastReceivedHint, setLastReceivedHint] = useState("No values received yet");
  const [savedArtifactId, setSavedArtifactId] = useState<string | null>(null);
  const [activeRecordingId, setActiveRecordingId] = useState<string | null>(null);

  const captureActive =
    recordingState === "recording" || recordingState === "no-values-yet";

  // Real elapsed duration clock
  useEffect(() => {
    if (!captureActive) return;
    const id = window.setInterval(() => setDurationSeconds((d) => d + 1), 1000);
    return () => window.clearInterval(id);
  }, [captureActive]);

  // Live values from SSE — rows flow here when backend feeds them during capture
  const { rows: liveRows, status: liveStatus } = useLiveValues(
    sourceId ?? "",
    captureActive,
  );

  // Keep value count in sync with SSE rows during capture
  useEffect(() => {
    if (captureActive) {
      setValueCount(liveRows.length);
    }
  }, [captureActive, liveRows.length]);

  // Transition recording state from SSE connection status
  useEffect(() => {
    if (!captureActive) return;
    if (liveStatus === "open") {
      setRecordingState("recording");
      if (liveRows.length > 0) {
        const latest = liveRows.reduce(
          (max, r) => (r.updatedAt > max.updatedAt ? r : max),
          liveRows[0],
        );
        setLastReceivedHint(latest.updatedAt);
      } else {
        setLastReceivedHint("Waiting for the first value");
      }
    } else if (liveStatus === "stale") {
      setRecordingState("disconnected");
      setLastReceivedHint("Stream went stale — connection may have dropped");
    } else if (liveStatus === "reconnecting") {
      setLastReceivedHint("Reconnecting to live stream");
    }
  }, [captureActive, liveStatus, liveRows]);

  const saveSummary = useMemo(() => {
    if (!source) {
      return null;
    }

    return {
      createdBy: "You",
      duration: formatDuration(durationSeconds),
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
    setDurationSeconds(0);
    setValueCount(0);
    setLastReceivedHint("No values received yet");
    setRecordingState("ready");
    setSavedArtifactId(null);
    setActiveRecordingId(null);
  }

  async function handleStartRecording() {
    if (!captureAllowed || !currentProjectId || !activeSource) {
      return;
    }

    try {
      const resp = await apiFetch<RecordingResponse>(
        `/api/v1/projects/${currentProjectId}/data-sources/${activeSource.id}/recording/start`,
        { method: "POST" },
      );
      setActiveRecordingId(resp.id);
      setDurationSeconds(0);
      setValueCount(0);
      setLastReceivedHint("Waiting for the first value");
      setRecordingState("no-values-yet");
      setSavedArtifactId(null);
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to start recording";
      push({ tone: "error", title });
    }
  }

  async function handleStopRecording() {
    if (!currentProjectId || !activeSource || !activeRecordingId) {
      // Fallback: just reset state if we have no active recording
      if (valueCount === 0) {
        setRecordingState("ready");
        setLastReceivedHint("No values were captured");
      } else {
        setRecordingState("save-ready");
        setLastReceivedHint("Capture stopped and ready to save");
      }
      return;
    }

    try {
      const resp = await apiFetch<RecordingResponse>(
        `/api/v1/projects/${currentProjectId}/data-sources/${activeSource.id}/recording/stop`,
        { method: "POST" },
      );
      setValueCount(resp.valueCount);
      if (resp.valueCount === 0) {
        setRecordingState("ready");
        setLastReceivedHint("No values were captured");
      } else {
        setRecordingState("save-ready");
        setLastReceivedHint("Capture stopped and ready to save");
        // Persist the recording to the artifacts store
        appendRecording({
          id: resp.id,
          createdAt: resp.createdAt,
          createdBy: resp.createdBy,
          sourceId: resp.dataSourceId,
          valueCount: resp.valueCount,
        });
        setSavedArtifactId(resp.id);
      }
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to stop recording";
      push({ tone: "error", title });
      setRecordingState("ready");
      setLastReceivedHint("Stop failed — recording may be incomplete");
    }
  }

  function saveReadyRecording() {
    const artifactId = savedArtifactId ?? activeRecordingId ?? "";
    navigate(`/data-sources/${activeSource.id}/replay?artifactId=${artifactId}`);
  }

  function savePartialRecording() {
    const artifactId = savedArtifactId ?? activeRecordingId ?? "";
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
              disabled={!captureAllowed}
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
              <h3 className="text-lg font-semibold text-shell-ink">Recording saved</h3>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Capture saved as a reusable recording. Continue into replay or return to the source.
              </p>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                This recording covers activity across{" "}
                {source.parameterCount.toLocaleString()} parameters in the source.
              </p>
            </div>
            <StatusBadge label="Saved" tone="accent" />
          </div>

          <div className="mt-5">
            <div className="rounded-md border border-shell-line bg-white px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Recording summary
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
              Open replay
            </button>
            <button className="shell-action" type="button" onClick={resetCapture}>
              Record again
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
              <div className="mt-5">
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
