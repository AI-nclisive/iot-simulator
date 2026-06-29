import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { type RecordingRow } from "./mock-recordings";

// ---------------------------------------------------------------------------
// Validation types
// ---------------------------------------------------------------------------

type ValidationOk = {
  status: "ok";
  artifact: RecordingRow;
};

type ValidationIncompatible = {
  status: "incompatible";
  reason: string;
};

type ValidationUnsupportedVersion = {
  status: "unsupported_version";
  version: string;
  maxSupported: string;
};

type ValidationResult =
  | ValidationOk
  | ValidationIncompatible
  | ValidationUnsupportedVersion;

// ---------------------------------------------------------------------------
// Mock validation — rotates outcomes based on file name
// ---------------------------------------------------------------------------

export function simulateImportValidation(file: File): Promise<ValidationResult> {
  return new Promise((resolve) => {
    setTimeout(() => {
      const name = file.name.toLowerCase();

      // Keyword checks take priority over the charCode rotation
      if (name.includes("version")) {
        resolve({
          status: "unsupported_version",
          version: "3.2.0",
          maxSupported: "2.9.1",
        });
        return;
      }

      if (name.includes("incompat") || name.charCodeAt(0) % 3 === 1) {
        resolve({
          status: "incompatible",
          reason:
            "Protocol mismatch: the artifact uses BACnet/IP which is not supported by any configured source in this project.",
        });
        return;
      }

      if (name.charCodeAt(0) % 3 === 2) {
        resolve({
          status: "unsupported_version",
          version: "3.2.0",
          maxSupported: "2.9.1",
        });
        return;
      }

      // Default: success with a mock artifact preview
      const estimatedValueCount = Math.round((file.size / 1024) || 512) * 20;
      const artifact: RecordingRow = {
        id: `imported-${Date.now()}`,
        origin: "imported",
        sourceId: "src-01",
        valueCount: estimatedValueCount,
        capturedAt: new Date().toISOString().slice(0, 16).replace("T", " "),
        capturedBy: "Import",
      };
      resolve({ status: "ok", artifact });
    }, 800);
  });
}

// ---------------------------------------------------------------------------
// Dialog steps
// ---------------------------------------------------------------------------

