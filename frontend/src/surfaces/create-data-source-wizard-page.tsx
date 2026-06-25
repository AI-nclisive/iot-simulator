import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type ProtocolOption = {
  id: "OPC UA" | "Modbus TCP";
  note: string;
  portHint: string;
};

type SourceBasis = "scan" | "manual" | "import" | "synthetic";

type BasisOption = {
  id: SourceBasis;
  label: string;
  note: string;
  recommended?: boolean;
};

type WizardFormState = {
  basis: SourceBasis | null;
  importArtifactName: string;
  manualTemplate: "empty" | "analog" | "discrete";
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
  schemaReviewNote: string;
  syntheticProfile: "steady" | "spike" | "cycle";
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
  {
    id: "synthetic",
    label: "Synthetic setup",
    note: "Start from a generated pattern set for deliberate test coverage.",
  },
];

const wizardSteps = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "schema", label: "Schema" },
  { id: "runtime", label: "Runtime" },
  { id: "review", label: "Review" },
] as const;

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

function validationMessage(stepIndex: number, form: WizardFormState) {
  if (stepIndex === 0 && !form.protocol) {
    return "Choose a protocol to continue.";
  }

  if (stepIndex === 1 && !form.basis) {
    return "Choose how this source will be created.";
  }

  if (stepIndex === 2) {
    if (!form.name.trim()) {
      return "Enter a source name to continue.";
    }

    if (form.basis === "scan" && !form.scanEndpoint.trim()) {
      return "Enter the real endpoint before continuing.";
    }

    if (form.basis === "scan") {
      const credentialMessage = credentialValidationMessage(form);

      if (credentialMessage) {
        return credentialMessage;
      }
    }

    if (form.basis === "import" && !form.importArtifactName.trim()) {
      return "Enter the prepared artifact name before continuing.";
    }
  }

  if (stepIndex === 3 && form.basis === "scan") {
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

function reviewLines(form: WizardFormState) {
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
            ? form.importArtifactName || "-"
            : suggestedEndpoint(form.protocol, form.basis),
    },
    ...(form.basis === "scan"
      ? [
          {
            label: "Credentials",
            value: credentialReviewValue(form),
          },
        ]
      : []),
    { label: "Runtime behavior", value: runtimeLabel },
    { label: "Schema note", value: form.schemaReviewNote.trim() || "No note" },
  ];
}

