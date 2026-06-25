import { useEffect, useId, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import {
  evidenceDeliveryTone,
  evidenceExportStateTone,
  evidenceIssueTone,
  evidenceStatusTone,
  evidenceTimelineTone,
  isEvidenceExportAvailable,
} from "./evidence-detail-helpers";
import { evidenceArtifacts, type EvidenceArtifact, type EvidenceFormat } from "./mock-evidence";

type ExportDialogProps = {
  evidence: EvidenceArtifact;
  open: boolean;
  onClose: () => void;
  onExportComplete: (result: "success" | "failed", format: EvidenceFormat) => void;
};

function ExportEvidenceDialog({ evidence, open, onClose, onExportComplete }: ExportDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const [format, setFormat] = useState<EvidenceFormat>(evidence.formats[0]);
  const [includeSummary, setIncludeSummary] = useState(true);
  const [includeTimeline, setIncludeTimeline] = useState(true);
  const [includeClients, setIncludeClients] = useState(true);
  const [includeIssues, setIncludeIssues] = useState(evidence.issues.length > 0);

  useEffect(() => {
    if (!open) return;
    setFormat(evidence.formats[0]);
    setIncludeSummary(true);
    setIncludeTimeline(true);
    setIncludeClients(true);
    setIncludeIssues(evidence.issues.length > 0);
  }, [evidence.formats, evidence.issues.length, open]);

  if (!open || typeof document === "undefined") return null;

  const selectedScope = [
    includeSummary ? "Summary" : null,
    includeTimeline ? "Timeline" : null,
    includeClients ? "Clients" : null,
    includeIssues ? "Faults and errors" : null,
  ].filter(Boolean);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      <button
        aria-label="Close export dialog"
        className="absolute inset-0"
        type="button"
        onClick={onClose}
      />

      <div
        aria-describedby={descriptionId}
        aria-labelledby={titleId}
        aria-modal="true"
        className="relative z-10 w-full max-w-3xl rounded-lg border border-shell-line bg-white shadow-panel"
        role="dialog"
      >
        <div className="border-b border-shell-line px-5 py-4">
          <StatusBadge label="Export evidence" tone="accent" />
          <h2 id={titleId} className="mt-3 text-lg font-semibold text-shell-ink">
            Export evidence
          </h2>
          <p id={descriptionId} className="mt-2 text-sm leading-6 text-shell-muted">
            Choose the artifact format and scope. Credential material, passwords, and secret
            values are excluded from every export.
          </p>
        </div>

        <div className="space-y-5 px-5 py-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Artifact
              </p>
              <p className="mt-2 text-sm font-medium text-shell-ink">{evidence.title}</p>
              <p className="mt-1 text-sm text-shell-muted">
                {evidence.runType} · {evidence.runId}
              </p>
            </div>

            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Secret handling
              </p>
              <p className="mt-2 text-sm font-medium text-shell-ink">Excluded from export</p>
              <p className="mt-1 text-sm text-shell-muted">
                Credentials and secret references stay out of files.
              </p>
            </div>
          </div>

          <label className="flex flex-col gap-2 text-sm text-shell-muted">
            Format
            <select
              className="shell-field"
              value={format}
              onChange={(event) => setFormat(event.target.value as EvidenceFormat)}
            >
              {evidence.formats.map((availableFormat) => (
                <option key={availableFormat} value={availableFormat}>
                  {availableFormat}
                </option>
              ))}
            </select>
          </label>

          <fieldset className="space-y-3 rounded-md border border-shell-line px-4 py-4">
            <legend className="px-1 text-sm font-medium text-shell-ink">Artifact scope</legend>
            {(
              [
                ["Summary", includeSummary, setIncludeSummary],
                ["Timeline", includeTimeline, setIncludeTimeline],
                ["Clients", includeClients, setIncludeClients],
                ["Faults and errors", includeIssues, setIncludeIssues],
              ] as [string, boolean, (v: boolean) => void][]
            ).map(([label, checked, setChecked]) => (
              <label key={label} className="flex items-start gap-3 text-sm text-shell-muted">
                <input
                  className="mt-1"
                  checked={checked}
                  type="checkbox"
                  onChange={(event) => setChecked(event.target.checked)}
                />
                <span>{label}</span>
              </label>
            ))}
          </fieldset>

          <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Export summary
            </p>
            <p className="mt-2 text-sm text-shell-ink">
              {format} ·{" "}
              {selectedScope.length > 0 ? selectedScope.join(", ") : "No sections selected"}
            </p>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
          <button className="shell-action" type="button" onClick={onClose}>
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={selectedScope.length === 0}
            type="button"
            onClick={() => onExportComplete("success", format)}
          >
            Start export
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

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

export function EvidenceDetailPage() {
  const { evidenceId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);
  const evidence = evidenceArtifacts.find((a) => a.id === evidenceId);
  const [exportDialogOpen, setExportDialogOpen] = useState(false);
  const [exportedFormat, setExportedFormat] = useState<EvidenceFormat | null>(null);
  const [exportOutcome, setExportOutcome] = useState<"success" | "failed" | null>(null);

  const exportAvailable = evidence ? isEvidenceExportAvailable(evidence) : false;
  const currentExportState = exportOutcome === "success"
    ? "Exported"
    : exportOutcome === "failed"
      ? "Export failed"
      : evidence?.exportState;

  useEffect(() => {
    if (searchParams.get("export") !== "1" || !access.isAdmin || !exportAvailable) return;
    setExportDialogOpen(true);
  }, [access.isAdmin, exportAvailable, searchParams]);

  if (!evidence || !currentExportState) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Return to the Evidence list and choose a valid artifact."
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

  function closeExportDialog() {
    setExportDialogOpen(false);
    const next = new URLSearchParams(searchParams);
    next.delete("export");
    setSearchParams(next, { replace: true });
  }

  function handleExportComplete(result: "success" | "failed", format: EvidenceFormat) {
    setExportedFormat(format);
    setExportOutcome(result);
    setExportDialogOpen(false);
    const next = new URLSearchParams(searchParams);
    next.delete("export");
    setSearchParams(next, { replace: true });
  }

  const effectiveStatus = exportOutcome === "success"
    ? ("Exported" as const)
    : exportOutcome === "failed"
      ? ("Export failed" as const)
      : evidence.status;

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-4xl">
            <Link className="shell-text-action -ml-2" to="/evidence">
              Back to evidence
            </Link>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">{evidence.title}</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">{evidence.completeness}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={effectiveStatus} tone={evidenceStatusTone(effectiveStatus)} />
            <StatusBadge
              label={currentExportState}
              tone={evidenceExportStateTone(currentExportState)}
            />
          </div>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <SummaryCard label="Run origin" value={`${evidence.runType} · ${evidence.runId}`} />
          <SummaryCard label="Initiator" value={evidence.initiator} />
          <SummaryCard label="Duration" value={evidence.duration} />
          <SummaryCard label="Values" value={evidence.valueCount.toLocaleString()} />
          <SummaryCard label="Size" value={evidence.sizeLabel} />
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          <button
            className="shell-action"
            disabled={!access.isAdmin || !exportAvailable}
            type="button"
            onClick={() => setExportDialogOpen(true)}
          >
            {exportAvailable ? "Export evidence" : "Export not ready"}
          </button>
          {evidence.sourcePath ? (
            <Link className="shell-text-action" to={evidence.sourcePath}>
              Open source
            </Link>
          ) : null}
          {evidence.scenarioName ? (
            <Link className="shell-text-action" to="/scenarios">
              Open scenario
            </Link>
          ) : null}
        </div>

        {exportedFormat ? (
          <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
            <p className="text-sm font-medium text-shell-ink">
              Last export: {exportedFormat}
            </p>
            <p className="mt-1 text-sm text-shell-muted">
              {exportOutcome === "success"
                ? "Export completed. Secret values were excluded from the artifact."
                : "Export failed. Adjust the scope or format and retry."}
            </p>
          </div>
        ) : null}
      </section>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Timeline</h3>
            <StatusBadge label={evidence.completedAt ?? "Still capturing"} />
          </div>

          <ol className="mt-5 space-y-3">
            {evidence.timeline.map((event) => (
              <li
                key={event.id}
                className="rounded-md border border-shell-line bg-white px-4 py-4"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge
                    label={event.time}
                    tone={evidenceTimelineTone(event.tone)}
                  />
                  <p className="text-sm font-medium text-shell-ink">{event.title}</p>
                </div>
                <p className="mt-2 text-sm leading-6 text-shell-muted">{event.description}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="shell-panel px-5 py-5">
          <h3 className="text-base font-semibold text-shell-ink">Origin</h3>
          <dl className="mt-5 grid gap-3">
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Project
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{evidence.projectName}</dd>
            </div>
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Source or scenario
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">
                {evidence.scenarioName ?? evidence.sourceName}
              </dd>
            </div>
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Export formats
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{evidence.formats.join(", ")}</dd>
            </div>
          </dl>
        </section>
      </div>

      <div className="grid gap-3 xl:grid-cols-2">
        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Clients</h3>
            <StatusBadge
              label={`${evidence.clientCount} client${evidence.clientCount === 1 ? "" : "s"}`}
            />
          </div>

          {evidence.clients.length > 0 ? (
            <div className="mt-5 space-y-3">
              {evidence.clients.map((client) => (
                <div
                  key={client.id}
                  className="rounded-md border border-shell-line bg-white px-4 py-4"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium text-shell-ink">{client.name}</p>
                    <StatusBadge
                      label={client.delivery}
                      tone={evidenceDeliveryTone(client.delivery)}
                    />
                  </div>
                  <p className="mt-2 text-sm text-shell-muted">{client.protocol}</p>
                </div>
              ))}
            </div>
          ) : (
            <SharedStatePanel
              message="No client delivery was captured for this evidence artifact."
              state={
                evidence.status === "Partial" || evidence.status === "Export failed"
                  ? "warning"
                  : "empty"
              }
              title="No clients captured."
            />
          )}
        </section>

        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Faults and errors</h3>
            <StatusBadge
              label={`${evidence.issues.length} issue${evidence.issues.length === 1 ? "" : "s"}`}
            />
          </div>

          {evidence.issues.length > 0 ? (
            <div className="mt-5 space-y-3">
              {evidence.issues.map((issue) => (
                <div
                  key={issue.id}
                  className="rounded-md border border-shell-line bg-white px-4 py-4"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge
                      label={issue.severity}
                      tone={evidenceIssueTone(issue.severity)}
                    />
                    <p className="text-sm font-medium text-shell-ink">{issue.label}</p>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-shell-muted">{issue.description}</p>
                </div>
              ))}
            </div>
          ) : (
            <SharedStatePanel
              message="No faults or errors are attached to this evidence artifact."
              state="empty"
              title="No issues captured."
            />
          )}
        </section>
      </div>

      <ExportEvidenceDialog
        evidence={evidence}
        open={exportDialogOpen}
        onClose={closeExportDialog}
        onExportComplete={handleExportComplete}
      />
    </div>
  );
}
