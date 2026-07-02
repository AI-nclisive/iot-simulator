import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { apiFetch } from "../api";
import type { BackendProtocol } from "../api";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type RecordingStepId = "connection" | "scan-type" | "schedule" | "review";
type RecordingStep = { id: RecordingStepId; label: string };

const STEPS: RecordingStep[] = [
  { id: "connection", label: "Connection" },
  { id: "scan-type", label: "Scan type" },
  { id: "schedule", label: "Schedule" },
  { id: "review", label: "Review" },
];

type RecordingWizardForm = {
  protocol: "OPC UA" | "Modbus TCP" | null;
  endpoint: string;
  credentialMode: "anonymous" | "password" | "external-ref";
  username: string;
  password: string;
  secretRef: string;
  credentialConfirmed: boolean;
  scanType: "schema-only" | "schema-and-data" | null;
  scheduleStartEnabled: boolean;
  scheduleStart: string;
  scheduleEndEnabled: boolean;
  scheduleEnd: string;
};

function optionButtonClass(selected: boolean) {
  return `w-full rounded-md border px-4 py-4 text-left transition ${
    selected
      ? "border-shell-accent bg-white shadow-sm"
      : "border-shell-line bg-shell-base/55 hover:border-shell-accent/45 hover:bg-white"
  }`;
}

function stepChipClass(current: boolean, completed: boolean) {
  if (current) return "border-shell-accent bg-white text-shell-ink";
  if (completed) return "border-shell-line bg-white text-shell-ink";
  return "border-shell-line bg-shell-base/55 text-shell-muted";
}

function backendProtocol(protocol: "OPC UA" | "Modbus TCP"): BackendProtocol {
  return protocol === "OPC UA" ? "OPC_UA" : "MODBUS_TCP";
}

