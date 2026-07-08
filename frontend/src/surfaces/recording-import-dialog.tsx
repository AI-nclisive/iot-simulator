import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { apiFetch, ApiError } from "../api";

// ── API shape ─────────────────────────────────────────────────────────────────

type ImportedRecordingResponse = {
  id: string;
  name: string | null;
  valueCount: number;
  origin: string;
};

// ── Steps ────────────────────────────────────────────────────────────────────

type Step = "select" | "uploading" | "done" | "error";

// ── Props ─────────────────────────────────────────────────────────────────────

type RecordingImportDialogProps = {
  open: boolean;
  projectId: string;
  canImport: boolean;
  onClose: () => void;
  onImported: () => void;
};

// ── Component ─────────────────────────────────────────────────────────────────

export function RecordingImportDialog({
  open,
  projectId,
  canImport,
  onClose,
  onImported,
}: RecordingImportDialogProps) {
  const [step, setStep] = useState<Step>("select");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [importedRecording, setImportedRecording] = useState<ImportedRecordingResponse | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      setStep("select");
      setSelectedFile(null);
      setDragOver(false);
      setImportedRecording(null);
      setUploadError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && step !== "uploading") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, step, onClose]);

  useEffect(() => {
    if (open) cancelRef.current?.focus();
  }, [open]);

  if (!open || typeof document === "undefined") return null;

  // ── Handlers ─────────────────────────────────────────────────────────────

  function acceptFile(file: File) {
    const ext = file.name.split(".").pop()?.toLowerCase();
    if (ext !== "iotsim") return;
    setSelectedFile(file);
  }

  async function handleUpload() {
    if (!selectedFile) return;
    setStep("uploading");
    const formData = new FormData();
    formData.append("file", selectedFile);
    try {
      const recording = await apiFetch<ImportedRecordingResponse>(
        `/api/v1/projects/${projectId}/recordings/import`,
        { method: "POST", body: formData },
      );
      setImportedRecording(recording);
      setStep("done");
      onImported();
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.title : "Import failed. Check the file and try again.");
      setStep("error");
    }
  }

  function handleTryAnother() {
    setSelectedFile(null);
    setUploadError(null);
    setStep("select");
  }

  // ── Render helpers ────────────────────────────────────────────────────────

  function renderFileSelect() {
    return (
      <>
        <p className="mt-2 text-sm text-shell-muted">
          Select a <code className="text-xs">.iotsim</code> recording bundle to import into this project.
        </p>

        <div
          aria-label="File drop zone"
          className={`mt-4 flex flex-col items-center justify-center gap-3 rounded-md border-2 border-dashed px-6 py-8 transition ${
            dragOver
              ? "border-shell-accent bg-shell-accent/5"
              : "border-shell-line bg-shell-base/40"
          }`}
          onDragLeave={(e) => { e.preventDefault(); setDragOver(false); }}
          onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            const file = e.dataTransfer.files?.[0];
            if (file) acceptFile(file);
          }}
        >
          {selectedFile ? (
            <div className="text-center">
              <p className="text-sm font-medium text-shell-ink">{selectedFile.name}</p>
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
            accept=".iotsim"
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
            onClick={() => setSelectedFile(null)}
          >
            Remove file
          </button>
        ) : null}

        <div className="mt-5 flex justify-end gap-2">
          <button ref={cancelRef} className="shell-action" type="button" onClick={onClose}>
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={!selectedFile}
            type="button"
            onClick={() => void handleUpload()}
          >
            Import
          </button>
        </div>
      </>
    );
  }

  function renderUploading() {
    return (
      <div className="mt-6 flex flex-col items-center gap-3 py-8">
        <div
          aria-label="Uploading"
          className="h-8 w-8 animate-spin rounded-full border-4 border-shell-line border-t-shell-accent"
          role="status"
        />
        <p className="text-sm text-shell-muted">Importing recording…</p>
      </div>
    );
  }

  function renderDone() {
    const label = importedRecording?.name ?? importedRecording?.id?.slice(0, 8) ?? "Recording";
    const count = importedRecording?.valueCount ?? 0;
    return (
      <>
        <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Imported successfully
          </p>
          <p className="mt-2 font-medium text-shell-ink">{label}</p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-shell-muted">Origin</dt>
              <dd className="mt-1 text-shell-ink capitalize">
                {importedRecording?.origin ?? "imported"}
              </dd>
            </div>
            <div>
              <dt className="text-shell-muted">Values</dt>
              <dd className="mt-1 text-shell-ink">{count.toLocaleString()}</dd>
            </div>
          </dl>
        </div>
        <div className="mt-5 flex justify-end">
          <button className="shell-action" type="button" onClick={onClose}>
            Done
          </button>
        </div>
      </>
    );
  }

  function renderError() {
    return (
      <>
        <div
          className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4"
          role="alert"
        >
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Import failed
          </p>
          <p className="mt-2 text-sm text-shell-ink">{uploadError}</p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button className="shell-action" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="shell-action" type="button" onClick={handleTryAnother}>
            Try another file
          </button>
        </div>
      </>
    );
  }

  function renderReadOnly() {
    return (
      <>
        <p className="mt-4 text-sm text-shell-muted">
          Importing recordings is available to Admins only. Contact your project
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

  const titles: Record<Step, string> = {
    select: "Import recording",
    uploading: "Import recording — Uploading",
    done: "Import recording — Done",
    error: "Import recording — Failed",
  };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      {step !== "uploading" ? (
        <button
          aria-label="Close import dialog"
          className="absolute inset-0"
          type="button"
          onClick={onClose}
        />
      ) : null}
      <section
        aria-label="Import recording dialog"
        aria-modal="true"
        className="relative z-10 w-full max-w-lg rounded-lg border border-shell-line bg-white px-6 py-6 shadow-panel"
        role="dialog"
      >
        <h3 className="text-lg font-semibold text-shell-ink">
          {canImport ? titles[step] : "Import recording"}
        </h3>

        {!canImport
          ? renderReadOnly()
          : step === "select"
            ? renderFileSelect()
            : step === "uploading"
              ? renderUploading()
              : step === "done"
                ? renderDone()
                : renderError()}
      </section>
    </div>,
    document.body,
  );
}
