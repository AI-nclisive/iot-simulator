import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { apiFetch } from "../api";
import type { BackendProtocol } from "../api";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type DataSourceResponse = {
  id: string;
  [key: string]: unknown;
};

type ProtocolOption = {
  id: "OPC UA" | "Modbus TCP";
  note: string;
  portHint: string;
};

type SourceBasis = "scan" | "import";

type BasisOption = {
  id: SourceBasis;
  label: string;
  note: string;
  recommended?: boolean;
};

export type WizardFormState = {
  basis: SourceBasis | null;
  importSelectedRecordingId: string | null;
  modbusAddressBase: "0" | "1";
  modbusUnitId: string;
  name: string;
  opcUaNamespaceStrategy: "normalize" | "preserve";
  opcUaSecurity: "Basic256Sha256" | "None";
  protocol: ProtocolOption["id"] | null;
  realDeviceEndpoint: string;
  runtimeBehavior: "start-now" | "stopped";
  scanCredentialConfirmed: boolean;
  scanCredentialMode: "anonymous" | "external-ref" | "password";
  scanPassword: string;
  scanSecretRef: string;
  scanState: "complete" | "error" | "idle" | "large" | "partial" | "scanning" | "unknown";
  scanTestResult: "auth-error" | "idle" | "success" | "error";
  scanUsername: string;
  scheduleEndEnabled: boolean;
  scheduleEnd: string;
  scheduleStartEnabled: boolean;
  scheduleStart: string;
  simulatorPort: string;
  schemaReviewNote: string;
};

const protocolOptions: ProtocolOption[] = [
  {
    id: "OPC UA",
    note: "Discovery, recording, and replay against address-rich industrial endpoints.",
    portHint: "Common endpoint: opc.tcp://host:4840",
  },
  {
    id: "Modbus TCP",
    note: "Register-driven simulation for device integrations that expect Modbus behavior.",
    portHint: "Common endpoint: host:502",
  },
];

const basisOptions: BasisOption[] = [
  {
    id: "scan",
    label: "Real source",
    note: "Connect to a live device endpoint to discover structure and start the simulator.",
    recommended: true,
  },
  {
    id: "import",
    label: "Prepared data",
    note: "Use an existing recording as the data source for this simulator.",
  },
];

export type WizardStepId = "protocol" | "basis" | "setup" | "import" | "schedule" | "runtime" | "review";
type WizardStep = { id: WizardStepId; label: string };

const SCAN_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "schedule", label: "Schedule" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
];

const IMPORT_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "import", label: "Import data" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
];

const DEFAULT_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "schedule", label: "Schedule" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
];

function getActiveSteps(basis: SourceBasis | null): WizardStep[] {
  if (basis === "scan") return SCAN_STEPS;
  if (basis === "import") return IMPORT_STEPS;
  return DEFAULT_STEPS;
}

function optionButtonClass(selected: boolean) {
  return `w-full rounded-md border px-4 py-4 text-left transition ${
    selected
      ? "border-shell-accent bg-white shadow-sm"
      : "border-shell-line bg-shell-base/55 hover:border-shell-accent/45 hover:bg-white"
  }`;
}

function stepChipClass(current: boolean, completed: boolean) {
  if (current) {
    return "border-shell-accent bg-white text-shell-ink";
  }

  if (completed) {
    return "border-shell-line bg-white text-shell-ink";
  }

  return "border-shell-line bg-shell-base/55 text-shell-muted";
}

function suggestedEndpoint(protocol: ProtocolOption["id"] | null, basis: SourceBasis | null) {
  if (basis === "scan") {
    if (protocol === "OPC UA") {
      return "opc.tcp://plant.local:4840";
    }

    if (protocol === "Modbus TCP") {
      return "10.20.4.40:502";
    }
  }

  if (protocol === "OPC UA") {
    return "opc.tcp://simulator.local:4840";
  }

  return "127.0.0.1:502";
}