type Step = "select" | "validating" | "result" | "confirm";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type RecordingImportDialogProps = {
  open: boolean;
  canImport: boolean;
  onClose: () => void;
  onImported: (artifact: RecordingRow) => void;
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function RecordingImportDialog({
  open,
  canImport,
  onClose,
  onImported,
}: RecordingImportDialogProps) {
  const [step, setStep] = useState<Step>("select");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [validationResult, setValidationResult] =
    useState<ValidationResult | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  // Reset when dialog closes / reopens
  useEffect(() => {
    if (open) {
      setStep("select");
      setSelectedFile(null);
      setDragOver(false);
      setValidationResult(null);
    }
  }, [open]);

  // Keyboard dismiss
  useEffect(() => {
    if (!open) return;

    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && step !== "validating") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, step, onClose]);

  // Focus cancel button when dialog opens
  useEffect(() => {
    if (open) cancelRef.current?.focus();
  }, [open]);

  if (!open || typeof document === "undefined") return null;

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------

  function acceptFile(file: File) {
    const ext = file.name.split(".").pop()?.toLowerCase();
    if (ext !== "json" && ext !== "iotsim") return;
    setSelectedFile(file);
    setValidationResult(null);
    setStep("select");
  }

  async function handleNext() {
    if (!selectedFile) return;
    setStep("validating");
    const result = await simulateImportValidation(selectedFile);
    setValidationResult(result);
    setStep("result");
  }

  function handleImport() {
    if (!validationResult || validationResult.status !== "ok") return;
    onImported(validationResult.artifact);
  }

  function handleTryAnother() {
    setSelectedFile(null);
    setValidationResult(null);
    setStep("select");
  }

  // -------------------------------------------------------------------------
  // Render helpers
  // -------------------------------------------------------------------------

  function renderFileSelect() {
    return (
      <>
        <p className="mt-2 text-sm text-shell-muted">
          Select a <code className="text-xs">.json</code> or{" "}
          <code className="text-xs">.iotsim</code> artifact file to import into
          this project.
        </p>

        {/* Drag-drop zone */}
        <div
          aria-label="File drop zone"
          className={`mt-4 flex flex-col items-center justify-center gap-3 rounded-md border-2 border-dashed px-6 py-8 transition ${
            dragOver
              ? "border-shell-accent bg-shell-accent/5"
              : "border-shell-line bg-shell-base/40"
          }`}
          onDragLeave={(e) => {
            e.preventDefault();
            setDragOver(false);
          }}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            const file = e.dataTransfer.files?.[0];
            if (file) acceptFile(file);
          }}
        >
          {selectedFile ? (
            <div className="text-center">
              <p className="text-sm font-medium text-shell-ink">
                {selectedFile.name}
              </p>
              <p className="mt-1 text-xs text-shell-muted">
                {(selectedFile.size / 1024).toFixed(1)} KB
              </p>
            </div>
          ) : (
            <p className="text-sm text-shell-muted">
              Drag &amp; drop a file here, or{" "}
              <button
                className="text-shell-accent underline underline-offset-2"
                type="button"
                onClick={() => fileInputRef.current?.click()}
              >
                browse
              </button>
            </p>
          )}
          <input
            ref={fileInputRef}
            accept=".json,.iotsim"
            className="sr-only"
            data-testid="file-input"
            tabIndex={-1}
            type="file"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) acceptFile(file);
              e.target.value = "";
            }}
          />
        </div>

        {selectedFile ? (
          <button
            className="mt-3 text-xs text-shell-muted underline underline-offset-2"
            type="button"
            onClick={() => {
              setSelectedFile(null);
              setValidationResult(null);
            }}
          >
            Remove file
          </button>
        ) : null}

        <div className="mt-5 flex justify-end gap-2">
          <button
            ref={cancelRef}
            className="shell-action"
            type="button"
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={!selectedFile}
            type="button"
            onClick={handleNext}
          >
            Next
          </button>
        </div>
      </>
    );
  }

  function renderValidating() {
    return (
      <div className="mt-6 flex flex-col items-center gap-3 py-8">
        <div
          aria-label="Validating"
          className="h-8 w-8 animate-spin rounded-full border-4 border-shell-line border-t-shell-accent"
          role="status"
        />
        <p className="text-sm text-shell-muted">Validating artifact…</p>
      </div>
    );
  }

  function renderResult() {
    if (!validationResult) return null;

    if (validationResult.status === "incompatible") {
      return (
        <>
          <div
            className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4"
            role="alert"
          >
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Incompatible artifact
            </p>
            <p className="mt-2 text-sm text-shell-ink">
              {validationResult.reason}
            </p>
          </div>
          <p className="mt-3 text-xs text-shell-muted">
            This artifact cannot be imported in its current form. Fix the
            compatibility issue in the source system and try again.
          </p>
          <div className="mt-5 flex justify-end gap-2">
            <button
              className="shell-action"
              type="button"
              onClick={onClose}
            >
              Cancel
            </button>
            <button
              className="shell-action"
              type="button"
              onClick={handleTryAnother}
            >
              Try another file
            </button>
          </div>
        </>
      );
    }

    if (validationResult.status === "unsupported_version") {
      return (
        <>
          <div
            className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4"
            role="alert"
          >
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Unsupported artifact version
            </p>
            <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-shell-muted">Artifact version</dt>
                <dd className="mt-1 font-medium text-shell-ink">
                  {validationResult.version}
                </dd>
              </div>
              <div>
                <dt className="text-shell-muted">Max supported</dt>
                <dd className="mt-1 font-medium text-shell-ink">
                  {validationResult.maxSupported}
                </dd>
              </div>
            </dl>
          </div>
          <p className="mt-3 text-xs text-shell-muted">
            This artifact was created with a newer version of the IoT Simulator
            and cannot be imported. Upgrade the simulator or export the artifact
            in a compatible format.
          </p>
          <div className="mt-5 flex justify-end gap-2">
            <button
              className="shell-action"
              type="button"
              onClick={onClose}
            >
              Cancel
            </button>
            <button
              className="shell-action"
              type="button"
              onClick={handleTryAnother}
            >
              Try another file
            </button>
          </div>
        </>
      );
    }

    // status === "ok" — preview card
    const { artifact } = validationResult;
    return (
      <>
        <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Ready to import
          </p>
          <p className="mt-2 font-medium text-shell-ink">{artifact.id}</p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-shell-muted">Origin</dt>
              <dd className="mt-1 text-shell-ink capitalize">{artifact.origin}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Values</dt>
              <dd className="mt-1 text-shell-ink">{artifact.valueCount.toLocaleString()}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Captured by</dt>
              <dd className="mt-1 text-shell-ink">{artifact.capturedBy}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Captured at</dt>
              <dd className="mt-1 text-shell-ink">{artifact.capturedAt}</dd>
            </div>
          </dl>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            className="shell-action"
            type="button"
            onClick={handleTryAnother}
          >
            Back
          </button>
          <button
            className="shell-action"
            type="button"
            onClick={() => setStep("confirm")}
          >
            Import
          </button>
        </div>
      </>
    );
  }

  function renderConfirm() {
    if (!validationResult || validationResult.status !== "ok") return null;
    const { artifact } = validationResult;

    return (
      <>
        <p className="mt-2 text-sm text-shell-muted">
          Confirm that you want to add the following artifact to this project.
        </p>
        <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Summary
          </p>
          <p className="mt-2 font-medium text-shell-ink">{artifact.id}</p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-shell-muted">Origin</dt>
              <dd className="mt-1 text-shell-ink capitalize">{artifact.origin}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Values</dt>
              <dd className="mt-1 text-shell-ink">{artifact.valueCount.toLocaleString()}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Captured by</dt>
              <dd className="mt-1 text-shell-ink">{artifact.capturedBy}</dd>
            </div>
            <div>
              <dt className="text-shell-muted">Captured at</dt>
              <dd className="mt-1 text-shell-ink">{artifact.capturedAt}</dd>
            </div>
          </dl>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            className="shell-action"
            type="button"
            onClick={() => setStep("result")}
          >
            Back
          </button>
          <button
            className="shell-action"
            data-testid="confirm-import-btn"
            type="button"
            onClick={handleImport}
          >
            Confirm import
          </button>
        </div>
      </>
    );
  }

  function renderReadOnly() {
    return (
      <>
        <p className="mt-4 text-sm text-shell-muted">
          Importing artifacts is available to Admins only. Contact your project
          administrator to request access.
        </p>
        <div className="mt-5 flex justify-end">
          <button className="shell-action" type="button" onClick={onClose}>
            Close
          </button>
        </div>
      </>
    );
  }

  // -------------------------------------------------------------------------
  // Title per step
  // -------------------------------------------------------------------------

  const titles: Record<Step, string> = {
    select: "Import artifact — Select file",
    validating: "Import artifact — Validating",
    result: "Import artifact — Validation result",
    confirm: "Import artifact — Confirm",
  };

  // -------------------------------------------------------------------------
  // Portal render
  // -------------------------------------------------------------------------

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      {step !== "validating" ? (
        <button
          aria-label="Close import dialog"
          className="absolute inset-0"
          type="button"
          onClick={onClose}
        />
      ) : null}
      <section
        aria-label="Import artifact dialog"
        aria-modal="true"
        className="relative z-10 w-full max-w-lg rounded-lg border border-shell-line bg-white px-6 py-6 shadow-panel"
        role="dialog"
      >
        <h3 className="text-lg font-semibold text-shell-ink">
          {canImport ? titles[step] : "Import artifact"}
        </h3>

        {!canImport
          ? renderReadOnly()
          : step === "select"
            ? renderFileSelect()
            : step === "validating"
              ? renderValidating()
              : step === "result"
                ? renderResult()
                : renderConfirm()}
      </section>
    </div>,
    document.body,
  );
}
