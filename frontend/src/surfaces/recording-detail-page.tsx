import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type TabId = "schema" | "values";

type ValueEntry = {
  parameterId: string;
  timestamp: string;
  value: string | null;
  quality: string;
};

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

export function RecordingDetailPage() {
  const { recordingId } = useParams<{ recordingId: string }>();
  const navigate = useNavigate();
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const artifacts = useArtifactsStore((s) => s.artifacts);
  const isLoading = useArtifactsStore((s) => s.isLoading);
  const error = useArtifactsStore((s) => s.error);
  const loadRecordingById = useArtifactsStore((s) => s.loadRecordingById);

  const [activeTab, setActiveTab] = useState<TabId>("schema");

  const [values, setValues] = useState<ValueEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [valuesLoading, setValuesLoading] = useState(false);
  const [valuesError, setValuesError] = useState<string | null>(null);
  const [valuesLoaded, setValuesLoaded] = useState(false);

  useEffect(() => {
    if (currentProjectId && recordingId) {
      void loadRecordingById(currentProjectId, recordingId);
    }
  }, [currentProjectId, recordingId, loadRecordingById]);

  const recording = artifacts.find((a) => a.id === recordingId) ?? null;

  const loadValues = useCallback(
    async (cursor?: string) => {
      if (!currentProjectId || !recordingId) return;
      setValuesLoading(true);
      setValuesError(null);
      try {
        const qs = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
        const page = await apiFetch<{ items: ValueEntry[]; nextCursor: string | null }>(
          `/api/v1/projects/${currentProjectId}/recordings/${recordingId}/values${qs}`,
        );
        setValues((prev) => (cursor ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor ?? null);
        setValuesLoaded(true);
      } catch (err) {
        setValuesError(err instanceof ApiError ? err.title : "Failed to load values");
      } finally {
        setValuesLoading(false);
      }
    },
    [currentProjectId, recordingId],
  );

  useEffect(() => {
    if (activeTab === "values" && !valuesLoaded && recording && recording.valueCount > 0) {
      void loadValues();
    }
  }, [activeTab, valuesLoaded, recording, loadValues]);

  function tabClass(tab: TabId) {
    return `border-b-2 px-4 py-2 text-sm font-medium transition ${
      activeTab === tab
        ? "border-shell-accent text-shell-ink"
        : "border-transparent text-shell-muted hover:text-shell-ink"
    }`;
  }

  if (isLoading) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel message="Loading recording details." state="loading" title="Loading…" />
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel message={error} state="error" title="Could not load recording." />
        </section>
      </div>
    );
  }

  if (!recording) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="The recording could not be found. It may have been deleted."
            state="empty"
            title="Recording not found."
          />
        </section>
      </div>
    );
  }

  function renderValuesTab() {
    if (recording!.valueCount === 0) {
      return (
        <SharedStatePanel
          message="No data values were captured in this recording. This recording contains schema only."
          state="empty"
          title="No values captured."
        />
      );
    }

    if (valuesError && values.length === 0) {
      return <SharedStatePanel message={valuesError} state="error" title="Failed to load values." />;
    }

    if (valuesLoading && values.length === 0) {
      return <SharedStatePanel message="Loading values…" state="loading" title="" />;
    }

    return (
      <div className="space-y-4">
        <div className="overflow-x-auto rounded-md border border-shell-line">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-shell-line bg-shell-base/55 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                <th className="px-4 py-3">Timestamp</th>
                <th className="px-4 py-3">Parameter</th>
                <th className="px-4 py-3">Value</th>
                <th className="px-4 py-3">Quality</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-shell-line bg-white">
              {values.map((v, i) => (
                <tr key={`${v.parameterId}-${v.timestamp}-${i}`}>
                  <td className="px-4 py-2 font-mono text-xs text-shell-muted whitespace-nowrap">
                    {formatDate(v.timestamp)}
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-shell-ink break-all">
                    {v.parameterId}
                  </td>
                  <td className="px-4 py-2 text-shell-ink">{v.value ?? "—"}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${
                        v.quality === "GOOD"
                          ? "bg-green-50 text-green-700"
                          : "bg-amber-50 text-amber-700"
                      }`}
                    >
                      {v.quality}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {nextCursor ? (
          <div className="flex flex-col items-center gap-2">
            <button
              className="shell-action"
              disabled={valuesLoading}
              type="button"
              onClick={() => { setValuesError(null); void loadValues(nextCursor); }}
            >
              {valuesLoading ? "Loading…" : "Load more"}
            </button>
            {valuesError ? (
              <p className="text-xs text-red-600">{valuesError}</p>
            ) : null}
          </div>
        ) : null}

        {!nextCursor && values.length > 0 ? (
          <p className="text-center text-xs text-shell-muted">
            All {recording!.valueCount.toLocaleString()} values loaded.
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <button
              className="text-sm text-shell-muted hover:text-shell-ink transition"
              type="button"
              onClick={() => navigate("/recordings")}
            >
              ← Recordings
            </button>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">
              Recording{" "}
              <span className="font-mono text-lg text-shell-muted">{recording.id.slice(0, 8)}</span>
            </h2>
          </div>
          <StatusBadge
            label={recording.origin === "captured" ? "Recorded" : "Imported"}
            tone="neutral"
          />
        </div>
      </section>

      <section className="shell-panel px-5 py-5">
        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 text-sm">
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Data source
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.sourceId || "—"}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Captured at
            </dt>
            <dd className="mt-2 text-shell-ink">{formatDate(recording.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Captured by
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.createdBy || "—"}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Values recorded
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.valueCount.toLocaleString()}</dd>
          </div>
        </dl>
      </section>

      <section className="shell-panel flex-1 px-5 py-0">
        <div className="flex gap-1 border-b border-shell-line">
          <button className={tabClass("schema")} type="button" onClick={() => setActiveTab("schema")}>
            Schema
          </button>
          <button className={tabClass("values")} type="button" onClick={() => setActiveTab("values")}>
            Values
          </button>
        </div>

        <div className="py-5">
          {activeTab === "schema" ? (
            <SharedStatePanel
              message="Schema view is coming soon — the captured node structure will be displayed here."
              state="empty"
              title="Schema not yet available."
            />
          ) : null}

          {activeTab === "values" ? renderValuesTab() : null}
        </div>
      </section>
    </div>
  );
}