export function validationMessage(stepId: WizardStepId, form: WizardFormState, accessMode: "local" | "shared" = "local") {
  if (stepId === "protocol" && !form.protocol) {
    return "Choose a protocol to continue.";
  }

  if (stepId === "basis" && !form.basis) {
    return "Choose how this source will be created.";
  }

  if (stepId === "setup" || stepId === "import") {
    if (!form.name.trim()) {
      return "Enter a source name to continue.";
    }

    if (stepId === "setup" && form.basis === "scan" && !form.realDeviceEndpoint.trim()) {
      return "Enter the real endpoint before continuing.";
    }

    if (stepId === "setup" && form.basis === "scan" && accessMode !== "local") {
      const credentialMessage = credentialValidationMessage(form);
      if (credentialMessage) return credentialMessage;
    }

    if (stepId === "setup") {
      if (!form.simulatorPort.trim() || isNaN(Number(form.simulatorPort)) || Number(form.simulatorPort) < 1 || Number(form.simulatorPort) > 65535) {
        return "Enter a valid simulator port (1–65535).";
      }
    }

    if (stepId === "import") {
      if (!form.importSelectedRecordingId) {
        return "Select a recording to continue.";
      }
    }
  }

  if (stepId === "schedule") {
    if (form.scheduleStartEnabled && !form.scheduleStart) {
      return "Enter a start date and time.";
    }
    if (form.scheduleEndEnabled && !form.scheduleEnd) {
      return "Enter an end date and time.";
    }
  }

  return null;
}

function credentialValidationMessage(form: WizardFormState) {
  if (form.scanCredentialMode === "anonymous") {
    return null;
  }

  if (form.scanCredentialMode === "password") {
    if (!form.scanUsername.trim()) {
      return "Enter the username required for this endpoint.";
    }

    if (!form.scanPassword.trim()) {
      return "Enter the password required for this endpoint.";
    }
  }

  if (form.scanCredentialMode === "external-ref" && !form.scanSecretRef.trim()) {
    return "Enter the external secret reference required for this endpoint.";
  }

  if (!form.scanCredentialConfirmed) {
    return "Confirm credential use before continuing.";
  }

  return null;
}

function credentialReviewValue(form: WizardFormState) {
  if (form.scanCredentialMode === "anonymous") {
    return "Anonymous access";
  }

  if (!form.scanCredentialConfirmed) {
    return "Pending confirmation";
  }

  if (form.scanCredentialMode === "external-ref") {
    return "External secret reference configured";
  }

  return "Session-only username/password configured";
}

function credentialPersistenceLabel(form: WizardFormState) {
  if (form.scanCredentialMode === "external-ref") {
    return "Saved reference";
  }

  if (form.scanCredentialMode === "password") {
    return "Session only";
  }

  return "Not required";
}


function reviewLines(form: WizardFormState, selectedRecordingLabel?: string) {
  const basisLabel = basisOptions.find((option) => option.id === form.basis)?.label ?? "-";
  const runtimeLabel =
    form.runtimeBehavior === "start-now" ? "Start immediately" : "Save without starting";

  return [
    { label: "Source name", value: form.name || "-" },
    { label: "Protocol", value: form.protocol ?? "-" },
    { label: "Source basis", value: basisLabel },
    { label: "Simulator port", value: form.simulatorPort || "-" },
    ...(form.basis === "scan"
      ? [{ label: "Real device endpoint", value: form.realDeviceEndpoint || "-" }]
      : []),
    ...(form.basis === "scan"
      ? [{ label: "Credentials", value: credentialReviewValue(form) }]
      : []),
    ...(form.basis === "import"
      ? [{ label: "Recording", value: selectedRecordingLabel ?? "-" }]
      : []),
    ...([{ label: "Runtime behavior", value: runtimeLabel }]),
    ...(form.scheduleStartEnabled
      ? [{ label: "Start", value: form.scheduleStart || "-" }]
      : []),
    ...(form.scheduleEndEnabled
      ? [{ label: "End", value: form.scheduleEnd || "-" }]
      : []),
  ];
}

