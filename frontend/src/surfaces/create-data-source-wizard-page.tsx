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
import { SyntheticProfileStep, type SyntheticProfileValue } from "./synthetic-profile-step";

type DataSourceResponse = {
  id: string;
  [key: string]: unknown;
};

export type DiscoveredNodeResponse = {
  nodeId: string;
  parentId: string | null;
  path: string;
  name: string;
  kind: string;
  dataType: string | null;
  valueRank: number | null;
  access: string | null;
  unit: string | null;
  description: string | null;
  unknownType: boolean;
};

export type TypeResolutionEntry = {
  nodeId: string;
  dataType: string;
  valueRank: number;
  access: string;
  exclude: boolean;
};

type ScanJobStatus = "PENDING" | "RUNNING" | "COMPLETE" | "ERROR" | "PARTIAL";

type ScanJobResult = {
  jobId: string;
  status: ScanJobStatus;
  truncated: boolean;
  discoveredCount: number;
  unknownCount: number;
  message: string | null;
  nodes: DiscoveredNodeResponse[];
};

type ScanStepStatus = "idle" | "scanning" | "complete" | "error" | "partial";

type ProtocolOption = {
  id: "OPC UA" | "Modbus TCP";
  note: string;
  portHint: string;
};

type SourceBasis = "scan" | "import" | "synthetic";

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
  startCapture: boolean;
};

// Modbus TCP hidden until IS-059 (worker) is implemented.
const protocolOptions: ProtocolOption[] = [
  {
    id: "OPC UA",
    note: "Discovery, recording, and replay against address-rich industrial endpoints.",
    portHint: "Common endpoint: opc.tcp://host:4840",
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
  {
    id: "synthetic",
    label: "Synthetic device",
    note: "Generate values from patterns, reusing an existing source's schema for structure.",
  },
];

export type WizardStepId =
  | "protocol"
  | "basis"
  | "setup"
  | "scan"
  | "recording"
  | "import"
  | "configure"
  | "schedule"
  | "review";
type WizardStep = { id: WizardStepId; label: string };

const SCAN_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "scan", label: "Scan" },
  { id: "recording", label: "Recording" },
  { id: "schedule", label: "Schedule" },
  { id: "review", label: "Review" },
];

const IMPORT_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "import", label: "Import data" },
  { id: "review", label: "Review" },
];

const SYNTHETIC_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "configure", label: "Configure profile" },
  { id: "review", label: "Review" },
];

const DEFAULT_STEPS: WizardStep[] = [
  { id: "protocol", label: "Protocol" },
  { id: "basis", label: "Source basis" },
  { id: "setup", label: "Setup" },
  { id: "schedule", label: "Schedule" },
  { id: "review", label: "Review" },
];

function getActiveSteps(basis: SourceBasis | null): WizardStep[] {
  if (basis === "scan") return SCAN_STEPS;
  if (basis === "import") return IMPORT_STEPS;
  if (basis === "synthetic") return SYNTHETIC_STEPS;
  return DEFAULT_STEPS;
}

