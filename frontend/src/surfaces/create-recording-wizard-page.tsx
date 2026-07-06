import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useNotificationStore } from "../shell/notification-store";
import { apiFetch, ApiError } from "../api";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type RecordingStepId = "source" | "scan-type" | "review";
type RecordingStep = { id: RecordingStepId; label: string };

const STEPS: RecordingStep[] = [
  { id: "source", label: "Data source" },
  { id: "scan-type", label: "Scan type" },
  { id: "review", label: "Review" },
];

type ScanType = "SCHEMA_AND_DATA" | "SCHEMA_ONLY";

type RecordingWizardForm = {
  dataSourceId: string | null;
  scanType: ScanType;
  name: string;
};

function stepChipClass(current: boolean, completed: boolean) {
  if (current) return "border-shell-accent bg-white text-shell-ink";
  if (completed) return "border-shell-line bg-white text-shell-ink";
  return "border-shell-line bg-shell-base/55 text-shell-muted";
}

function optionButtonClass(selected: boolean) {
  return `w-full rounded-md border px-4 py-4 text-left transition ${
    selected
      ? "border-shell-accent bg-white shadow-sm"
      : "border-shell-line bg-shell-base/55 hover:border-shell-accent/45 hover:bg-white"
  }`;
}

function stepValidation(form: RecordingWizardForm, stepId: RecordingStepId): string | null {
  if (stepId === "source" && !form.dataSourceId) {
    return "Select a data source to continue.";
  }
  return null;
}

