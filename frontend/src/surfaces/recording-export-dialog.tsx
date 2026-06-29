import { useEffect, useId, useState } from "react";
import { createPortal } from "react-dom";
import type { RecordingRow } from "./mock-recordings";

export type ExportFormat = "iotsim" | "json" | "csv";
type ExportPhase = "idle" | "exporting" | "done";

type RecordingExportDialogProps = {
  recording: RecordingRow;
  open: boolean;
  onClose: () => void;
};

export function RecordingExportDialog({
  recording,
  open,
  onClose,
}: RecordingExportDialogProps) {
  const titleId = useId();
  const descriptionId = useId();

  const [format, setFormat] = useState<ExportFormat>("iotsim");
  const [includeSchema, setIncludeSchema] = useState(true);
  const [phase, setPhase] = useState<ExportPhase>("idle");

  useEffect(() => {
    if (!open) return;
    setFormat("iotsim");
    setIncludeSchema(true);
    setPhase("idle");
  }, [open]);

  // CSV format cannot include schema
  useEffect(() => {
    if (format === "csv") {
      setIncludeSchema(false);
    }
  }, [format]);

  if (!open || typeof document === "undefined") return null;

  function handleExport() {
    setPhase("exporting");
    setTimeout(() => {
      setPhase("done");
    }, 800);
  }

  return createPortal(
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
    >
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
        className="relative z-10 w-full max-w-2xl rounded-lg border border-shell-line bg-white shadow-panel"
        role="dialog"
      >
        {/* Header */}
        <div className="border-b border-shell-line px-5 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-shell-ink">
            Export recording
          </h2>
          <p
            id={descriptionId}
            className="mt-2 text-sm leading-6 text-shell-muted"
          >
            Choose a format and scope for the exported artifact. Credential
            fields are always excluded from exports.
          </p>
        </div>

        <div className="space-y-5 px-5 py-4">
          {/* Artifact summary */}
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Artifact
              </p>
              <p className="mt-2 text-sm font-medium text-shell-ink">
                {recording.id}
              </p>
              <p className="mt-1 text-sm text-shell-muted">{recording.origin}</p>
              <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-shell-muted">
                <div>
                  <dt className="font-semibold uppercase tracking-wide">
                    Values
                  </dt>
                  <dd className="mt-1 text-shell-ink">{recording.valueCount.toLocaleString()}</dd>
                </div>
                <div>
                  <dt className="font-semibold uppercase tracking-wide">
                    Captured by
                  </dt>
                  <dd className="mt-1 text-shell-ink">{recording.capturedBy}</dd>
                </div>
                <div>
                  <dt className="font-semibold uppercase tracking-wide">
                    Captured at
                  </dt>
                  <dd className="mt-1 text-shell-ink">{recording.capturedAt}</dd>
                </div>
              </dl>
            </div>

            {/* Secret exclusion notice */}
            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Secret handling
              </p>
              <p className="mt-2 text-sm font-medium text-shell-ink">
                Excluded from export
              </p>
              <p
                className="mt-1 text-sm text-shell-muted"
                data-testid="secret-exclusion-notice"
              >
                Credential fields are always excluded from exports.
              </p>
            </div>
          </div>

          {/* Format selection */}
          <fieldset className="space-y-3 rounded-md border border-shell-line px-4 py-4">
            <legend className="px-1 text-sm font-medium text-shell-ink">
              Format
            </legend>
            {(
              [
                [
                  "iotsim",
                  "IoT Simulator package (.iotsim)",
                  "Full portable format, includes schema",
                ],
                ["json", "Raw values (JSON)", "Values only, no schema"],
                [
                  "csv",
                  "CSV summary",
                  "Tabular summary of parameter statistics",
                ],
              ] as [ExportFormat, string, string][]
            ).map(([value, label, description]) => (
              <label
                key={value}
                className="flex items-start gap-3 text-sm text-shell-muted"
              >
                <input
                  checked={format === value}
                  className="mt-1"
                  name="export-format"
                  type="radio"
                  value={value}
                  onChange={() => setFormat(value)}
                />
                <span>
                  <span className="font-medium text-shell-ink">{label}</span>
                  <span className="ml-2 text-xs">{description}</span>
                </span>
              </label>
            ))}
          </fieldset>

          {/* Scope options */}
          <fieldset className="space-y-3 rounded-md border border-shell-line px-4 py-4">
            <legend className="px-1 text-sm font-medium text-shell-ink">
              Scope
            </legend>
            <label className="flex items-start gap-3 text-sm text-shell-muted">
              <input
                checked={includeSchema}
                className="mt-1"
                disabled={format === "csv"}
                type="checkbox"
                onChange={(e) => setIncludeSchema(e.target.checked)}
              />
              <span>
                Include schema definition
                {format === "csv" ? (
                  <span className="ml-2 text-xs">(not available for CSV)</span>
                ) : null}
              </span>
            </label>
            <label className="flex items-start gap-3 text-sm text-shell-muted">
              <input
                checked
                className="mt-1"
                disabled
                type="checkbox"
              />
              <span>
                Exclude credential values
                <span className="ml-2 text-xs text-shell-muted">
                  (always enforced)
                </span>
              </span>
            </label>
          </fieldset>

          {/* Inline success message */}
          {phase === "done" ? (
            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3">
              <p className="text-sm font-medium text-shell-ink">
                Export ready — download would start here.
              </p>
              <p className="mt-1 text-sm text-shell-muted">
                Secret values were excluded from the artifact.
              </p>
            </div>
          ) : null}
        </div>

        {/* Actions */}
        <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
          <button
            autoFocus
            className="shell-action"
            type="button"
            onClick={onClose}
          >
            {phase === "done" ? "Close" : "Cancel"}
          </button>
          {phase !== "done" ? (
            <button
              className="shell-action"
              disabled={phase === "exporting"}
              type="button"
              onClick={handleExport}
            >
              {phase === "exporting" ? "Preparing…" : "Export"}
            </button>
          ) : null}
        </div>
      </div>
    </div>,
    document.body,
  );
}
