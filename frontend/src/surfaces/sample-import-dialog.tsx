import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useArtifactsStore, type SampleResponse } from "../shell/artifacts-store";

type Step = "select" | "validating" | "confirm";

type SampleImportDialogProps = {
  open: boolean;
  canImport: boolean;
  existingNames: string[];
  projectId: string;
  onClose: () => void;
  onImported: (sample: SampleResponse) => void;
};

export function stripExtension(filename: string): string {
  return filename.replace(/\.[^.]+$/, "");
}

export function SampleImportDialog({
  open,
  canImport,
  existingNames,
  projectId,
  onClose,
  onImported,
}: SampleImportDialogProps) {
  const [step, setStep] = useState<Step>("select");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [sampleName, setSampleName] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [dropError, setDropError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      setStep("select");
      setSelectedFile(null);
      setDragOver(false);
      setSampleName("");
      setNameError(null);
      setDropError(null);
      setConfirmError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && step !== "validating") onClose();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, step, onClose]);

  useEffect(() => {
    if (open) cancelRef.current?.focus();
  }, [open]);

  if (!open || typeof document === "undefined") return null;

  function acceptFile(file: File) {
    const ext = file.name.split(".").pop()?.toLowerCase();
    if (ext !== "json" && ext !== "iotsim") {
      setDropError("Unsupported file type — use .json or .iotsim.");
      return;
    }
    setDropError(null);
    setSelectedFile(file);
    const derived = stripExtension(file.name);
    setSampleName(derived);
    setNameError(null);
  }

  function validateName(name: string): string | null {
    const trimmed = name.trim();
    if (!trimmed) return "Name is required.";
    if (existingNames.some((n) => n.toLowerCase() === trimmed.toLowerCase())) {
      return `A sample named "${trimmed}" already exists. Choose a different name.`;
    }
    return null;
  }

  async function handleNext() {
    if (!selectedFile) return;
    const err = validateName(sampleName);
    if (err) { setNameError(err); return; }
    setStep("validating");
    try {
      if (selectedFile.name.toLowerCase().endsWith(".json")) {
        const text = await selectedFile.text();
        JSON.parse(text);
      }
      setStep("confirm");
    } catch {
      setStep("select");
      setNameError("File does not contain valid JSON.");
    }
  }

  async function handleConfirm() {
    const trimmed = sampleName.trim();
    setConfirmError(null);
    setStep("validating");
    try {
      const sample = await useArtifactsStore.getState().createSample(projectId, {
        name: trimmed,
        derivedFromRecordingId: "",
        selection: "full",
        tags: [],
      });
      onImported(sample);
    } catch {
      setConfirmError("Failed to save sample. Please try again.");
      setStep("confirm");
    }
  }

  function renderReadOnly() {
    return (
      <>
        <p className="mt-4 text-sm text-shell-muted">
          Importing samples is available to Admins only.
        </p>
        <div className="mt-5 flex justify-end">
          <button className="shell-action" type="button" onClick={onClose}>Close</button>
        </div>
      </>
    );
  }

  function renderSelect() {
    return (
      <>
        <p className="mt-2 text-sm text-shell-muted">
          Select a <code className="text-xs">.json</code> or{" "}
          <code className="text-xs">.iotsim</code> file to import as a sample.
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
            accept=".json,.iotsim"
            className="sr-only"
            tabIndex={-1}
            type="file"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) acceptFile(file);
              e.target.value = "";
            }}
          />
        </div>

        {dropError ? (
          <p className="mt-2 text-xs text-red-600" role="alert">{dropError}</p>
        ) : null}

        {selectedFile ? (
          <button
            className="mt-2 text-xs text-shell-muted underline underline-offset-2"
            type="button"
            onClick={() => { setSelectedFile(null); setSampleName(""); setNameError(null); setDropError(null); }}
          >
            Remove file
          </button>
        ) : null}

        {selectedFile ? (
          <div className="mt-4">
            <label className="block text-sm font-medium text-shell-ink">
              Sample name
              <input
                autoFocus
                className={`shell-field mt-1 w-full ${nameError ? "border-red-400 focus-visible:ring-red-300/40" : ""}`}
                placeholder="Enter a name for this sample"
                type="text"
                value={sampleName}
                onChange={(e) => {
                  setSampleName(e.target.value);
                  if (nameError) setNameError(validateName(e.target.value));
                }}
              />
            </label>
            {nameError ? (
              <p className="mt-1 text-xs text-red-600" role="alert">{nameError}</p>
            ) : null}
          </div>
        ) : null}

        <div className="mt-5 flex justify-end gap-2">
          <button ref={cancelRef} className="shell-action" type="button" onClick={onClose}>
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={!selectedFile}
            type="button"
            onClick={() => void handleNext()}
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
        <p className="text-sm text-shell-muted">Validating file…</p>
      </div>
    );
  }

  function renderConfirm() {
    return (
      <>
        <p className="mt-2 text-sm text-shell-muted">
          Confirm the details below before adding this sample to the project.
        </p>
        <div className="mt-4 rounded-md border border-shell-line bg-shell-base/60 px-4 py-4 space-y-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">Name</p>
            <p className="mt-1 font-medium text-shell-ink">{sampleName.trim()}</p>
          </div>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-shell-muted">File</p>
              <p className="mt-1 text-shell-ink truncate">{selectedFile?.name}</p>
            </div>
            <div>
              <p className="text-shell-muted">Size</p>
              <p className="mt-1 text-shell-ink">
                {selectedFile ? `${(selectedFile.size / 1024).toFixed(1)} KB` : "—"}
              </p>
            </div>
          </div>
        </div>
        {confirmError ? (
          <p className="mt-2 text-xs text-red-600" role="alert">{confirmError}</p>
        ) : null}
        <div className="mt-5 flex justify-end gap-2">
          <button
            className="shell-action"
            type="button"
            onClick={() => setStep("select")}
          >
            Back
          </button>
          <button
            className="shell-action"
            type="button"
            onClick={() => void handleConfirm()}
          >
            Add sample
          </button>
        </div>
      </>
    );
  }

  const titles: Record<Step, string> = {
    select: "Add sample — Select file",
    validating: "Add sample — Validating",
    confirm: "Add sample — Confirm",
  };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
      {step !== "validating" ? (
        <button
          aria-label="Close dialog"
          className="absolute inset-0"
          type="button"
          onClick={onClose}
        />
      ) : null}
      <section
        aria-label="Add sample dialog"
        aria-modal="true"
        className="relative z-10 w-full max-w-lg rounded-lg border border-shell-line bg-white px-6 py-6 shadow-panel"
        role="dialog"
      >
        <h3 className="text-lg font-semibold text-shell-ink">
          {canImport ? titles[step] : "Add sample"}
        </h3>

        {!canImport
          ? renderReadOnly()
          : step === "select"
            ? renderSelect()
            : step === "validating"
              ? renderValidating()
              : renderConfirm()}
      </section>
    </div>,
    document.body,
  );
}
