import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { StatusBadge, type StatusTone } from "./status-badge";

type ConfirmationTone = "warning" | "danger";

type ConfirmationImpact = {
  label: string;
  value: string;
};

type ConfirmationDialogProps = {
  open: boolean;
  tone?: ConfirmationTone;
  title: string;
  message: string;
  objectLabel?: string;
  impacts?: ConfirmationImpact[];
  reversibilityLabel: string;
  confirmLabel: string;
  cancelLabel?: string;
  isProcessing?: boolean;
  onConfirm: () => void;
  onClose: () => void;
};

function toneLabel(tone: ConfirmationTone) {
  return tone === "danger" ? "Cannot be undone" : "Confirmation required";
}

function toneBadge(tone: ConfirmationTone): StatusTone {
  return tone === "danger" ? "danger" : "warning";
}

export function ConfirmationDialog({
  open,
  tone = "warning",
  title,
  message,
  objectLabel,
  impacts = [],
  reversibilityLabel,
  confirmLabel,
  cancelLabel = "Cancel",
  isProcessing = false,
  onConfirm,
  onClose,
}: ConfirmationDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const cancelButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    cancelButtonRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isProcessing) {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isProcessing, onClose, open]);

  if (!open || typeof document === "undefined") {
    return null;
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      {!isProcessing ? (
        <button
          aria-label="Close confirmation dialog"
          className="absolute inset-0"
          type="button"
          onClick={onClose}
        />
      ) : null}

      <div
        aria-describedby={descriptionId}
        aria-labelledby={titleId}
        aria-modal="true"
        className="relative z-10 w-full max-w-2xl rounded-lg border border-shell-line bg-white shadow-panel"
        role="dialog"
      >
        <div className="border-b border-shell-line px-5 py-4">
          <StatusBadge label={toneLabel(tone)} tone={toneBadge(tone)} />
          <h2 id={titleId} className="mt-3 text-lg font-semibold text-shell-ink">
            {title}
          </h2>
          <p id={descriptionId} className="mt-2 text-sm leading-6 text-shell-muted">
            {message}
          </p>
        </div>

        <div className="px-5 py-4">
          <dl className="grid gap-3 sm:grid-cols-2">
            {objectLabel ? (
              <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3 sm:col-span-2">
                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Affected object
                </dt>
                <dd className="mt-2 text-sm font-medium text-shell-ink">{objectLabel}</dd>
              </div>
            ) : null}

            {impacts.map((impact) => (
              <div
                key={impact.label}
                className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3"
              >
                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  {impact.label}
                </dt>
                <dd className="mt-2 text-sm text-shell-ink">{impact.value}</dd>
              </div>
            ))}

            <div className="rounded-md border border-shell-line bg-shell-base/60 px-4 py-3 sm:col-span-2">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Reversibility
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{reversibilityLabel}</dd>
            </div>
          </dl>
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
          <button
            ref={cancelButtonRef}
            className="shell-action"
            disabled={isProcessing}
            type="button"
            onClick={onClose}
          >
            {cancelLabel}
          </button>
          <button
            className={tone === "danger" ? "shell-action-danger" : "shell-action-warning"}
            disabled={isProcessing}
            type="button"
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

export type { ConfirmationDialogProps, ConfirmationImpact, ConfirmationTone };
