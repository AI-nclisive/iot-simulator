import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type RecordingStepId = "source" | "review";
type RecordingStep = { id: RecordingStepId; label: string };

const STEPS: RecordingStep[] = [
  { id: "source", label: "Data source" },
  { id: "review", label: "Review" },
];

type RecordingWizardForm = {
  dataSourceId: string | null;
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
  const access = resolveAccess(accessMode, sharedRole);
  const dataSources = useDataSourcesStore((s) => s.dataSources);
  const loadDataSources = useDataSourcesStore((s) => s.loadDataSources);

  useEffect(() => {
    if (currentProjectId) void loadDataSources(currentProjectId);
  }, [currentProjectId, loadDataSources]);

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [form, setForm] = useState<RecordingWizardForm>({ dataSourceId: null });

  const safeStep = Math.min(currentStep, STEPS.length - 1);
  const activeStepId = STEPS[safeStep].id;
  const currentValidationMessage = stepValidation(form, activeStepId);
  const selectedSource = dataSources.find((ds) => ds.id === form.dataSourceId) ?? null;

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

  function startCapture() {
    if (!form.dataSourceId) return;
    navigate(`/data-sources/${form.dataSourceId}/record`);
  }

  function renderSourceStep() {
    if (dataSources.length === 0) {
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
            The recording will capture live data from the selected source. Only sources with a real device endpoint can be recorded.
          </p>
        </section>
        <div className="space-y-2">
          {dataSources.map((ds) => {
            const isSelected = form.dataSourceId === ds.id;
            const canCapture = !!ds.realDeviceEndpoint;
            return (
              <button
                key={ds.id}
                className={canCapture
                  ? optionButtonClass(isSelected)
                  : "w-full cursor-not-allowed rounded-md border border-shell-line bg-shell-base/30 px-4 py-4 text-left opacity-50"
                }
                disabled={!canCapture}
                type="button"
                onClick={() => canCapture && setForm((f) => ({ ...f, dataSourceId: ds.id }))}
              >
                <div className="flex flex-wrap items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-shell-ink">{ds.name}</p>
                    {ds.realDeviceEndpoint ? (
                      <p className="mt-1 text-xs text-shell-muted font-mono">{ds.realDeviceEndpoint}</p>
                    ) : null}
                    {!canCapture ? (
                      <p className="mt-1 text-xs text-shell-muted">No real device endpoint — live capture unavailable</p>
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

  function renderReviewStep() {
    if (!selectedSource) return null;
    const items = [
      { label: "Data source", value: selectedSource.name },
      { label: "Protocol", value: selectedSource.protocol },
      { label: "Status", value: selectedSource.status },
      ...(selectedSource.realDeviceEndpoint ? [{ label: "Endpoint", value: selectedSource.realDeviceEndpoint }] : []),
    ];
    return (
      <div className="space-y-4">
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
        <SharedStatePanel
          message="Clicking 'Start capture' will open the live recording page where you connect to the device, stream values, and save the result when done."
          state="empty"
          title="This will open the live capture flow."
        />
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
                type="button"
                onClick={startCapture}
              >
                Start capture
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