export function CreateDataSourceWizardPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const startDataSource = useDataSourcesStore((state) => state.startDataSource);
  const push = useNotificationStore((state) => state.push);
  const access = resolveAccess(accessMode, sharedRole);

  const artifacts = useArtifactsStore((state) => state.artifacts);
  const isArtifactsLoading = useArtifactsStore((state) => state.isLoading);
  const artifactsError = useArtifactsStore((state) => state.error);
  const loadRecordings = useArtifactsStore((state) => state.loadRecordings);

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [form, setForm] = useState<WizardFormState>({
    basis: null,
    importSelectedRecordingId: null,
    modbusAddressBase: "0",
    modbusUnitId: "1",
    name: "",
    opcUaNamespaceStrategy: "normalize",
    opcUaSecurity: "None",
    protocol: null,
    realDeviceEndpoint: "",
    runtimeBehavior: "stopped",
    scanCredentialConfirmed: false,
    scanCredentialMode: "anonymous",
    scanPassword: "",
    scanSecretRef: "",
    scanState: "idle",
    scanTestResult: "idle",
    scanUsername: "",
    scheduleEndEnabled: false,
    scheduleEnd: "",
    scheduleStartEnabled: false,
    scheduleStart: "",
    simulatorPort: "",
    schemaReviewNote: "",
  });

  const activeSteps = getActiveSteps(form.basis);
  const safeStep = Math.min(currentStep, activeSteps.length - 1);
  const activeStepId = activeSteps[safeStep].id;
  const currentValidationMessage = validationMessage(activeStepId, form, accessMode);
  const currentProtocol = protocolOptions.find((option) => option.id === form.protocol) ?? null;
  const currentBasis = basisOptions.find((option) => option.id === form.basis) ?? null;

  const selectedRecording = useMemo(
    () => artifacts.find((a) => a.id === form.importSelectedRecordingId) ?? null,
    [artifacts, form.importSelectedRecordingId],
  );
  const reviewItems = useMemo(
    () => reviewLines(form, selectedRecording ? `Recording ${selectedRecording.id.slice(0, 8)}` : undefined),
    [form, selectedRecording],
  );

  useEffect(() => {
    if (form.basis === "import" && currentProjectId) {
      void loadRecordings(currentProjectId);
    }
  }, [form.basis, currentProjectId, loadRecordings]);

  if (!access.canCreateSource) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Creating a source is restricted here. In shared mode this flow becomes available only where the current role is allowed to modify project content."
            state="locked"
            title="Source creation is not available."
          />
        </section>
      </div>
    );
  }

  function updateForm(patch: Partial<WizardFormState>) {
    setForm((current) => ({ ...current, ...patch }));
  }

  function handleProtocolSelect(protocol: ProtocolOption["id"]) {
    updateForm({
      protocol,
      simulatorPort:
        form.simulatorPort.trim().length === 0
          ? protocol === "OPC UA" ? "4840" : "502"
          : form.simulatorPort,
      realDeviceEndpoint:
        form.basis === "scan" && form.realDeviceEndpoint.trim().length === 0
          ? suggestedEndpoint(protocol, form.basis)
          : form.realDeviceEndpoint,
    });
  }

  function handleBasisSelect(basis: SourceBasis) {
    updateForm({
      basis,
      importSelectedRecordingId: null,
      realDeviceEndpoint:
      basis === "scan" && form.realDeviceEndpoint.trim().length === 0
          ? suggestedEndpoint(form.protocol, basis)
          : form.realDeviceEndpoint,
      scanState: basis === "scan" ? form.scanState : "idle",
      scanTestResult: basis === "scan" ? form.scanTestResult : "idle",
    });
  }

  function backendProtocolForForm(protocol: WizardFormState["protocol"]): BackendProtocol {
    return protocol === "OPC UA" ? "OPC_UA" : "MODBUS_TCP";
  }

  async function testScanDetails() {
    if (!currentProjectId || !form.protocol) {
      updateForm({ scanTestResult: "error" });
      return;
    }
    try {
      const result = await apiFetch<{ status: string; message: string }>(
        `/api/v1/projects/${currentProjectId}/data-sources/scan/test-connection`,
        {
          method: "POST",
          body: JSON.stringify({
            protocol: backendProtocolForForm(form.protocol),
            endpointUrl: form.realDeviceEndpoint,
          }),
        },
      );
      updateForm({ scanTestResult: result.status === "OK" ? "success" : "error" });
    } catch {
      updateForm({ scanTestResult: "error" });
    }
  }


  function handleCredentialModeChange(mode: WizardFormState["scanCredentialMode"]) {
    updateForm({
      scanCredentialConfirmed: mode === "anonymous",
      scanCredentialMode: mode,
      scanPassword: mode === "password" ? form.scanPassword : "",
      scanSecretRef: mode === "external-ref" ? form.scanSecretRef : "",
      scanTestResult: "idle",
      scanUsername: mode === "password" ? form.scanUsername : "",
    });
  }

  function clearCredentialMaterial() {
    updateForm({
      scanCredentialConfirmed: form.scanCredentialMode === "anonymous",
      scanPassword: "",
      scanSecretRef: "",
      scanTestResult: "idle",
      scanUsername: "",
    });
  }

  function goNext() {
    if (currentValidationMessage) {
      setShowValidation(true);
      return;
    }

    setShowValidation(false);
    setCurrentStep((step) => Math.min(step + 1, activeSteps.length - 1));
  }

  function goBack() {
    setShowValidation(false);
    setCurrentStep((step) => Math.max(step - 1, 0));
  }

  function cancelWizard() {
    navigate("/data-sources");
  }

  async function createSource() {
    if (!form.protocol || !form.basis) {
      setShowValidation(true);
      return;
    }

    if (!currentProjectId) {
      push({ tone: "error", title: "No project selected. Choose a project from the sidebar first." });
      return;
    }

    try {
      const data = await apiFetch<DataSourceResponse>(
        `/api/v1/projects/${currentProjectId}/data-sources`,
        {
          method: "POST",
          body: JSON.stringify({
            name: form.name.trim(),
            simulatorPort: Number(form.simulatorPort),
            protocol: backendProtocolForForm(form.protocol),
            basis: form.basis.toUpperCase(),
            ...(form.basis === "scan" ? { realDeviceEndpoint: form.realDeviceEndpoint } : {}),
            ...(form.basis === "import" && form.importSelectedRecordingId
              ? { recordingId: form.importSelectedRecordingId }
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
      const createdId = data.id;

      if (form.runtimeBehavior === "start-now") {
        try {
          await startDataSource(createdId, currentProjectId);
        } catch {
          // Non-fatal: source was created; start failure is surfaced separately
        }
      }

      navigate(`/data-sources/${createdId}`);
    } catch (err) {
      console.error("[createSource]", err);
      const title = err instanceof Error ? err.message : "Failed to create source";
      push({ tone: "error", title });
    }
  }

  function renderSetupStep() {
    return (
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(18rem,1fr)]">
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Source name
              <input
                className="shell-field"
                placeholder="Line A telemetry source"
                type="text"
                value={form.name}
                onChange={(event) => updateForm({ name: event.target.value })}
              />
            </label>
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Simulator port
              <input
                className="shell-field"
                inputMode="numeric"
                max={65535}
                min={1}
                placeholder={form.protocol === "OPC UA" ? "4840" : "502"}
                type="number"
                value={form.simulatorPort}
                onChange={(event) => updateForm({ simulatorPort: event.target.value })}
              />
            </label>
          </div>

          {form.basis === "scan" ? (
            <div className="space-y-3">
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Real endpoint
                <input
                  className="shell-field"
                  placeholder={suggestedEndpoint(form.protocol, form.basis)}
                  type="text"
                    value={form.realDeviceEndpoint}
                    onChange={(event) =>
                      updateForm({
                        realDeviceEndpoint: event.target.value,
                        scanState: "idle",
                        scanTestResult: "idle",
                      })
                    }
                  />
                </label>

                {accessMode !== "local" ? (
                <div className="space-y-3 border-t border-shell-line pt-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-medium text-shell-ink">Credential handling</p>
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge label={credentialPersistenceLabel(form)} />
                      {form.scanCredentialConfirmed ? (
                        <StatusBadge label="Confirmed" tone="accent" />
                      ) : null}
                    </div>
                  </div>

                  <label className="flex flex-col gap-2 text-sm text-shell-muted">
                    Authentication
                    <select
                      className="shell-field"
                      value={form.scanCredentialMode}
                      onChange={(event) =>
                        handleCredentialModeChange(
                          event.target.value as WizardFormState["scanCredentialMode"],
                        )
                      }
                    >
                      <option value="anonymous">Anonymous</option>
                      <option value="password">Username and password</option>
                      <option value="external-ref">External secret reference</option>
                    </select>
                  </label>

                  {form.scanCredentialMode === "password" ? (
                    <div className="grid gap-3 sm:grid-cols-2">
                      <label className="flex flex-col gap-2 text-sm text-shell-muted">
                        Username
                        <input
                          autoComplete="username"
                          className="shell-field"
                          type="text"
                          value={form.scanUsername}
                          onChange={(event) =>
                            updateForm({
                              scanCredentialConfirmed: false,
                              scanTestResult: "idle",
                              scanUsername: event.target.value,
                            })
                          }
                        />
                      </label>

                      <label className="flex flex-col gap-2 text-sm text-shell-muted">
                        Password
                        <input
                          autoComplete="current-password"
                          className="shell-field"
                          type="password"
                          value={form.scanPassword}
                          onChange={(event) =>
                            updateForm({
                              scanCredentialConfirmed: false,
                              scanPassword: event.target.value,
                              scanTestResult: "idle",
                            })
                          }
                        />
                      </label>
                    </div>
                  ) : null}

                  {form.scanCredentialMode === "external-ref" ? (
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Secret reference
                      <input
                        className="shell-field"
                        placeholder="vault/plant/line-a/opcua"
                        type="text"
                        value={form.scanSecretRef}
                        onChange={(event) =>
                          updateForm({
                            scanCredentialConfirmed: false,
                            scanSecretRef: event.target.value,
                            scanTestResult: "idle",
                          })
                        }
                      />
                    </label>
                  ) : null}

                  {form.scanCredentialMode !== "anonymous" ? (
                    <div className="flex flex-wrap items-center gap-3">
                      <label className="flex items-center gap-2 text-sm text-shell-muted">
                        <input
                          checked={form.scanCredentialConfirmed}
                          type="checkbox"
                          onChange={(event) =>
                            updateForm({ scanCredentialConfirmed: event.target.checked })
                          }
                        />
                        Use for this scan
                      </label>
                      <button
                        className="shell-text-action"
                        type="button"
                        onClick={clearCredentialMaterial}
                      >
                        Clear value
                      </button>
                    </div>
                  ) : null}

                  <div className="flex flex-wrap items-center gap-3">
                    <button className="shell-action" type="button" onClick={testScanDetails}>
                      Test connection
                    </button>
                    {form.scanTestResult === "success" ? (
                      <StatusBadge label="Connection ready" tone="accent" />
                    ) : null}
                    {form.scanTestResult === "error" ? (
                      <StatusBadge label="Enter a valid endpoint" tone="danger" />
                    ) : null}
                    {form.scanTestResult === "auth-error" ? (
                      <StatusBadge label="Check credentials" tone="danger" />
                    ) : null}
                  </div>
                </div>
                ) : null}
              </div>
            ) : null}

          {form.protocol === "OPC UA" ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Security policy
                  <select
                    className="shell-field"
                    value={form.opcUaSecurity}
                    onChange={(event) =>
                      updateForm({
                        opcUaSecurity: event.target.value as WizardFormState["opcUaSecurity"],
                      })
                    }
                  >
                    <option value="None">None</option>
                    <option value="Basic256Sha256">Basic256Sha256</option>
                  </select>
                </label>
              </div>

              {form.basis === "scan" ? (
                <div className="border-t border-shell-line pt-3">
                  <button
                    className="flex items-center gap-1.5 text-sm text-shell-muted hover:text-shell-ink transition"
                    type="button"
                    onClick={() => setShowAdvanced((v) => !v)}
                  >
                    <span>{showAdvanced ? "▾" : "▸"}</span>
                    Advanced options
                  </button>

                  {showAdvanced ? (
                    <div className="mt-3 space-y-3">
                      <label className="flex flex-col gap-2 text-sm text-shell-muted">
                        Node ID format
                        <select
                          className="shell-field"
                          value={form.opcUaNamespaceStrategy}
                          onChange={(event) =>
                            updateForm({
                              opcUaNamespaceStrategy:
                                event.target.value as WizardFormState["opcUaNamespaceStrategy"],
                            })
                          }
                        >
                          <option value="normalize">Normalize (recommended)</option>
                          <option value="preserve">Preserve original</option>
                        </select>
                        <p className="text-xs text-shell-muted leading-5">
                          <strong className="text-shell-ink">Normalize</strong> — the simulator assigns its own node IDs after discovery. The schema works across different servers and is easier to read.
                          <br />
                          <strong className="text-shell-ink">Preserve original</strong> — keeps the exact node IDs from the real device (e.g. <span className="font-mono">ns=3;s=Boiler.Temp</span>). Use this only if another system expects those exact IDs.
                        </p>
                      </label>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </>
          ) : null}

          {form.protocol === "Modbus TCP" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Unit ID
                <input
                  className="shell-field"
                  inputMode="numeric"
                  type="text"
                  value={form.modbusUnitId}
                  onChange={(event) => updateForm({ modbusUnitId: event.target.value })}
                />
              </label>

              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Address base
                <select
                  className="shell-field"
                  value={form.modbusAddressBase}
                  onChange={(event) =>
                    updateForm({
                      modbusAddressBase:
                        event.target.value as WizardFormState["modbusAddressBase"],
                    })
                  }
                >
                  <option value="0">0-based addressing</option>
                  <option value="1">1-based addressing</option>
                </select>
              </label>
            </div>
          ) : null}

          {form.basis === "import" ? (
            <div className="space-y-3">
              <p className="text-sm text-shell-muted">
                Select a recording to use as the data source for this simulator.
              </p>
              {isArtifactsLoading ? (
                <p className="text-sm text-shell-muted">Loading recordings…</p>
              ) : artifactsError ? (
                <p className="text-sm text-shell-danger">{artifactsError}</p>
              ) : artifacts.length === 0 ? (
                <section className="rounded-md border border-shell-line bg-shell-base/40 px-4 py-6 text-center">
                  <p className="text-sm font-medium text-shell-ink">No recordings available.</p>
                  <p className="mt-1 text-sm text-shell-muted">
                    Create a recording first from the Recordings page.
                  </p>
                </section>
              ) : (
                <ul className="space-y-2">
                  {artifacts.map((artifact) => {
                    const isSelected = form.importSelectedRecordingId === artifact.id;
                    return (
                      <li key={artifact.id}>
                        <button
                          className={`w-full rounded-md border px-4 py-3 text-left transition ${
                            isSelected
                              ? "border-shell-accent bg-white shadow-sm"
                              : "border-shell-line bg-shell-base/55 hover:border-shell-accent/45 hover:bg-white"
                          }`}
                          type="button"
                          onClick={() => updateForm({ importSelectedRecordingId: artifact.id })}
                        >
                          <p className="text-sm font-medium text-shell-ink">
                            Recording {artifact.id.slice(0, 8)}
                          </p>
                          <div className="mt-1 flex flex-wrap items-center gap-2">
                            <span className="text-xs text-shell-muted">
                              {new Date(artifact.createdAt).toLocaleDateString("en-GB", {
                                day: "2-digit",
                                month: "short",
                                year: "numeric",
                              })}
                            </span>
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          ) : null}

        </div>

        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Current path
          </p>
          <dl className="mt-3 space-y-3 text-sm">
              <div>
                <dt className="text-shell-muted">Protocol</dt>
                <dd className="mt-1 text-shell-ink">{form.protocol ?? "-"}</dd>
              </div>
              <div>
                <dt className="text-shell-muted">Source basis</dt>
                <dd className="mt-1 text-shell-ink">{currentBasis?.label ?? "-"}</dd>
              </div>
              {form.basis === "scan" ? (
                <div>
                  <dt className="text-shell-muted">Credentials</dt>
                  <dd className="mt-1 text-shell-ink">{credentialReviewValue(form)}</dd>
                </div>
              ) : null}
              <div>
                <dt className="text-shell-muted">Next</dt>
                <dd className="mt-1 text-shell-ink">
                  {form.basis === "scan" ? "Schema review" : "Runtime"}
                </dd>
              </div>
          </dl>
        </section>
      </div>
    );
  }

  function renderScheduleStep() {
    return (
      <div className="space-y-4">
        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-sm font-medium text-shell-ink">Recording schedule</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Set a start and end time for this recording, or leave both off to start immediately with no time limit.
          </p>
        </section>

        <div className="space-y-4">
          <label className="flex items-center gap-3 text-sm text-shell-muted">
            <input
              checked={form.scheduleStartEnabled}
              type="checkbox"
              onChange={(e) => updateForm({ scheduleStartEnabled: e.target.checked, scheduleStart: e.target.checked ? form.scheduleStart : "" })}
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
              onChange={(e) => updateForm({ scheduleEndEnabled: e.target.checked, scheduleEnd: e.target.checked ? form.scheduleEnd : "" })}
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
              The recording will start immediately when the source starts and run without a time limit.
            </section>
          ) : null}
        </div>
      </div>
    );
  }

  function renderRuntimeStep() {
    return (
      <div className="grid gap-3 lg:grid-cols-2">
        <button
          className={optionButtonClass(form.runtimeBehavior === "stopped")}
          type="button"
          onClick={() => updateForm({ runtimeBehavior: "stopped" })}
        >
          <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm font-medium text-shell-ink">Save without starting</p>
              <StatusBadge label="Recommended" tone="accent" />
            </div>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Create the source as inactive. You can start it later from the source detail.
            </p>
          </button>

        <button
          className={optionButtonClass(form.runtimeBehavior === "start-now")}
          type="button"
          onClick={() => updateForm({ runtimeBehavior: "start-now" })}
        >
          <p className="text-sm font-medium text-shell-ink">Start immediately</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Create the source and mark it active right away so you can continue into runtime work.
          </p>
        </button>
      </div>
    );
  }

  function renderReviewStep() {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {reviewItems.map((item) => (
          <div
            key={item.label}
            className="rounded-md border border-shell-line bg-white px-4 py-4"
          >
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
    if (activeStepId === "protocol") {
      return (
        <div className="grid gap-3 lg:grid-cols-2">
          {protocolOptions.map((option) => (
            <button
              key={option.id}
              className={optionButtonClass(form.protocol === option.id)}
              type="button"
              onClick={() => handleProtocolSelect(option.id)}
            >
              <p className="text-sm font-medium text-shell-ink">{option.id}</p>
              <p className="mt-2 text-sm leading-6 text-shell-muted">{option.note}</p>
              <p className="mt-3 text-xs text-shell-muted">{option.portHint}</p>
            </button>
          ))}
        </div>
      );
    }

    if (activeStepId === "basis") {
      return (
        <div className="grid gap-3 lg:grid-cols-2">
          {basisOptions.map((option) => (
            <button
              key={option.id}
              className={optionButtonClass(form.basis === option.id)}
              type="button"
              onClick={() => handleBasisSelect(option.id)}
            >
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm font-medium text-shell-ink">{option.label}</p>
                {option.recommended ? (
                  <StatusBadge label="Recommended" tone="accent" />
                ) : null}
              </div>
              <p className="mt-2 text-sm leading-6 text-shell-muted">{option.note}</p>
            </button>
          ))}
        </div>
      );
    }

    if (activeStepId === "setup" || activeStepId === "import") {
      return renderSetupStep();
    }

    if (activeStepId === "schedule") {
      return renderScheduleStep();
    }

    if (activeStepId === "runtime") {
      return renderRuntimeStep();
    }

    return renderReviewStep();
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Create Data Source
            </p>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">
              New source wizard
            </h2>
          </div>

          <nav aria-label="Wizard steps">
            <ol className="flex flex-wrap items-center gap-2">
              {activeSteps.map((step, index) => (
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
              <h3 className="text-lg font-semibold text-shell-ink">
                {activeSteps[safeStep].label}
              </h3>
              <p className="mt-1 text-sm text-shell-muted">
                Step {safeStep + 1} of {activeSteps.length}
              </p>
            </div>

            {currentProtocol ? <StatusBadge label={currentProtocol.id} tone="accent" /> : null}
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
          <button className="shell-action" type="button" onClick={cancelWizard}>
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

            {safeStep === activeSteps.length - 1 ? (
              <button className="shell-action" type="button" onClick={createSource}>
                Create source
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