export function CreateRecordingWizardPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((s) => s.accessMode);
  const sharedRole = useShellStore((s) => s.sharedRole);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const push = useNotificationStore((s) => s.push);
  const access = resolveAccess(accessMode, sharedRole);
  const dataSources = useDataSourcesStore((s) => s.dataSources);

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [form, setForm] = useState<RecordingWizardForm>({
    dataSourceId: null,
    scanType: "SCHEMA_AND_DATA",
    name: "",
  });

  const safeStep = Math.min(currentStep, STEPS.length - 1);
  const activeStepId = STEPS[safeStep].id;
  const currentValidationMessage = stepValidation(form, activeStepId);
  const selectedSource = dataSources.find((ds) => ds.id === form.dataSourceId) ?? null;
  const captureBlocked = form.scanType === "SCHEMA_AND_DATA" && !selectedSource?.endpoint;

  if (!access.canCreateSource) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Creating a recording is restricted in the current role."
            state="locked"
            title="Recording creation is not available."
          />
        </section>
      </div>
    );
  }

  function goNext() {
    if (currentValidationMessage) {
      setShowValidation(true);
      return;
    }
    setShowValidation(false);
    setCurrentStep((s) => Math.min(s + 1, STEPS.length - 1));
  }

  function goBack() {
    setShowValidation(false);
    setCurrentStep((s) => Math.max(s - 1, 0));
  }

  async function createRecording() {
    if (!form.dataSourceId || !currentProjectId) return;

    // SCHEMA_AND_DATA: real live capture — navigate to RecordingFlowPage which
    // handles POST .../recording/start + stop and saves the recording.
    if (form.scanType === "SCHEMA_AND_DATA") {
      navigate(`/data-sources/${form.dataSourceId}/recording`);
      return;
    }

    // SCHEMA_ONLY: create a shell row — no live capture needed.
    try {
      const trimmedName = form.name.trim();
      const result = await apiFetch<{ id: string }>(
        `/api/v1/projects/${currentProjectId}/recordings`,
        {
          method: "POST",
          body: JSON.stringify({
            dataSourceId: form.dataSourceId,
            scanType: form.scanType,
            ...(trimmedName ? { name: trimmedName } : {}),
          }),
        },
      );
      push({ tone: "success", title: "Recording created." });
      navigate(`/recordings/${result.id}`);
    } catch (err) {
      const title = err instanceof ApiError ? err.title : (err instanceof Error ? err.message : "Failed to create recording");
      push({ tone: "error", title });
    }
  }

  function renderSourceStep() {
    const allSources = dataSources;
    if (allSources.length === 0) {
      return (
        <SharedStatePanel
          message="No data sources found in this project. Create a data source first, then return here to create a recording."
          state="empty"
          title="No data sources available."
        />
      );
    }
    return (
      <div className="space-y-4">
        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-sm font-medium text-shell-ink">Select a data source</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            The recording will capture data from the selected source.
          </p>
        </section>
        <div className="space-y-2">
          {allSources.map((ds) => {
            const isSelected = form.dataSourceId === ds.id;
            return (
              <button
                key={ds.id}
                className={optionButtonClass(isSelected)}
                type="button"
                onClick={() => setForm((f) => ({ ...f, dataSourceId: ds.id }))}
              >
                <div className="flex flex-wrap items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-shell-ink">{ds.name}</p>
                    {ds.endpoint ? (
                      <p className="mt-1 text-xs text-shell-muted font-mono">{ds.endpoint}</p>
                    ) : null}
                  </div>
                  <div className="flex flex-wrap gap-1 shrink-0">
                    <StatusBadge label={ds.protocol} tone="neutral" />
                    <StatusBadge
                      label={ds.status}
                      tone={ds.status === "Active" ? "accent" : "neutral"}
                    />
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  function renderScanTypeStep() {
    return (
      <div className="space-y-3">
        <button
          className={optionButtonClass(form.scanType === "SCHEMA_AND_DATA")}
          type="button"
          onClick={() => setForm((f) => ({ ...f, scanType: "SCHEMA_AND_DATA" }))}
        >
          <p className="text-sm font-medium text-shell-ink">Schema + data</p>
          <p className="mt-1 text-sm text-shell-muted">
            Opens the live capture flow — connects to the device, streams values, and saves the
            recording when you stop. Use this for replay and analysis.
          </p>
        </button>
        <button
          className={optionButtonClass(form.scanType === "SCHEMA_ONLY")}
          type="button"
          onClick={() => setForm((f) => ({ ...f, scanType: "SCHEMA_ONLY" }))}
        >
          <p className="text-sm font-medium text-shell-ink">Schema only</p>
          <p className="mt-1 text-sm text-shell-muted">
            Creates a schema-only recording shell — no value capture. Use this to inspect the device
            structure without live data.
          </p>
        </button>
        {captureBlocked ? (
          <SharedStatePanel
            message="The selected source has no real device endpoint. Schema + data capture requires a source configured with a real OPC UA or Modbus TCP address. Switch to Schema only, or go back and select a different source."
            state="warning"
            title="Live capture not available for this source."
          />
        ) : null}
      </div>
    );
  }

  function renderReviewStep() {
    if (!selectedSource) return null;
    const scanTypeLabel = form.scanType === "SCHEMA_ONLY" ? "Schema only" : "Schema + data";
    const liveCapture = form.scanType === "SCHEMA_AND_DATA";
    const items = [
      { label: "Data source", value: selectedSource.name },
      { label: "Protocol", value: selectedSource.protocol },
      { label: "Status", value: selectedSource.status },
      { label: "Scan type", value: scanTypeLabel },
      ...(selectedSource.endpoint ? [{ label: "Endpoint", value: selectedSource.endpoint }] : []),
    ];
    return (
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-shell-ink" htmlFor="recording-name">
            Recording name
            <span className="ml-1 text-xs text-shell-muted">(optional)</span>
          </label>
          <input
            className="shell-field mt-2 w-full max-w-sm"
            id="recording-name"
            maxLength={255}
            placeholder="e.g. Pump A baseline scan"
            type="text"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((item) => (
            <div key={item.label} className="rounded-md border border-shell-line bg-white px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                {item.label}
              </p>
              <p className="mt-2 text-sm text-shell-ink">{item.value}</p>
            </div>
          ))}
        </div>
        {captureBlocked ? (
          <SharedStatePanel
            message="Schema + data capture requires a real device endpoint. Go back and select Schema only, or choose a source with a real endpoint."
            state="warning"
            title="Live capture not available for this source."
          />
        ) : liveCapture ? (
          <SharedStatePanel
            message="Clicking 'Start capture' will open the live recording page where you connect to the device, stream values, and save the result when done."
            state="empty"
            title="This will open the live capture flow."
          />
        ) : null}
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Create Recording
            </p>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">New recording wizard</h2>
          </div>
          <nav aria-label="Wizard steps">
            <ol className="flex flex-wrap items-center gap-2">
              {STEPS.map((step, index) => (
                <li key={step.id}>
                  <button
                    aria-current={safeStep === index ? "step" : undefined}
                    className={`rounded-md border px-3 py-2 text-sm ${stepChipClass(
                      safeStep === index,
                      index < safeStep,
                    )}`}
                    disabled={index > safeStep}
                    type="button"
                    onClick={() => setCurrentStep(index)}
                  >
                    {index + 1}. {step.label}
                  </button>
                </li>
              ))}
            </ol>
          </nav>
        </div>
      </section>

      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-semibold text-shell-ink">{STEPS[safeStep].label}</h3>
              <p className="mt-1 text-sm text-shell-muted">
                Step {safeStep + 1} of {STEPS.length}
              </p>
            </div>
          </div>

          {showValidation && currentValidationMessage ? (
            <SharedStatePanel
              message={currentValidationMessage}
              state="warning"
              title="A required step is incomplete."
            />
          ) : null}

          {activeStepId === "source" ? renderSourceStep() : null}
          {activeStepId === "scan-type" ? renderScanTypeStep() : null}
          {activeStepId === "review" ? renderReviewStep() : null}
        </div>
      </section>

      <section className="shell-panel px-5 py-4">
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
          <button className="shell-action" type="button" onClick={() => navigate("/recordings")}>
            Cancel
          </button>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <button
              className="shell-action"
              disabled={safeStep === 0}
              type="button"
              onClick={goBack}
            >
              Back
            </button>
            {safeStep === STEPS.length - 1 ? (
              <button
                className="shell-action"
                disabled={activeStepId === "review" && captureBlocked}
                type="button"
                onClick={() => { void createRecording(); }}
              >
                {form.scanType === "SCHEMA_AND_DATA" ? "Start capture" : "Create recording"}
              </button>
            ) : (
              <button
                className="shell-action"
                disabled={!!currentValidationMessage}
                type="button"
                onClick={goNext}
              >
                Next
              </button>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
