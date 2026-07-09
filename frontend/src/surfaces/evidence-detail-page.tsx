import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { apiFetch, authHeaders } from "../api/client";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import {
  evidenceExportStateTone,
  evidenceStatusTone,
  isEvidenceExportAvailable,
} from "./evidence-detail-helpers";
import {
  evidenceCompletenessLabel,
  evidenceExportStateLabel,
  evidenceKindLabel,
  evidenceStatusLabel,
  evidenceTitle,
  mapEvidenceDto,
  type EvidenceExportStateLabel,
  type EvidenceItem,
  type EvidenceResponseDto,
  type EvidenceStatusLabel,
} from "./evidence-types";

function SummaryCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <p className="mt-3 text-sm font-medium text-shell-ink">{value}</p>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-3">
      <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </dt>
      <dd className="mt-2 text-sm text-shell-ink">{value ?? "—"}</dd>
    </div>
  );
}

function DetailLinkRow({ label, href, value }: { label: string; href: string; value: string }) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-3">
      <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </dt>
      <dd className="mt-2 text-sm">
        <Link className="shell-text-action" to={href}>{value}</Link>
      </dd>
    </div>
  );
}

function deriveDuration(startedAt: string, endedAt: string | null): string {
  if (!endedAt) return "Still running";
  const start = new Date(startedAt).getTime();
  const end = new Date(endedAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(end)) return "—";
  const diffSeconds = Math.round((end - start) / 1000);
  const minutes = Math.floor(diffSeconds / 60);
  const seconds = diffSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function EvidenceDetailPage() {
  const { evidenceId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  const [item, setItem] = useState<EvidenceItem | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  // Export state
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportOutcome, setExportOutcome] = useState<"success" | "failed" | null>(null);

  // Download state
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  useEffect(() => {
    if (!currentProjectId || !evidenceId) {
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setFetchError(null);

    apiFetch<EvidenceResponseDto>(
      `/api/v1/projects/${currentProjectId}/evidence/${evidenceId}`,
    )
      .then((dto) => {
        if (cancelled) return;
        setItem(mapEvidenceDto(dto));
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setFetchError(err instanceof Error ? err.message : "Failed to load evidence artifact");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [currentProjectId, evidenceId]);

  // Trigger export dialog from URL param
  useEffect(() => {
    if (searchParams.get("export") !== "1" || !access.isAdmin || !item) return;
    if (item.status === "CAPTURING") return;
    // Clear the param — the export UI is always visible on this page
    const next = new URLSearchParams(searchParams);
    next.delete("export");
    setSearchParams(next, { replace: true });
  }, [access.isAdmin, item, searchParams, setSearchParams]);

  if (isLoading) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Loading evidence artifact from the API."
            state="loading"
            title="Loading evidence…"
          />
        </section>
      </div>
    );
  }

  if (fetchError || !item) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message={fetchError ?? "Return to the Evidence list and choose a valid artifact."}
            state="error"
            title="This evidence artifact could not be found."
          />
          <div className="mt-4">
            <Link className="shell-text-action" to="/evidence">
              Back to evidence
            </Link>
          </div>
        </section>
      </div>
    );
  }

  const statusLbl: EvidenceStatusLabel =
    exportOutcome === "success"
      ? "Ready"
      : exportOutcome === "failed"
        ? "Export failed"
        : evidenceStatusLabel(item.status);

  const exportStateLbl: EvidenceExportStateLabel =
    exportOutcome === "success"
      ? "Exported"
      : exportOutcome === "failed"
        ? "Export failed"
        : evidenceExportStateLabel(item.exported, item.status);

  const exportAvailable = isEvidenceExportAvailable(statusLbl);
  const recoveryMode = exportStateLbl === "Export failed";
  const title = evidenceTitle(item.kind, item.runId);
  const duration = deriveDuration(item.startedAt, item.endedAt);

  function handleExport() {
    if (!currentProjectId || !evidenceId) return;
    setIsExporting(true);
    setExportError(null);

    apiFetch<unknown>(
      `/api/v1/projects/${currentProjectId}/evidence/${evidenceId}/export?format=BUNDLE`,
      { method: "POST" },
    )
      .then(() => {
        setExportOutcome("success");
      })
      .catch((err: unknown) => {
        setExportOutcome("failed");
        setExportError(err instanceof Error ? err.message : "Export failed");
      })
      .finally(() => {
        setIsExporting(false);
      });
  }

  function handleDownload() {
    if (!currentProjectId || !evidenceId) return;
    setIsDownloading(true);
    setDownloadError(null);

    fetch(
      `${import.meta.env.VITE_API_BASE_URL ?? ""}/api/v1/projects/${currentProjectId}/evidence/${evidenceId}/download`,
      {
        headers: authHeaders(),
      },
    )
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Download failed: ${response.statusText}`);
        }
        const blob = await response.blob();
        const contentDisposition = response.headers.get("Content-Disposition");
        let filename = `evidence-${evidenceId}.zip`;
        if (contentDisposition) {
          const match = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/.exec(contentDisposition);
          if (match?.[1]) {
            filename = match[1].replace(/['"]/g, "");
          }
        }
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
      })
      .catch((err: unknown) => {
        setDownloadError(err instanceof Error ? err.message : "Download failed");
      })
      .finally(() => {
        setIsDownloading(false);
      });
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-4xl">
            <Link className="shell-text-action -ml-2" to="/evidence">
              Back to evidence
            </Link>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">{title}</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              {evidenceCompletenessLabel(item.completeness)}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={statusLbl} tone={evidenceStatusTone(statusLbl)} />
            <StatusBadge
              label={exportStateLbl}
              tone={evidenceExportStateTone(exportStateLbl)}
            />
          </div>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <SummaryCard label="Kind" value={evidenceKindLabel(item.kind)} />
          <SummaryCard label="Initiator" value={item.initiator} />
          <SummaryCard label="Duration" value={duration} />
          <SummaryCard label="Created by" value={item.createdBy} />
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {access.isAdmin && exportAvailable ? (
            <button
              className={recoveryMode ? "shell-action-warning" : "shell-action"}
              disabled={isExporting}
              type="button"
              onClick={handleExport}
            >
              {isExporting
                ? "Exporting…"
                : recoveryMode
                  ? "Retry export"
                  : "Export evidence"}
            </button>
          ) : !access.isAdmin ? null : (
            <button className="shell-action" disabled type="button">
              Export not ready
            </button>
          )}

          {exportStateLbl === "Exported" || exportOutcome === "success" ? (
            <button
              className="shell-action"
              disabled={isDownloading}
              type="button"
              onClick={handleDownload}
            >
              {isDownloading ? "Downloading…" : "Download bundle"}
            </button>
          ) : null}
        </div>

        {exportError ? (
          <div className="mt-4 rounded-md border border-shell-danger/25 bg-shell-danger/10 px-4 py-3">
            <p className="text-sm font-medium text-shell-danger">Export failed.</p>
            <p className="mt-1 text-sm text-shell-muted">{exportError}</p>
          </div>
        ) : null}

        {exportOutcome === "success" && !exportError ? (
          <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
            <p className="text-sm font-medium text-shell-ink">Export queued successfully.</p>
            <p className="mt-1 text-sm text-shell-muted">
              The bundle is being assembled. Use the Download button to retrieve it.
            </p>
          </div>
        ) : null}

        {downloadError ? (
          <div className="mt-4 rounded-md border border-shell-danger/25 bg-shell-danger/10 px-4 py-3">
            <p className="text-sm font-medium text-shell-danger">Download failed.</p>
            <p className="mt-1 text-sm text-shell-muted">{downloadError}</p>
          </div>
        ) : null}
      </section>

      {recoveryMode && !exportOutcome ? (
        <section className="rounded-lg border border-shell-danger/25 bg-white px-5 py-5 shadow-panel">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0 max-w-3xl">
              <StatusBadge label="Export recovery" tone="danger" />
              <h3 className="mt-3 text-base font-semibold text-shell-ink">
                Export needs another pass
              </h3>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                The previous export failed before the artifact was written.
              </p>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Retry the export to re-queue bundle generation.
              </p>
            </div>
            {access.isAdmin ? (
              <button
                className="shell-action-warning"
                disabled={isExporting}
                type="button"
                onClick={handleExport}
              >
                {isExporting ? "Exporting…" : "Retry export"}
              </button>
            ) : null}
          </div>
        </section>
      ) : null}

      <div className="grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <section className="shell-panel px-5 py-5">
          <h3 className="text-base font-semibold text-shell-ink">Manifest</h3>
          <dl className="mt-5 grid gap-3">
            <DetailRow label="Kind" value={evidenceKindLabel(item.kind)} />
            <DetailRow label="Initiator" value={item.initiator} />
            <DetailRow label="Started at" value={item.startedAt} />
            <DetailRow label="Ended at" value={item.endedAt} />
            <DetailRow label="Completeness" value={evidenceCompletenessLabel(item.completeness)} />
            <DetailRow label="Run ID" value={item.runId} />
            {item.scenarioId ? (
              <DetailLinkRow label="Scenario" href={`/scenarios/${item.scenarioId}`} value={item.scenarioId} />
            ) : null}
            {item.recordingId ? (
              <DetailLinkRow label="Recording" href={`/recordings/${item.recordingId}`} value={item.recordingId} />
            ) : null}
          </dl>
        </section>

        <section className="shell-panel px-5 py-5">
          <h3 className="text-base font-semibold text-shell-ink">Sources</h3>
          {(item.sourceIds ?? []).length > 0 ? (
            <ul className="mt-5 space-y-3">
              {(item.sourceIds ?? []).map((sourceId) => (
                <li
                  key={sourceId}
                  className="rounded-md border border-shell-line bg-white px-4 py-3 text-sm text-shell-ink"
                >
                  <Link className="shell-text-action" to={`/data-sources/${sourceId}`}>{sourceId}</Link>
                </li>
              ))}
            </ul>
          ) : (
            <SharedStatePanel
              message="No source IDs are associated with this evidence artifact."
              state="empty"
              title="No sources."
            />
          )}

          <div className="mt-5 rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Bundle contents
            </p>
            <p className="mt-2 text-sm text-shell-muted">
              Full timeline, client delivery details, and value counts are available in the
              downloaded bundle.
            </p>
          </div>
        </section>
      </div>
    </div>
  );
}