export function CreateDataSourceWizardPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const createDataSource = useDataSourcesStore((state) => state.createDataSource);
  const startDataSource = useDataSourcesStore((state) => state.startDataSource);
  const access = resolveAccess(accessMode, sharedRole);

  const [currentStep, setCurrentStep] = useState(0);
  const [showValidation, setShowValidation] = useState(false);
  const [form, setForm] = useState<WizardFormState>({
    basis: null,
    importArtifactName: "",
    manualTemplate: "empty",
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
    schemaReviewNote: "",
    syntheticProfile: "steady",
  });

  const currentValidationMessage = validationMessage(currentStep, form);
  const currentProtocol = protocolOptions.find((option) => option.id === form.protocol) ?? null;
  const currentBasis = basisOptions.find((option) => option.id === form.basis) ?? null;

  const reviewItems = useMemo(() => reviewLines(form), [form]);

  useEffect(() => {
    if (form.scanState !== "scanning") {
      return;
    }

    const timerId = window.setTimeout(() => {
      setForm((current) => ({
        ...current,
        scanState: resolveScanOutcome(current),
      }));
    }, 900);

    return () => window.clearTimeout(timerId);
  }, [form.scanState]);

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
      importArtifactName:
        basis === "import" && form.importArtifactName.length === 0
          ? "line-temperature.recording"
          : form.importArtifactName,
      scanEndpoint:
      basis === "scan" && form.scanEndpoint.trim().length === 0
          ? suggestedEndpoint(form.protocol, basis)
          : form.scanEndpoint,
      scanState: basis === "scan" ? form.scanState : "idle",
      scanTestResult: basis === "scan" ? form.scanTestResult : "idle",
    });
  }

  function testScanDetails() {
    updateForm({ scanTestResult: resolveScanTestResult(form) });
  }

  function startScan() {
    const scanTestResult = resolveScanTestResult(form);

    if (scanTestResult !== "success") {
      updateForm({ scanState: "error", scanTestResult });
      return;
    }

    updateForm({ scanState: "scanning", scanTestResult });
  }

  function retryScan() {
    updateForm({ scanState: "idle" });
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
    setCurrentStep((step) => Math.min(step + 1, wizardSteps.length - 1));
  }

  function goBack() {
    setShowValidation(false);
    setCurrentStep((step) => Math.max(step - 1, 0));
  }

  function cancelWizard() {
    navigate("/data-sources");
  }

  function createSource() {
    if (!form.protocol || !form.basis) {
      setShowValidation(true);
      return;
    }

    const endpoint =
      form.basis === "scan"
        ? form.scanEndpoint
        : form.basis === "import"
          ? suggestedEndpoint(form.protocol, form.basis)
          : suggestedEndpoint(form.protocol, form.basis);

    const createdId = createDataSource({
      endpoint,
      name: form.name.trim(),
      protocol: form.protocol,
    });

    if (form.runtimeBehavior === "start-now") {
      startDataSource(createdId);
    }

    navigate(`/data-sources/${createdId}`);
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

              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Namespace handling
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
                  <option value="normalize">Normalize to simulator</option>
                  <option value="preserve">Preserve discovered namespaces</option>
                </select>
              </label>
            </div>
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
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Starting template
              <select
                className="shell-field"
                value={form.manualTemplate}
                onChange={(event) =>
                  updateForm({
                    manualTemplate: event.target.value as WizardFormState["manualTemplate"],
                  })
                }
              >
                <option value="empty">Empty structure</option>
                <option value="analog">Analog parameter set</option>
                <option value="discrete">Discrete state set</option>
              </select>
            </label>
          ) : null}

          {form.basis === "import" ? (
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Prepared artifact
              <input
                className="shell-field"
                placeholder="packaging-pressure.sample"
                type="text"
                value={form.importArtifactName}
                onChange={(event) =>
                  updateForm({ importArtifactName: event.target.value })
                }
              />
            </label>
          ) : null}

          {form.basis === "synthetic" ? (
            <label className="flex flex-col gap-2 text-sm text-shell-muted">
              Synthetic profile
              <select
                className="shell-field"
                value={form.syntheticProfile}
                onChange={(event) =>
                  updateForm({
                    syntheticProfile: event.target.value as WizardFormState["syntheticProfile"],
                  })
                }
              >
                <option value="steady">Steady trend</option>
                <option value="spike">Spike and recovery</option>
                <option value="cycle">Cyclic pattern</option>
              </select>
            </label>
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
	              <dd className="mt-1 text-shell-ink">Schema review</dd>
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

    const note =
      form.basis === "manual"
        ? "Manual structure will start from the chosen template."
        : form.basis === "import"
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
    if (currentStep === 0) {
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

    if (currentStep === 1) {
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

    if (currentStep === 2) {
      return renderSetupStep();
    }

    if (currentStep === 3) {
      return renderSchemaStep();
    }

    if (currentStep === 4) {
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

          <div className="flex flex-wrap items-center gap-2">
            {wizardSteps.map((step, index) => (
              <button
                key={step.id}
                className={`rounded-md border px-3 py-2 text-sm ${stepChipClass(
                  currentStep === index,
                  index < currentStep,
                )}`}
                disabled={index > currentStep}
                type="button"
                onClick={() => setCurrentStep(index)}
              >
                {index + 1}. {step.label}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-semibold text-shell-ink">
                {wizardSteps[currentStep].label}
              </h3>
              <p className="mt-1 text-sm text-shell-muted">
                Step {currentStep + 1} of {wizardSteps.length}
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
              disabled={currentStep === 0}
              type="button"
              onClick={goBack}
            >
              Back
            </button>

            {currentStep === wizardSteps.length - 1 ? (
              <button className="shell-action" type="button" onClick={createSource}>
                Create source
              </button>
	            ) : (
	              <button
	                className="shell-action"
	                disabled={Boolean(currentValidationMessage)}
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
