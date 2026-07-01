import { useEffect, useMemo, useRef, useState } from "react";
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

// Backend scan response shapes
type ScanJobResponse = {
  jobId: string;
  status: string;
};

type ScanPollResponse = {
  jobId: string;
  status: string;
  truncated: boolean;
  discoveredCount: number;
  unknownCount: number;
  message: string;
  nodes: unknown[];
};

type DataSourceResponse = {
  id: string;
  [key: string]: unknown;
};

type ProtocolOption = {
  id: "OPC UA" | "Modbus TCP";
  note: string;
  portHint: string;
};

type SourceBasis = "scan" | "manual" | "import";

type BasisOption = {
  id: SourceBasis;
  label: string;
  note: string;
  recommended?: boolean;
};

type WizardFormState = {
  basis: SourceBasis | null;
  importSelectedSampleId: string | null;
  modbusAddressBase: "0" | "1";
  modbusUnitId: string;
  name: string;
  opcUaNamespaceStrategy: "normalize" | "preserve";
  opcUaSecurity: "Basic256Sha256" | "None";
  protocol: ProtocolOption["id"] | null;
  runtimeBehavior: "start-now" | "stopped";
  scanCredentialConfirmed: boolean;
  scanCredentialMode: "anonymous" | "external-ref" | "password";
  scanPassword: string;
  scanEndpoint: string;
  scanSecretRef: string;
  scanState: "complete" | "error" | "idle" | "large" | "partial" | "scanning" | "unknown";
  scanTestResult: "auth-error" | "idle" | "success" | "error";
  scanUsername: string;
  scanJobId: string | null;
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
    label: "Scan real source",
    note: "Promoted path for discovering structure from a live endpoint.",
    recommended: true,
  },
  {
    id: "manual",
    label: "Manual schema",
    note: "Define the starting structure directly when no live source is available.",
  },
  {
    id: "import",
    label: "Prepared data",
    note: "Bring in an existing recording, sample, or schema package.",
  },
];

type WizardStepId = "protocol" | "basis" | "setup" | "import" | "schema" | "runtime" | "review";
type WizardStep = { id: WizardStepId; label: string };

const SCAN_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "schema", label: "Schema" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
];

const MANUAL_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Manual schema" },
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
  { id: "schema", label: "Schema" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
];