function stepValidation(stepId: RecordingStepId, form: RecordingWizardForm, accessMode: "local" | "shared"): string | null {
  if (stepId === "connection") {
    if (!form.protocol) return "Choose a protocol to continue.";
    if (!form.endpoint.trim()) return "Enter the real device endpoint.";
    if (accessMode !== "local" && form.credentialMode !== "anonymous") {
      if (form.credentialMode === "password") {
        if (!form.username.trim()) return "Enter the username.";
        if (!form.password.trim()) return "Enter the password.";
      }
      if (form.credentialMode === "external-ref" && !form.secretRef.trim()) {
        return "Enter the external secret reference.";
      }
      if (!form.credentialConfirmed) return "Confirm credential use before continuing.";
    }
  }
  if (stepId === "scan-type") {
    if (!form.scanType) return "Choose a scan type to continue.";
  }
  if (stepId === "schedule") {
    if (form.scheduleStartEnabled && !form.scheduleStart) return "Enter a start date and time.";
    if (form.scheduleEndEnabled && !form.scheduleEnd) return "Enter an end date and time.";
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

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [form, setForm] = useState<RecordingWizardForm>({
    protocol: null,
    endpoint: "",
    credentialMode: "anonymous",
    username: "",
    password: "",
    secretRef: "",
    credentialConfirmed: false,
    scanType: null,
    scheduleStartEnabled: false,
    scheduleStart: "",
    scheduleEndEnabled: false,
    scheduleEnd: "",
  });

  const safeStep = Math.min(currentStep, STEPS.length - 1);
  const activeStepId = STEPS[safeStep].id;
  const currentValidationMessage = stepValidation(activeStepId, form, accessMode);

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

  function updateForm(patch: Partial<RecordingWizardForm>) {
    setForm((f) => ({ ...f, ...patch }));
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
    if (!form.protocol || !form.scanType || !currentProjectId) return;
    try {
      const result = await apiFetch<{ id: string }>(
        `/api/v1/projects/${currentProjectId}/recordings`,
        {
          method: "POST",
          body: JSON.stringify({
            protocol: backendProtocol(form.protocol),
            endpoint: form.endpoint,
            scanType: form.scanType === "schema-only" ? "SCHEMA_ONLY" : "SCHEMA_AND_DATA",
            credentialMode: form.credentialMode,
            ...(form.credentialMode === "password"
              ? { username: form.username, password: form.password }
              : {}),
            ...(form.credentialMode === "external-ref"
              ? { secretRef: form.secretRef }
              : {}),
            ...(form.scheduleStartEnabled && form.scheduleStart
              ? { scheduleStart: form.scheduleStart }
              : {}),
            ...(form.scheduleEndEnabled && form.scheduleEnd
              ? { scheduleEnd: form.scheduleEnd }
              : {}),
          }),
        },
      );
      push({ tone: "success", title: "Recording created." });
      navigate(`/recordings/${result.id}`);
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to create recording";
      push({ tone: "error", title });
    }
  }

  function renderConnectionStep() {
    return (
      <div className="space-y-4">
        <div className="grid gap-3 lg:grid-cols-2">
          {(["OPC UA", "Modbus TCP"] as const).map((proto) => (
            <button
              key={proto}
              className={optionButtonClass(form.protocol === proto)}
              type="button"
              onClick={() => updateForm({ protocol: proto })}
            >
              <p className="text-sm font-medium text-shell-ink">{proto}</p>
              <p className="mt-2 text-xs text-shell-muted">
                {proto === "OPC UA" ? "Common endpoint: opc.tcp://host:4840" : "Common endpoint: host:502"}
              </p>
            </button>
          ))}
        </div>

        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Real device endpoint
          <input
            className="shell-field"
            placeholder={form.protocol === "OPC UA" ? "opc.tcp://plant.local:4840" : "10.20.4.40:502"}
            type="text"
            value={form.endpoint}
            onChange={(e) => updateForm({ endpoint: e.target.value })}
          />
        </label>

        {accessMode !== "local" ? (
          <div className="space-y-3 border-t border-shell-line pt-4">
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Authentication
              <select
                className="shell-field"
                value={form.credentialMode}
                onChange={(e) =>
                  updateForm({
                    credentialMode: e.target.value as RecordingWizardForm["credentialMode"],
                    credentialConfirmed: e.target.value === "anonymous",
                    username: "",
                    password: "",
                    secretRef: "",
                  })
                }
              >
                <option value="anonymous">Anonymous</option>
                <option value="password">Username and password</option>
                <option value="external-ref">External secret reference</option>
              </select>
            </label>
            {form.credentialMode === "password" ? (
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Username
                  <input
                    autoComplete="username"
                    className="shell-field"
                    type="text"
                    value={form.username}
                    onChange={(e) => updateForm({ username: e.target.value, credentialConfirmed: false })}
                  />
                </label>
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Password
                  <input
                    autoComplete="current-password"
                    className="shell-field"
                    type="password"
                    value={form.password}
                    onChange={(e) => updateForm({ password: e.target.value, credentialConfirmed: false })}
                  />
                </label>
              </div>
            ) : null}
            {form.credentialMode === "external-ref" ? (
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Secret reference
                <input
                  className="shell-field"
                  placeholder="vault/plant/line-a/opcua"
                  type="text"
                  value={form.secretRef}
                  onChange={(e) => updateForm({ secretRef: e.target.value, credentialConfirmed: false })}
                />
              </label>
            ) : null}
            {form.credentialMode !== "anonymous" ? (
              <label className="flex items-center gap-2 text-sm text-shell-muted">
                <input
                  checked={form.credentialConfirmed}
                  type="checkbox"
                  onChange={(e) => updateForm({ credentialConfirmed: e.target.checked })}
                />
                Use for this recording
              </label>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  }

  function renderScanTypeStep() {
    return (
      <div className="grid gap-3 lg:grid-cols-2">
        <button
          className={optionButtonClass(form.scanType === "schema-only")}
          type="button"
          onClick={() => updateForm({ scanType: "schema-only" })}
        >
          <p className="text-sm font-medium text-shell-ink">Scan schema only</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Discover the structure of the real device. No data values are captured — only the schema is saved.
          </p>
        </button>
        <button
          className={optionButtonClass(form.scanType === "schema-and-data")}
          type="button"
          onClick={() => updateForm({ scanType: "schema-and-data" })}
        >
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-shell-ink">Scan schema + data</p>
            <StatusBadge label="Recommended" tone="accent" />
          </div>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Discover the structure and capture live data values for the duration of the recording.
          </p>
        </button>
      </div>
    );
  }

  function renderScheduleStep() {
    return (
      <div className="space-y-4">
        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-sm font-medium text-shell-ink">Recording schedule</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Set a start and end time, or leave both off to start immediately with no time limit.
          </p>
        </section>

        <div className="space-y-4">
          <label className="flex items-center gap-3 text-sm text-shell-muted">
            <input
              checked={form.scheduleStartEnabled}
              type="checkbox"
              onChange={(e) =>
                updateForm({ scheduleStartEnabled: e.target.checked, scheduleStart: e.target.checked ? form.scheduleStart : "" })
              }
            />
            Schedule start time
          </label>
          {form.scheduleStartEnabled ? (
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Start at
              <input
                className="shell-field"
                type="datetime-local"
                value={form.scheduleStart}
                onChange={(e) => updateForm({ scheduleStart: e.target.value })}
              />
            </label>
          ) : null}

          <label className="flex items-center gap-3 text-sm text-shell-muted">
            <input
              checked={form.scheduleEndEnabled}
              type="checkbox"
              onChange={(e) =>
                updateForm({ scheduleEndEnabled: e.target.checked, scheduleEnd: e.target.checked ? form.scheduleEnd : "" })
              }
            />
            Schedule end time
          </label>
          {form.scheduleEndEnabled ? (
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              End at
              <input
                className="shell-field"
                type="datetime-local"
                value={form.scheduleEnd}
                onChange={(e) => updateForm({ scheduleEnd: e.target.value })}
              />
            </label>
          ) : null}

          {!form.scheduleStartEnabled && !form.scheduleEndEnabled ? (
            <section className="rounded-md border border-shell-accent/30 bg-shell-accent/5 px-4 py-3 text-sm text-shell-ink">
              The recording will start immediately and run without a time limit.
            </section>
          ) : null}
        </div>
      </div>
    );
  }

  function renderReviewStep() {
    const scanTypeLabel =
      form.scanType === "schema-only" ? "Scan schema only" : "Scan schema + data";
    const items = [
      { label: "Protocol", value: form.protocol ?? "-" },
      { label: "Endpoint", value: form.endpoint || "-" },
      { label: "Scan type", value: scanTypeLabel },
      ...(form.scheduleStartEnabled ? [{ label: "Start", value: form.scheduleStart || "-" }] : []),
      ...(form.scheduleEndEnabled ? [{ label: "End", value: form.scheduleEnd || "-" }] : []),
      ...(!form.scheduleStartEnabled && !form.scheduleEndEnabled
        ? [{ label: "Schedule", value: "Start immediately, no time limit" }]
        : []),
    ];
    return (
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
    );
  }

  function renderCurrentStep() {
    if (activeStepId === "connection") return renderConnectionStep();
    if (activeStepId === "scan-type") return renderScanTypeStep();
    if (activeStepId === "schedule") return renderScheduleStep();
    return renderReviewStep();
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
            {form.protocol ? <StatusBadge label={form.protocol} tone="accent" /> : null}
          </div>

          {showValidation && currentValidationMessage ? (
            <SharedStatePanel
              message={currentValidationMessage}
              state="warning"
              title="A required step is incomplete."
            />
          ) : null}

          {renderCurrentStep()}
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
              <button className="shell-action" type="button" onClick={createRecording}>
                Create recording
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