/** Lowest port >= base not already used by an existing source, so a new source doesn't collide. */
function nextFreePort(base: number, used: Set<number>): number {
  let port = base;
  while (used.has(port)) port++;
  return port;
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

  if (stepId === "recording") {
    return null;
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

export function scanStepValidationMessage(
  scanStatus: ScanStepStatus,
  typeResolutions: TypeResolutionEntry[],
  scanResult: { nodes: DiscoveredNodeResponse[]; discoveredCount?: number; unknownCount?: number; truncated?: boolean } | null,
): string | null {
  if (scanStatus === "scanning") return "Scanning in progress…";
  if (scanStatus === "error") return "Scan failed";
  if (scanStatus === "idle") return "Scan has not started yet";
  // complete or partial
  if (scanResult) {
    const unknownUnresolved = scanResult.nodes.filter((n) => {
      if (!n.unknownType) return false;
      const res = typeResolutions.find((r) => r.nodeId === n.nodeId);
      if (!res) return true;
      if (res.exclude) return false;
      if (!res.dataType) return true;
      return false;
    });
    if (unknownUnresolved.length > 0) return "Resolve all unknown node types to continue";
  }
  return null;
}

function configureValidationMessage(form: WizardFormState, synthetic: SyntheticProfileValue) {
  if (!form.name.trim()) {
    return "Enter a source name to continue.";
  }
  if (
    !form.simulatorPort.trim() ||
    isNaN(Number(form.simulatorPort)) ||
    Number(form.simulatorPort) < 1 ||
    Number(form.simulatorPort) > 65535
  ) {
    return "Enter a valid simulator port (1–65535).";
  }
  if (!synthetic.schemaFromSourceId) {
    return "Pick an existing source whose schema you want to reuse.";
  }
  if (!synthetic.valid) {
    return "Configure at least one measurement with a valid pattern.";
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


function reviewLines(
  form: WizardFormState,
  selectedRecordingLabel?: string,
  syntheticSummary?: { schemaSourceName: string; measurementCount: number },
) {
  const basisLabel = basisOptions.find((option) => option.id === form.basis)?.label ?? "-";
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
    ...(form.basis === "scan"
      ? [{ label: "After creation", value: form.startCapture ? "Start live capture" : "Open data source" }]
      : []),
    ...(form.basis === "import"
      ? [{ label: "Recording", value: selectedRecordingLabel ?? "-" }]
      : []),
    ...(form.basis === "synthetic" && syntheticSummary
      ? [
          { label: "Schema reused from", value: syntheticSummary.schemaSourceName },
          { label: "Measurements", value: String(syntheticSummary.measurementCount) },
        ]
      : []),
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
  const push = useNotificationStore((state) => state.push);
  const access = resolveAccess(accessMode, sharedRole);

  const artifacts = useArtifactsStore((state) => state.artifacts);
  const isArtifactsLoading = useArtifactsStore((state) => state.isLoading);
  const artifactsError = useArtifactsStore((state) => state.error);
  const loadRecordings = useArtifactsStore((state) => state.loadRecordings);

  const dataSources = useDataSourcesStore((state) => state.dataSources);
  const loadDataSources = useDataSourcesStore((state) => state.loadDataSources);
  const createSyntheticSource = useDataSourcesStore((state) => state.createSyntheticSource);

  const [scanJobId, setScanJobId] = useState<string | null>(null);
  const [scanStatus, setScanStatus] = useState<ScanStepStatus>("idle");
  const [scanTrigger, setScanTrigger] = useState(0);
  const [scanResult, setScanResult] = useState<{
    discoveredCount: number;
    unknownCount: number;
    truncated: boolean;
    nodes: DiscoveredNodeResponse[];
  } | null>(null);
  const [typeResolutions, setTypeResolutions] = useState<TypeResolutionEntry[]>([]);
  const [scanErrorMessage, setScanErrorMessage] = useState<string | null>(null);
  const scanPollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Persists the completed job ID across step transitions so the create call can
  // use it even after the scan-step cleanup has reset scanJobId state to null.
  const scanJobIdForCreateRef = useRef<string | null>(null);

  const [synthetic, setSynthetic] = useState<SyntheticProfileValue>({
    schemaFromSourceId: null,
    config: null,
    valid: false,
    measurementCount: 0,
  });
  const [syntheticSeed, setSyntheticSeed] = useState("");

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
    startCapture: true,
  });

  const activeSteps = getActiveSteps(form.basis);
  const safeStep = Math.min(currentStep, activeSteps.length - 1);
  const activeStepId = activeSteps[safeStep].id;
  const currentValidationMessage =
    activeStepId === "configure"
      ? configureValidationMessage(form, synthetic)
      : activeStepId === "scan"
        ? scanStepValidationMessage(scanStatus, typeResolutions, scanResult)
        : validationMessage(activeStepId, form, accessMode);
  const currentProtocol = protocolOptions.find((option) => option.id === form.protocol) ?? null;
  const currentBasis = basisOptions.find((option) => option.id === form.basis) ?? null;

  const selectedRecording = useMemo(
    () => artifacts.find((a) => a.id === form.importSelectedRecordingId) ?? null,
    [artifacts, form.importSelectedRecordingId],
  );
  const syntheticSchemaSourceName = useMemo(
    () => (dataSources ?? []).find((s) => s.id === synthetic.schemaFromSourceId)?.name ?? "-",
    [dataSources, synthetic.schemaFromSourceId],
  );
  const reviewItems = useMemo(
    () =>
      reviewLines(
        form,
        selectedRecording ? `Recording ${selectedRecording.id.slice(0, 8)}` : undefined,
        form.basis === "synthetic"
          ? { schemaSourceName: syntheticSchemaSourceName, measurementCount: synthetic.measurementCount }
          : undefined,
      ),
    [form, selectedRecording, syntheticSchemaSourceName, synthetic.measurementCount],
  );

  useEffect(() => {
    if (form.basis === "import" && currentProjectId) {
      void loadRecordings(currentProjectId);
    }
    if (form.basis === "synthetic" && currentProjectId) {
      void loadDataSources(currentProjectId);
    }
  }, [form.basis, currentProjectId, loadRecordings, loadDataSources]);

  // Synthetic sources serve their own OPC UA/Modbus port; the per-protocol default (4840/502)
  // collides with any source already using it. Once sources load, bump an empty/default port to
  // the next free one so a new synthetic source can actually start. A user-entered non-default
  // port is left untouched.
  useEffect(() => {
    if (form.basis !== "synthetic") return;
    const used = new Set(
      (dataSources ?? [])
        .map((s) => s.simulatorPort)
        .filter((p): p is number => typeof p === "number"),
    );
    if (used.size === 0) return;
    const base = form.protocol === "OPC UA" ? 4840 : 502;
    const current = Number(form.simulatorPort);
    const isEmptyOrDefault = form.simulatorPort.trim() === "" || current === base;
    if (isEmptyOrDefault) {
      const free = String(nextFreePort(base, used));
      if (free !== form.simulatorPort) updateForm({ simulatorPort: free });
    }
  }, [form.basis, form.protocol, form.simulatorPort, dataSources]);

  // Auto-start scan when the user enters the "scan" step
  useEffect(() => {
    if (activeStepId !== "scan") return;
    if (scanStatus !== "idle") return;
    if (!currentProjectId || !form.protocol) return;

    let cancelled = false;

    async function startScan() {
      setScanStatus("scanning");
      try {
        const job = await apiFetch<{ jobId: string; status: string }>(
          `/api/v1/projects/${currentProjectId}/data-sources/scan`,
          {
            method: "POST",
            body: JSON.stringify({
              protocol: backendProtocolForForm(form.protocol),
              endpointUrl: form.realDeviceEndpoint,
            }),
          },
        );
        if (cancelled) return;
        setScanJobId(job.jobId);
        scanJobIdForCreateRef.current = job.jobId;

        // Start polling
        scanPollRef.current = setInterval(async () => {
          try {
            const result = await apiFetch<ScanJobResult>(
              `/api/v1/projects/${currentProjectId}/data-sources/scan/${job.jobId}`,
            );
            if (cancelled) return;
            if (result.status === "COMPLETE" || result.status === "PARTIAL") {
              if (scanPollRef.current !== null) {
                clearInterval(scanPollRef.current);
                scanPollRef.current = null;
              }
              setScanStatus(result.status === "COMPLETE" ? "complete" : "partial");
              setScanResult({
                discoveredCount: result.discoveredCount,
                unknownCount: result.unknownCount,
                truncated: result.truncated,
                nodes: result.nodes,
              });
              // Pre-populate typeResolutions for unknown nodes
              const unknownNodes = result.nodes.filter((n) => n.unknownType);
              setTypeResolutions(
                unknownNodes.map((n) => ({
                  nodeId: n.nodeId,
                  dataType: "",
                  valueRank: n.valueRank ?? 1,
                  access: n.access ?? "READ",
                  exclude: false,
                })),
              );
            } else if (result.status === "ERROR") {
              if (scanPollRef.current !== null) {
                clearInterval(scanPollRef.current);
                scanPollRef.current = null;
              }
              setScanStatus("error");
              setScanErrorMessage(result.message ?? "Scan failed");
            } else {
              // PENDING or RUNNING — update live count
              setScanResult((prev) =>
                prev
                  ? { ...prev, discoveredCount: result.discoveredCount }
                  : {
                      discoveredCount: result.discoveredCount,
                      unknownCount: result.unknownCount,
                      truncated: result.truncated,
                      nodes: result.nodes,
                    },
              );
            }
          } catch {
            if (!cancelled) {
              if (scanPollRef.current !== null) {
                clearInterval(scanPollRef.current);
                scanPollRef.current = null;
              }
              setScanStatus("error");
              setScanErrorMessage("Failed to poll scan status");
            }
          }
        }, 2000);
      } catch (err) {
        if (!cancelled) {
          setScanStatus("error");
          setScanErrorMessage(err instanceof Error ? err.message : "Failed to start scan");
        }
      }
    }

    void startScan();

    return () => {
      cancelled = true;
      if (scanPollRef.current !== null) {
        clearInterval(scanPollRef.current);
        scanPollRef.current = null;
      }
      setScanStatus("idle");
      setScanResult(null);
      setScanJobId(null);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeStepId, scanTrigger]);

  // Cleanup polling interval on unmount
  useEffect(() => {
    return () => {
      if (scanPollRef.current !== null) {
        clearInterval(scanPollRef.current);
        scanPollRef.current = null;
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

    if (form.basis === "synthetic") {
      if (!synthetic.config || !synthetic.valid) {
        setShowValidation(true);
        return;
      }
      try {
        const createdId = await createSyntheticSource({
          projectId: currentProjectId,
          name: form.name.trim(),
          protocol: form.protocol,
          simulatorPort: Number(form.simulatorPort),
          config: synthetic.config,
          schemaFromSourceId: synthetic.schemaFromSourceId,
        });
        navigate(`/data-sources/${createdId}`);
      } catch (err) {
        console.error("[createSyntheticSource]", err);
        const title = err instanceof Error ? err.message : "Failed to create synthetic source";
        push({ tone: "error", title });
      }
      return;
    }

    if (form.basis === "scan" && scanJobIdForCreateRef.current) {
      try {
        const data = await apiFetch<DataSourceResponse>(
          `/api/v1/projects/${currentProjectId}/data-sources/scan/${scanJobIdForCreateRef.current}/create`,
          {
            method: "POST",
            body: JSON.stringify({
              name: form.name.trim(),
              realDeviceEndpoint: form.realDeviceEndpoint,
              ...(typeResolutions.length > 0 ? { typeResolutions } : {}),
            }),
          },
        );
        if (form.startCapture) {
          navigate(`/data-sources/${data.id}/record`);
        } else {
          navigate(`/data-sources/${data.id}`);
        }
      } catch (err) {
        console.error("[createSource/scan]", err);
        const title = err instanceof Error ? err.message : "Failed to create source from scan";
        push({ tone: "error", title });
      }
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
                            {artifact.name || `Recording ${artifact.id.slice(0, 8)}`}
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

  function handleTypeResolutionChange(nodeId: string, patch: Partial<TypeResolutionEntry>) {
    setTypeResolutions((prev) =>
      prev.map((entry) => (entry.nodeId === nodeId ? { ...entry, ...patch } : entry)),
    );
  }

  function retryScan() {
    setScanStatus("idle");
    setScanJobId(null);
    setScanResult(null);
    setTypeResolutions([]);
    setScanErrorMessage(null);
    setScanTrigger((t) => t + 1);
  }

  function renderScanStep() {
    if (scanStatus === "idle" || scanStatus === "scanning") {
      return (
        <div className="space-y-4">
          <section className="rounded-md border border-shell-line bg-white px-4 py-6 text-center">
            <p className="text-sm font-medium text-shell-ink">
              {scanStatus === "idle" ? "Starting scan…" : "Scanning…"}
            </p>
            {scanResult ? (
              <p className="mt-2 text-sm text-shell-muted">
                Discovered {scanResult.discoveredCount} nodes
              </p>
            ) : (
              <p className="mt-2 text-sm text-shell-muted">Connecting to endpoint</p>
            )}
          </section>
        </div>
      );
    }

    if (scanStatus === "error") {
      return (
        <div className="space-y-4">
          <section className="rounded-md border border-shell-danger/40 bg-shell-danger/5 px-4 py-4">
            <p className="text-sm font-medium text-shell-danger">Scan failed</p>
            {scanErrorMessage ? (
              <p className="mt-2 text-sm text-shell-muted">{scanErrorMessage}</p>
            ) : null}
          </section>
          <button className="shell-action" type="button" onClick={retryScan}>
            Retry
          </button>
        </div>
      );
    }

    // complete or partial
    if (!scanResult) return null;

    const unknownNodes = scanResult.nodes.filter((n) => n.unknownType);

    return (
      <div className="space-y-4">
        <section className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-sm font-medium text-shell-ink">
            Discovered {scanResult.discoveredCount} nodes
          </p>
          {scanResult.unknownCount > 0 ? (
            <p className="mt-1 text-sm text-shell-muted">
              {scanResult.unknownCount} unknown types need resolution
            </p>
          ) : null}
          {scanStatus === "partial" ? (
            <p className="mt-1 text-sm text-shell-muted">
              Partial results — some nodes may not have been discovered.
            </p>
          ) : null}
        </section>

        {scanResult.truncated ? (
          <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            Scan was truncated — only the first {scanResult.discoveredCount} nodes are shown.
          </section>
        ) : null}

        {unknownNodes.length > 0 ? (
          <div className="space-y-3">
            <p className="text-sm font-medium text-shell-ink">
              Resolve unknown node types
            </p>
            <p className="text-sm text-shell-muted">
              The following nodes have unknown types. Choose a data type or exclude them from the source.
            </p>
            <ul className="space-y-2">
              {unknownNodes.map((node) => {
                const res = typeResolutions.find((r) => r.nodeId === node.nodeId);
                return (
                  <li
                    key={node.nodeId}
                    className="flex flex-wrap items-center gap-3 rounded-md border border-shell-line bg-white px-4 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-shell-ink">
                        {node.name || node.nodeId}
                      </p>
                      <p className="truncate text-xs text-shell-muted">{node.path || node.nodeId}</p>
                    </div>
                    <select
                      aria-label={`Data type for ${node.name || node.nodeId}`}
                      className="shell-field w-40 shrink-0"
                      value={res?.exclude ? "__exclude__" : (res?.dataType ?? "")}
                      onChange={(e) => {
                        if (e.target.value === "__exclude__") {
                          handleTypeResolutionChange(node.nodeId, { exclude: true, dataType: "" });
                        } else {
                          handleTypeResolutionChange(node.nodeId, {
                            exclude: false,
                            dataType: e.target.value,
                          });
                        }
                      }}
                    >
                      <option value="">Choose type…</option>
                      <option value="FLOAT64">FLOAT64</option>
                      <option value="INT32">INT32</option>
                      <option value="BOOL">BOOL</option>
                      <option value="STRING">STRING</option>
                      <option value="__exclude__">Exclude</option>
                    </select>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : null}
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

  function renderConfigureStep() {
    return (
      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-2 text-sm text-shell-muted">
            Source name
            <input
              className="shell-field"
              placeholder="Synthetic Line A"
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

        <SyntheticProfileStep
          projectId={currentProjectId}
          sources={dataSources ?? []}
          seed={syntheticSeed}
          onSeedChange={setSyntheticSeed}
          onChange={setSynthetic}
        />
      </div>
    );
  }

  function renderRecordingStep() {
    return (
      <div className="space-y-3">
        <button
          className={optionButtonClass(form.startCapture)}
          type="button"
          onClick={() => updateForm({ startCapture: true })}
        >
          <p className="text-sm font-medium text-shell-ink">Start live capture</p>
          <p className="mt-1 text-sm text-shell-muted">
            After the source is created, immediately open the live recording flow to capture data from the device.
          </p>
        </button>
        <button
          className={optionButtonClass(!form.startCapture)}
          type="button"
          onClick={() => updateForm({ startCapture: false })}
        >
          <p className="text-sm font-medium text-shell-ink">Skip — just create the source</p>
          <p className="mt-1 text-sm text-shell-muted">
            Create the data source only. You can start a recording from the Recordings page at any time.
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

    if (activeStepId === "scan") {
      return renderScanStep();
    }

    if (activeStepId === "recording") {
      return renderRecordingStep();
    }

    if (activeStepId === "configure") {
      return renderConfigureStep();
    }

    if (activeStepId === "schedule") {
      return renderScheduleStep();
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