function getActiveSteps(basis: SourceBasis | null): WizardStep[] {
  if (basis === "scan") return SCAN_STEPS;
  if (basis === "manual") return MANUAL_STEPS;
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

function validationMessage(stepId: WizardStepId, form: WizardFormState) {
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

    if (stepId === "setup" && form.basis === "scan" && !form.scanEndpoint.trim()) {
      return "Enter the real endpoint before continuing.";
    }

    if (stepId === "setup" && form.basis === "scan") {
      const credentialMessage = credentialValidationMessage(form);
      if (credentialMessage) return credentialMessage;
    }

    if (stepId === "import") {
      if (!form.importSelectedSampleId) {
        return "Select a sample to continue.";
      }
    }
  }

  if (stepId === "schema" && form.basis === "scan") {
    if (form.scanState === "idle" || form.scanState === "scanning") {
      return "Run the scan and review the discovery result before continuing.";
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

function resolveScanTestResult(form: WizardFormState): WizardFormState["scanTestResult"] {
  if (!form.scanEndpoint.trim()) {
    return "error";
  }

  if (credentialValidationMessage(form)) {
    return "auth-error";
  }

  const loweredEndpoint = form.scanEndpoint.toLowerCase();
  const loweredPassword = form.scanPassword.toLowerCase();
  const loweredSecretRef = form.scanSecretRef.toLowerCase();

  if (
    loweredEndpoint.includes("authfail") ||
    loweredPassword.includes("invalid") ||
    loweredSecretRef.includes("invalid")
  ) {
    return "auth-error";
  }

  return "success";
}

function scanReviewCopy(scanState: WizardFormState["scanState"]) {
  if (scanState === "complete") {
    return {
      message: "Review the discovered structure, then continue when it matches what this source should simulate.",
      status: "Ready for review",
    };
  }

  if (scanState === "partial") {
    return {
      message: "Review the discovered subset and continue only if the missing areas are acceptable for this source.",
      status: "Review partial result",
    };
  }

  if (scanState === "large") {
    return {
      message: "Review the high-level structure first. You can refine the parameter set later in Schema.",
      status: "Review large schema",
    };
  }

  return {
    message: "Review the unknown values and continue only if they can be mapped later in schema editing.",
    status: "Resolve unknown types",
  };
}

function resolveScanOutcome(form: WizardFormState): WizardFormState["scanState"] {
  const endpoint = form.scanEndpoint.toLowerCase();

  if (endpoint.includes("legacy")) {
    return "unknown";
  }

  if (endpoint.includes("partial")) {
    return "partial";
  }

  if (endpoint.includes("field") || endpoint.includes("lab")) {
    return "large";
  }

  return "complete";
}

function reviewLines(form: WizardFormState, selectedSampleName?: string) {
  const basisLabel = basisOptions.find((option) => option.id === form.basis)?.label ?? "-";
  const runtimeLabel =
    form.runtimeBehavior === "start-now" ? "Start immediately" : "Save without starting";

  return [
    { label: "Source name", value: form.name || "-" },
    { label: "Protocol", value: form.protocol ?? "-" },
    { label: "Source basis", value: basisLabel },
    {
      label: "Endpoint or input",
      value:
        form.basis === "scan"
          ? form.scanEndpoint || "-"
          : form.basis === "import"
            ? selectedSampleName ?? "-"
            : suggestedEndpoint(form.protocol, form.basis),
    },
    ...(form.basis === "scan"
      ? [{ label: "Credentials", value: credentialReviewValue(form) }]
      : []),
    ...(form.basis === "import"
      ? [{ label: "Sample", value: selectedSampleName ?? "-" }]
      : []),
    ...(form.basis === "manual"
      ? [{ label: "Next step", value: "Schema editor opens after creation" }]
      : [{ label: "Runtime behavior", value: runtimeLabel }]),
    ...(form.basis === "scan"
      ? [{ label: "Schema note", value: form.schemaReviewNote.trim() || "No note" }]
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
  const scanPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const samples = useArtifactsStore((state) => state.samples);
  const isSamplesLoading = useArtifactsStore((state) => state.isSamplesLoading);
  const samplesError = useArtifactsStore((state) => state.samplesError);
  const loadSamples = useArtifactsStore((state) => state.loadSamples);

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [form, setForm] = useState<WizardFormState>({
    basis: null,
    importSelectedSampleId: null,
    modbusAddressBase: "0",
    modbusUnitId: "1",
    name: "",
    opcUaNamespaceStrategy: "normalize",
    opcUaSecurity: "None",
    protocol: null,
    runtimeBehavior: "stopped",
    scanCredentialConfirmed: false,
    scanCredentialMode: "anonymous",
    scanPassword: "",
    scanEndpoint: "",
    scanSecretRef: "",
    scanState: "idle",
    scanTestResult: "idle",
    scanUsername: "",
    scanJobId: null,
    schemaReviewNote: "",
  });

  const activeSteps = getActiveSteps(form.basis);
  const safeStep = Math.min(currentStep, activeSteps.length - 1);
  const activeStepId = activeSteps[safeStep].id;
  const currentValidationMessage = validationMessage(activeStepId, form);
  const currentProtocol = protocolOptions.find((option) => option.id === form.protocol) ?? null;
  const currentBasis = basisOptions.find((option) => option.id === form.basis) ?? null;

  const selectedSample = useMemo(
    () => samples.find((s) => s.id === form.importSelectedSampleId) ?? null,
    [samples, form.importSelectedSampleId],
  );
  const reviewItems = useMemo(() => reviewLines(form, selectedSample?.name), [form, selectedSample]);

  // Load samples when import basis is active
  useEffect(() => {
    if (form.basis === "import" && currentProjectId) {
      loadSamples(currentProjectId);
    }
  }, [form.basis, currentProjectId, loadSamples]);

  // Clean up polling on unmount
  useEffect(() => {
    return () => {
      if (scanPollRef.current !== null) {
        clearInterval(scanPollRef.current);
      }
    };
  }, []);

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
      scanEndpoint:
        form.basis === "scan" && form.scanEndpoint.trim().length === 0
          ? suggestedEndpoint(protocol, form.basis)
          : form.scanEndpoint,
    });
  }

  function handleBasisSelect(basis: SourceBasis) {
    updateForm({
      basis,
      importSelectedSampleId: null,
      scanEndpoint:
      basis === "scan" && form.scanEndpoint.trim().length === 0
          ? suggestedEndpoint(form.protocol, basis)
          : form.scanEndpoint,
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
            endpointUrl: form.scanEndpoint,
          }),
        },
      );
      updateForm({ scanTestResult: result.status === "OK" ? "success" : "error" });
    } catch {
      updateForm({ scanTestResult: "error" });
    }
  }

  async function startScan() {
    if (!currentProjectId || !form.protocol) {
      updateForm({ scanState: "error", scanTestResult: "error" });
      return;
    }
    updateForm({ scanState: "scanning" });
    try {
      const job = await apiFetch<ScanJobResponse>(
        `/api/v1/projects/${currentProjectId}/data-sources/scan`,
        {
          method: "POST",
          body: JSON.stringify({
            protocol: backendProtocolForForm(form.protocol),
            endpointUrl: form.scanEndpoint,
          }),
        },
      );
      updateForm({ scanJobId: job.jobId });
      // Poll every 2 s until done/failed/etc.
      if (scanPollRef.current !== null) clearInterval(scanPollRef.current);
      scanPollRef.current = setInterval(async () => {
        try {
          const poll = await apiFetch<ScanPollResponse>(
            `/api/v1/projects/${currentProjectId}/data-sources/scan/${job.jobId}`,
          );
          const terminalStatuses = ["DONE", "FAILED", "UNREACHABLE", "AUTH_FAILED", "PARTIAL", "LARGE_SCHEMA"];
          if (terminalStatuses.includes(poll.status)) {
            if (scanPollRef.current !== null) clearInterval(scanPollRef.current);
            if (poll.status === "DONE") {
              updateForm({ scanState: "complete" });
            } else if (poll.status === "PARTIAL") {
              updateForm({ scanState: "partial" });
            } else if (poll.status === "LARGE_SCHEMA") {
              updateForm({ scanState: "large" });
            } else {
              updateForm({ scanState: "error" });
            }
          }
        } catch {
          if (scanPollRef.current !== null) clearInterval(scanPollRef.current);
          updateForm({ scanState: "error" });
        }
      }, 2000);
    } catch {
      updateForm({ scanState: "error" });
    }
  }

  function retryScan() {
    if (scanPollRef.current !== null) {
      clearInterval(scanPollRef.current);
      scanPollRef.current = null;
    }
    updateForm({ scanState: "idle", scanJobId: null });
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
      let createdId: string;

      if (form.basis === "scan" && form.scanJobId) {
        // Use the scan job create endpoint
        const data = await apiFetch<DataSourceResponse>(
          `/api/v1/projects/${currentProjectId}/data-sources/scan/${form.scanJobId}/create`,
          {
            method: "POST",
            body: JSON.stringify({
              name: form.name.trim(),
              endpoint: form.scanEndpoint,
            }),
          },
        );
        createdId = data.id;
      } else {
        const endpointUrl =
          form.basis === "scan"
            ? form.scanEndpoint
            : suggestedEndpoint(form.protocol, form.basis);
        const data = await apiFetch<DataSourceResponse>(
          `/api/v1/projects/${currentProjectId}/data-sources`,
          {
            method: "POST",
            body: JSON.stringify({
              name: form.name.trim(),
              endpoint: JSON.stringify({ url: endpointUrl }),
              runtimeConfig: JSON.stringify({}),
              protocol: backendProtocolForForm(form.protocol),
              basis: form.basis.toUpperCase(),
            }),
          },
        );
        createdId = data.id;
      }

      if (form.runtimeBehavior === "start-now") {
        try {
          await startDataSource(createdId, currentProjectId);
        } catch {
          // Non-fatal: source was created; start failure is surfaced separately
        }
      }

      if (form.basis === "manual") {
        navigate(`/data-sources/${createdId}?tab=schema`);
      } else {
        navigate(`/data-sources/${createdId}`);
      }
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

          {form.basis === "scan" ? (
            <div className="space-y-3">
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Real endpoint
                <input
                  className="shell-field"
                  placeholder={suggestedEndpoint(form.protocol, form.basis)}
                  type="text"
                    value={form.scanEndpoint}
                    onChange={(event) =>
                      updateForm({
                        scanEndpoint: event.target.value,
                        scanState: "idle",
                        scanTestResult: "idle",
                      })
                    }
                  />
                </label>

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

          {form.basis === "manual" ? (
            <section className="rounded-md border border-shell-accent/30 bg-shell-accent/5 px-4 py-3 text-sm text-shell-ink">
              After creation, the Schema editor opens automatically — add parameters, folders, and data types there.
            </section>
          ) : null}

          {form.basis === "import" ? (
            <div className="space-y-3">
              <p className="text-sm text-shell-muted">
                Select a sample to use as the data source for this source.
              </p>
              {isSamplesLoading ? (
                <p className="text-sm text-shell-muted">Loading samples…</p>
              ) : samplesError ? (
                <p className="text-sm text-shell-danger">{samplesError}</p>
              ) : samples.length === 0 ? (
                <section className="rounded-md border border-shell-line bg-shell-base/40 px-4 py-6 text-center">
                  <p className="text-sm font-medium text-shell-ink">No samples available.</p>
                  <p className="mt-1 text-sm text-shell-muted">
                    Create a sample from an existing recording first.
                  </p>
                </section>
              ) : (
                <ul className="space-y-2">
                  {samples.map((sample) => {
                    const isSelected = form.importSelectedSampleId === sample.id;
                    return (
                      <li key={sample.id}>
                        <button
                          className={`w-full rounded-md border px-4 py-3 text-left transition ${
                            isSelected
                              ? "border-shell-accent bg-white shadow-sm"
                              : "border-shell-line bg-shell-base/55 hover:border-shell-accent/45 hover:bg-white"
                          }`}
                          type="button"
                          onClick={() => updateForm({ importSelectedSampleId: sample.id })}
                        >
                          <p className="text-sm font-medium text-shell-ink">{sample.name}</p>
                          <div className="mt-1 flex flex-wrap items-center gap-2">
                            {sample.tags.length > 0
                              ? sample.tags.map((tag) => (
                                  <span
                                    key={tag}
                                    className="rounded bg-shell-base px-1.5 py-0.5 text-xs text-shell-muted"
                                  >
                                    {tag}
                                  </span>
                                ))
                              : null}
                            <span className="text-xs text-shell-muted">
                              {new Date(sample.createdAt).toLocaleDateString("en-GB", {
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

  function renderSchemaStep() {
    const scanCopy = scanReviewCopy(form.scanState);

    if (form.basis === "scan") {
      return (
        <div className="space-y-4">
          {form.scanState === "idle" ? (
            <SharedStatePanel
              actionLabel="Start scan"
              message="Start discovery now. This step cannot be reviewed until the simulator reads the real endpoint."
              state="warning"
              title="Scan the endpoint before reviewing schema."
              onAction={startScan}
            />
          ) : null}

          {form.scanState === "scanning" ? (
            <SharedStatePanel
              message="The simulator is discovering nodes and preparing a reviewable structure."
              state="loading"
              title="Scanning the real source."
            />
          ) : null}

          {form.scanState === "error" ? (
            <SharedStatePanel
              actionLabel="Retry"
              message="Go back to Setup if the endpoint or credentials need changes, then test the connection before retrying."
              state="error"
              title="The scan could not start."
              onAction={retryScan}
            />
          ) : null}

          {["complete", "partial", "large", "unknown"].includes(form.scanState) ? (
            <section className="rounded-md border border-shell-line bg-white px-4 py-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <p className="text-sm font-medium text-shell-ink">Discovery result</p>
                    <p className="mt-2 text-sm leading-6 text-shell-muted">
                      {scanCopy.message}
                    </p>
                  </div>

                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge
                      label={scanCopy.status}
                    tone={
                      form.scanState === "complete"
                        ? "accent"
                        : form.scanState === "unknown"
                          ? "danger"
                          : "warning"
                    }
                  />
                </div>
              </div>

                <dl className="mt-4 grid gap-3 text-sm text-shell-muted sm:grid-cols-3">
                <div>
                  <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                    Endpoint
                  </dt>
                  <dd className="mt-2 text-sm text-shell-ink">{form.scanEndpoint}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                    Protocol
                  </dt>
                  <dd className="mt-2 text-sm text-shell-ink">{form.protocol}</dd>
                </div>
                  <div>
                    <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                      Next
                    </dt>
                    <dd className="mt-2 text-sm text-shell-ink">Continue into schema review</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                      Credentials
                    </dt>
                    <dd className="mt-2 text-sm text-shell-ink">{credentialReviewValue(form)}</dd>
                  </div>
                </dl>

              <div className="mt-4">
                <button className="shell-action" type="button" onClick={retryScan}>
                  Retry scan
                </button>
              </div>
            </section>
          ) : null}
        </div>
      );
    }

    if (form.basis === "manual") {
      return (
        <div className="space-y-4">
          <section className="rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-sm font-medium text-shell-ink">Schema Editor handoff</p>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Structure will be defined in the Schema Editor after this source is saved. Choose a
              starting template in the Setup step to seed the initial structure.
            </p>
          </section>

          <label className="flex flex-col gap-2 text-sm text-shell-muted">
            Review note
            <textarea
              className="shell-field min-h-[7rem] resize-y"
              placeholder="Optional schema note for this draft path"
              value={form.schemaReviewNote}
              onChange={(event) => updateForm({ schemaReviewNote: event.target.value })}
            />
          </label>
        </div>
      );
    }

    const note =
      form.basis === "import"
        ? "Prepared artifact will seed the initial schema and value shape."
        : "Synthetic setup will generate the starting schema profile.";

    return (
      <div className="space-y-4">
        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-sm text-shell-ink">{note}</p>
        </section>

        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Review note
          <textarea
            className="shell-field min-h-[7rem] resize-y"
            placeholder="Optional schema note for this draft path"
            value={form.schemaReviewNote}
            onChange={(event) => updateForm({ schemaReviewNote: event.target.value })}
          />
        </label>
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

    if (activeStepId === "schema") {
      return renderSchemaStep();
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
