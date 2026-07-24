import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, mapDataType } from "../api";
import { resolveAccess } from "../shell/access-policy";
import { useManualSchemasStore } from "../shell/manual-schemas-store";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { buildTree, canHaveChildren, type NodeDto, type ReferenceDto } from "./data-source-schema-editor";
import { TemplatePickerModal, type TemplateInfo } from "./template-picker-modal";

const DATA_TYPES = [
  "BOOL", "INT8", "UINT8", "INT16", "UINT16", "INT32", "UINT32", "INT64", "UINT64",
  "FLOAT32", "FLOAT64", "STRING", "BYTES", "DATETIME", "LOCALIZED_TEXT", "GUID",
  "STATUS_CODE", "QUALIFIED_NAME", "NODE_ID", "EXPANDED_NODE_ID", "XML_ELEMENT",
] as const;

const SUGGESTED_VARIABLES = [
  { group: "Measurements", name: "Temperature", dataType: "FLOAT64", unit: "°C", description: "Process temperature" },
  { group: "Measurements", name: "Pressure", dataType: "FLOAT64", unit: "bar", description: "Process pressure" },
  { group: "Measurements", name: "FlowRate", dataType: "FLOAT64", unit: "m³/h", description: "Volumetric flow rate" },
  { group: "Measurements", name: "Level", dataType: "FLOAT64", unit: "%", description: "Tank or vessel level" },
  { group: "Measurements", name: "Speed", dataType: "FLOAT64", unit: "rpm", description: "Rotational speed" },
  { group: "Measurements", name: "Voltage", dataType: "FLOAT64", unit: "V", description: "Electrical voltage" },
  { group: "Measurements", name: "Current", dataType: "FLOAT64", unit: "A", description: "Electrical current" },
  { group: "Measurements", name: "Power", dataType: "FLOAT64", unit: "kW", description: "Active power" },
  { group: "Measurements", name: "Energy", dataType: "FLOAT64", unit: "kWh", description: "Accumulated energy" },
  { group: "Control", name: "Setpoint", dataType: "FLOAT64", unit: null, description: "Requested target value" },
  { group: "Control", name: "Enabled", dataType: "BOOL", unit: null, description: "Whether the component is enabled" },
  { group: "Control", name: "Mode", dataType: "STRING", unit: null, description: "Selected operating mode" },
  { group: "Control", name: "Start", dataType: "BOOL", unit: null, description: "Start command" },
  { group: "Control", name: "Stop", dataType: "BOOL", unit: null, description: "Stop command" },
  { group: "Control", name: "Reset", dataType: "BOOL", unit: null, description: "Reset command" },
  { group: "Device information", name: "Status", dataType: "UINT16", unit: null, description: "Device status code" },
  { group: "Device information", name: "DeviceId", dataType: "STRING", unit: null, description: "Device identifier" },
  { group: "Device information", name: "SerialNumber", dataType: "STRING", unit: null, description: "Device serial number" },
  { group: "Device information", name: "Manufacturer", dataType: "STRING", unit: null, description: "Device manufacturer" },
  { group: "Device information", name: "FirmwareVersion", dataType: "STRING", unit: null, description: "Installed firmware version" },
  { group: "Device information", name: "Timestamp", dataType: "DATETIME", unit: null, description: "Time of the last update" },
  { group: "Limits & diagnostics", name: "HighLimit", dataType: "FLOAT64", unit: null, description: "Configured upper operating limit" },
  { group: "Limits & diagnostics", name: "LowLimit", dataType: "FLOAT64", unit: null, description: "Configured lower operating limit" },
  { group: "Limits & diagnostics", name: "Quality", dataType: "STATUS_CODE", unit: null, description: "Quality of the current value" },
  { group: "Limits & diagnostics", name: "DiagnosticMessage", dataType: "LOCALIZED_TEXT", unit: null, description: "Human-readable diagnostic information" },
  { group: "Simulation", name: "Counter", dataType: "FLOAT64", unit: null, description: "Monotonically increasing simulated value" },
  { group: "Simulation", name: "Random", dataType: "FLOAT64", unit: null, description: "Random simulated value" },
  { group: "Simulation", name: "Sawtooth", dataType: "FLOAT64", unit: null, description: "Sawtooth simulated signal" },
  { group: "Simulation", name: "Sinusoid", dataType: "FLOAT64", unit: null, description: "Sinusoidal simulated signal" },
  { group: "Simulation", name: "Square", dataType: "FLOAT64", unit: null, description: "Square-wave simulated signal" },
  { group: "Simulation", name: "Triangle", dataType: "FLOAT64", unit: null, description: "Triangle-wave simulated signal" },
  { group: "Static data", name: "BooleanValue", dataType: "BOOL", unit: null, description: "Static Boolean value" },
  { group: "Static data", name: "StringValue", dataType: "STRING", unit: null, description: "Static text value" },
  { group: "Static data", name: "DateTimeValue", dataType: "DATETIME", unit: null, description: "Static date and time value" },
  { group: "Static data", name: "NodeIdValue", dataType: "NODE_ID", unit: null, description: "Static OPC UA NodeId value" },
  { group: "Analog & data items", name: "EngineeringUnits", dataType: "STRING", unit: null, description: "Engineering units label" },
  { group: "Analog & data items", name: "EURangeLow", dataType: "FLOAT64", unit: null, description: "Engineering range lower bound" },
  { group: "Analog & data items", name: "EURangeHigh", dataType: "FLOAT64", unit: null, description: "Engineering range upper bound" },
  { group: "Analog & data items", name: "Definition", dataType: "STRING", unit: null, description: "Data item definition" },
  { group: "State & access", name: "State", dataType: "UINT32", unit: null, description: "Multi-state value" },
  { group: "State & access", name: "AccessLevel", dataType: "UINT8", unit: null, description: "OPC UA access-level flags" },
  { group: "State & access", name: "UserAccessLevel", dataType: "UINT8", unit: null, description: "User-specific access-level flags" },
  { group: "State & access", name: "Alias", dataType: "NODE_ID", unit: null, description: "Alias target NodeId" },
] as const;

const PARAMETER_GROUPS = ["Measurements", "Control", "Device information", "Limits & diagnostics", "Simulation", "Static data", "Analog & data items", "State & access"] as const;

type StructureTemplate = {
  group: string;
  name: string;
  description: string;
  variables: Array<{ name: string; dataType: string; description: string; unit?: string }>;
};

const STRUCTURE_TEMPLATES = [
  {
    group: "Process equipment", name: "Tank / vessel", description: "A vessel with level, process measurements, limits, and status.",
    variables: [
      { name: "Level", dataType: "FLOAT64", unit: "%", description: "Tank or vessel level" },
      { name: "Temperature", dataType: "FLOAT64", unit: "°C", description: "Process temperature" },
      { name: "Pressure", dataType: "FLOAT64", unit: "bar", description: "Process pressure" },
      { name: "HighLimit", dataType: "FLOAT64", description: "Configured upper operating limit" },
      { name: "LowLimit", dataType: "FLOAT64", description: "Configured lower operating limit" },
      { name: "Status", dataType: "UINT16", description: "Vessel status code" },
    ],
  },
  {
    group: "Process equipment", name: "Pump", description: "A pump with commands, operating state, speed, pressure, and flow.",
    variables: [
      { name: "Enabled", dataType: "BOOL", description: "Whether the pump is enabled" },
      { name: "Running", dataType: "BOOL", description: "Whether the pump is running" },
      { name: "Start", dataType: "BOOL", description: "Start command" },
      { name: "Stop", dataType: "BOOL", description: "Stop command" },
      { name: "Speed", dataType: "FLOAT64", unit: "rpm", description: "Pump speed" },
      { name: "Pressure", dataType: "FLOAT64", unit: "bar", description: "Pump discharge pressure" },
      { name: "FlowRate", dataType: "FLOAT64", unit: "m³/h", description: "Pump flow rate" },
      { name: "Status", dataType: "UINT16", description: "Pump status code" },
    ],
  },
  {
    group: "Process equipment", name: "Motor", description: "A motor with control, operating state, and electrical measurements.",
    variables: [
      { name: "Enabled", dataType: "BOOL", description: "Whether the motor is enabled" },
      { name: "Running", dataType: "BOOL", description: "Whether the motor is running" },
      { name: "Start", dataType: "BOOL", description: "Start command" },
      { name: "Stop", dataType: "BOOL", description: "Stop command" },
      { name: "Speed", dataType: "FLOAT64", unit: "rpm", description: "Rotational speed" },
      { name: "Current", dataType: "FLOAT64", unit: "A", description: "Motor current" },
      { name: "Voltage", dataType: "FLOAT64", unit: "V", description: "Motor voltage" },
      { name: "Power", dataType: "FLOAT64", unit: "kW", description: "Motor power" },
      { name: "Temperature", dataType: "FLOAT64", unit: "°C", description: "Motor temperature" },
    ],
  },
  {
    group: "Process equipment", name: "Valve", description: "A valve with position feedback, a target position, commands, and status.",
    variables: [
      { name: "Position", dataType: "FLOAT64", unit: "%", description: "Actual valve position" },
      { name: "Setpoint", dataType: "FLOAT64", unit: "%", description: "Requested valve position" },
      { name: "Open", dataType: "BOOL", description: "Open command" },
      { name: "Close", dataType: "BOOL", description: "Close command" },
      { name: "Enabled", dataType: "BOOL", description: "Whether the valve is enabled" },
      { name: "Status", dataType: "UINT16", description: "Valve status code" },
    ],
  },
  {
    group: "Simulation", name: "Simulation signals", description: "A folder with common generated signals.",
    variables: [
      { name: "Counter", dataType: "FLOAT64", description: "Monotonically increasing simulated value" },
      { name: "Random", dataType: "FLOAT64", description: "Random simulated value" },
      { name: "Sinusoid", dataType: "FLOAT64", description: "Sinusoidal simulated signal" },
      { name: "Square", dataType: "FLOAT64", description: "Square-wave simulated signal" },
      { name: "Triangle", dataType: "FLOAT64", description: "Triangle-wave simulated signal" },
    ],
  },
  {
    group: "Device information", name: "Device identity", description: "A folder with common device identity and status values.",
    variables: [
      { name: "DeviceId", dataType: "STRING", description: "Device identifier" },
      { name: "SerialNumber", dataType: "STRING", description: "Device serial number" },
      { name: "Manufacturer", dataType: "STRING", description: "Device manufacturer" },
      { name: "FirmwareVersion", dataType: "STRING", description: "Installed firmware version" },
      { name: "Status", dataType: "UINT16", description: "Device status code" },
      { name: "Timestamp", dataType: "DATETIME", description: "Time of the last update" },
    ],
  },
  {
    group: "Analog & data items", name: "Analog item", description: "A folder with a value and engineering-range metadata.",
    variables: [
      { name: "Value", dataType: "FLOAT64", description: "Measured analog value" },
      { name: "EURangeLow", dataType: "FLOAT64", description: "Engineering range lower bound" },
      { name: "EURangeHigh", dataType: "FLOAT64", description: "Engineering range upper bound" },
      { name: "EngineeringUnits", dataType: "STRING", description: "Engineering units label" },
    ],
  },
  {
    group: "Conditions", name: "Condition values", description: "Common condition state, severity, acknowledgement, and message values.",
    variables: [
      { name: "ActiveState", dataType: "LOCALIZED_TEXT", description: "Current active state" },
      { name: "AckedState", dataType: "LOCALIZED_TEXT", description: "Current acknowledgement state" },
      { name: "EnabledState", dataType: "LOCALIZED_TEXT", description: "Current enabled state" },
      { name: "Severity", dataType: "UINT16", description: "Current condition severity" },
      { name: "LastSeverity", dataType: "UINT16", description: "Previous condition severity" },
      { name: "Message", dataType: "LOCALIZED_TEXT", description: "Operator-facing condition message" },
      { name: "Comment", dataType: "LOCALIZED_TEXT", description: "Operator comment" },
    ],
  },
  {
    group: "Static data", name: "Static data", description: "Reusable scalar examples for fixed or slowly changing values.",
    variables: [
      { name: "BooleanValue", dataType: "BOOL", description: "Static Boolean value" },
      { name: "StringValue", dataType: "STRING", description: "Static text value" },
      { name: "DateTimeValue", dataType: "DATETIME", description: "Static date and time value" },
      { name: "NodeIdValue", dataType: "NODE_ID", description: "Static OPC UA NodeId value" },
      { name: "Quality", dataType: "STATUS_CODE", description: "Quality of the static values" },
    ],
  },
  {
    group: "State & access", name: "Access and state", description: "State values, access flags, and alias examples for a server branch.",
    variables: [
      { name: "State", dataType: "UINT32", description: "Multi-state value" },
      { name: "AccessLevel", dataType: "UINT8", description: "OPC UA access-level flags" },
      { name: "UserAccessLevel", dataType: "UINT8", description: "User-specific access-level flags" },
      { name: "Alias", dataType: "NODE_ID", description: "Alias target NodeId" },
      { name: "Enabled", dataType: "BOOL", description: "Whether the branch is enabled" },
    ],
  },
] as const satisfies readonly StructureTemplate[];

const VALUE_RANKS = ["SCALAR", "ARRAY"] as const;
const ACCESS_LEVELS = ["READ", "READ_WRITE"] as const;
const UPCOMING_NODE_CLASSES = ["Method", "DataType"] as const;
const REFERENCE_TYPES = ["ORGANIZES", "HAS_COMPONENT", "HAS_PROPERTY", "HAS_TYPE_DEFINITION", "GENERIC"] as const;

function referenceTypeLabel(type: ReferenceDto["type"]): string {
  switch (type) {
    case "ORGANIZES": return "Organizes";
    case "HAS_COMPONENT": return "Has component";
    case "HAS_PROPERTY": return "Has property";
    case "HAS_TYPE_DEFINITION": return "Has type definition";
    case "GENERIC": return "Generic";
  }
}

type ValidationIssue = { nodeId: string; message: string };
type BatchVariableRow = { id: string; name: string; dataType: string; unit: string; description: string };

export function validateManualSchemaNodes(nodes: NodeDto[]): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const byId = new Map(nodes.map((node) => [node.nodeId, node]));
  const siblingNames = new Map<string, Set<string>>();
  for (const node of nodes) {
    const name = node.name.trim();
    if (!name) issues.push({ nodeId: node.nodeId, message: "A node needs a browse name." });
    else if (/[\\/]/.test(name)) issues.push({ nodeId: node.nodeId, message: "A browse name cannot contain a slash or backslash." });
    if (node.parentId) {
      const parent = byId.get(node.parentId);
      if (!parent) issues.push({ nodeId: node.nodeId, message: "Its parent no longer exists." });
      else if (!canHaveChildren(parent.kind)) issues.push({ nodeId: node.nodeId, message: "Only a folder, object, or variable can contain another node." });
      // IS-189: When parent is VARIABLE, the parent must have a reference (HasProperty or HasComponent) to this child
      else if (parent.kind === "VARIABLE") {
        const hasValidRef = (parent.references ?? []).some((ref) =>
          (ref.type === "HAS_PROPERTY" || ref.type === "HAS_COMPONENT") && ref.targetNodeId === node.nodeId
        );
        if (!hasValidRef) {
          issues.push({ nodeId: node.nodeId, message: "Variable parent must have a reference (HasProperty or HasComponent) to this child." });
        }
      }
    }
    const key = `${node.parentId ?? "__top_level__"}:${name}`;
    const ids = siblingNames.get(key) ?? new Set<string>();
    ids.add(node.nodeId);
    siblingNames.set(key, ids);
  }
  for (const ids of siblingNames.values()) {
    if (ids.size > 1) ids.forEach((nodeId) => issues.push({ nodeId, message: "Sibling nodes must have unique browse names." }));
  }
  return issues;
}

function newNodeId(): string {
  return `node-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** Every id in `nodes` that is `rootId` or a (possibly indirect) descendant of it. */
function collectSubtreeIds(nodes: NodeDto[], rootId: string): Set<string> {
  const ids = new Set([rootId]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const n of nodes) {
      if (n.parentId && ids.has(n.parentId) && !ids.has(n.nodeId)) {
        ids.add(n.nodeId);
        grew = true;
      }
    }
  }
  return ids;
}

export function formatDataType(dataType: string): string {
  const parts = dataType.split('_').map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase());
  const formatted = parts.join('');
  return formatted === 'Datetime' ? 'DateTime' : formatted;
}

function typeLabel(dataType: string): string {
  const mapped = mapDataType(dataType as Parameters<typeof mapDataType>[0]);
  return mapped ? mapped.charAt(0).toUpperCase() + mapped.slice(1) : dataType;
}

type SaveMode = "in-place" | "save-as";

export function ManualSchemaEditorPage() {
  const { schemaId } = useParams<{ schemaId: string }>();
  const navigate = useNavigate();
  const accessMode = useShellStore((s) => s.accessMode);
  const sharedRole = useShellStore((s) => s.sharedRole);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);
  const push = useNotificationStore((s) => s.push);

  const loadManualSchemaById = useManualSchemasStore((s) => s.loadManualSchemaById);
  const updateManualSchema = useManualSchemasStore((s) => s.updateManualSchema);
  const createManualSchema = useManualSchemasStore((s) => s.createManualSchema);

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [protocol, setProtocol] = useState("OPC_UA");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [nodes, setNodes] = useState<NodeDto[]>([]);
  const [savedSnapshot, setSavedSnapshot] = useState<{ name: string; description: string; nodes: NodeDto[] }>({
    name: "",
    description: "",
    nodes: [],
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [addKind, setAddKind] = useState<"FOLDER" | "OBJECT" | "VARIABLE" | null>(null);
  const [addName, setAddName] = useState("");
  const [addType, setAddType] = useState<string>("FLOAT64");
  const [addValueRank, setAddValueRank] = useState<string>("SCALAR");
  const [addAccess, setAddAccess] = useState<string>("READ");
  const [addUnit, setAddUnit] = useState("");
  const [addDescription, setAddDescription] = useState("");
  const [selectedSuggestion, setSelectedSuggestion] = useState("");
  const [addParentId, setAddParentId] = useState<string | null>(null);
  const [addRefTargetId, setAddRefTargetId] = useState<string>("");
  const [addRefType, setAddRefType] = useState<ReferenceDto["type"]>("ORGANIZES");
  const [batchRows, setBatchRows] = useState<BatchVariableRow[]>([]);
  const [showLibrary, setShowLibrary] = useState(false);
  const [showBatch, setShowBatch] = useState(false);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [saveMode, setSaveMode] = useState<SaveMode | null>(null);
  const [saveAsName, setSaveAsName] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [showOpcUaAttributes, setShowOpcUaAttributes] = useState(false);
  const [showTemplatePickerModal, setShowTemplatePickerModal] = useState(false);

  useEffect(() => {
    if (!currentProjectId || !schemaId) return;
    setIsLoading(true);
    setLoadError(null);
    loadManualSchemaById(currentProjectId, schemaId)
      .then((schema) => {
        setProtocol(schema.protocol);
        setName(schema.name);
        setDescription(schema.description ?? "");
        setNodes(schema.nodes);
        setSavedSnapshot({
          name: schema.name,
          description: schema.description ?? "",
          nodes: schema.nodes,
        });
      })
      .catch((err) => {
        const msg = err instanceof ApiError ? err.title : "Failed to load manual schema";
        setLoadError(msg);
      })
      .finally(() => setIsLoading(false));
  }, [currentProjectId, schemaId, loadManualSchemaById]);

  const hasUnsavedChanges = useMemo(() => {
    return (
      name !== savedSnapshot.name ||
      description !== savedSnapshot.description ||
      JSON.stringify(nodes) !== JSON.stringify(savedSnapshot.nodes)
    );
  }, [name, description, nodes, savedSnapshot]);

  const treeRoots = useMemo(() => buildTree(nodes), [nodes]);
  const selectedNode = nodes.find((n) => n.nodeId === selectedId) ?? null;
  const variableCount = nodes.filter((n) => n.kind === "VARIABLE").length;
  const containers = nodes.filter((n) => canHaveChildren(n.kind));
  const isEmpty = nodes.length === 0;
  const catalogParentId = selectedNode && canHaveChildren(selectedNode.kind) ? selectedNode.nodeId : null;
  const validationIssues = useMemo(() => validateManualSchemaNodes(nodes), [nodes]);
  const selectedIssues = selectedId ? validationIssues.filter((issue) => issue.nodeId === selectedId) : [];

  function toggleExpand(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function addReference() {
    if (!selectedNode || !addRefTargetId || addRefTargetId === selectedNode.nodeId) return;
    const reference: ReferenceDto = { targetNodeId: addRefTargetId, type: addRefType, forward: true };
    updateSelectedNode({ references: [...(selectedNode.references ?? []), reference] });
    setAddRefTargetId("");
  }

  function removeReference(index: number) {
    if (!selectedNode) return;
    updateSelectedNode({ references: (selectedNode.references ?? []).filter((_, i) => i !== index) });
  }

  function updateSelectedNode(patch: Partial<NodeDto>) {
    if (!selectedId) return;
    if (patch.name !== undefined) {
      renameNode(selectedId, patch.name);
      return;
    }
    setNodes((prev) => prev.map((n) => (n.nodeId === selectedId ? { ...n, ...patch } : n)));
  }

  /**
   * Renaming a node changes its own path (parentPath + "/" + name); when the node is a
   * folder, every descendant's path carries the old prefix and must be rewritten too, or
   * the saved schema would have structurally inconsistent paths (a child's path no longer
   * starting under its own parent's).
   */
  function renameNode(nodeId: string, newName: string) {
    setNodes((prev) => {
      const target = prev.find((n) => n.nodeId === nodeId);
      if (!target) return prev;
      const oldPath = target.path;
      const parent = target.parentId ? prev.find((n) => n.nodeId === target.parentId) : null;
      const newPath = parent ? `${parent.path}/${newName}` : `/${newName}`;
      return prev.map((n) => {
        if (n.nodeId === nodeId) {
          return { ...n, name: newName, path: newPath };
        }
        if (n.path === oldPath || n.path.startsWith(`${oldPath}/`)) {
          return { ...n, path: newPath + n.path.slice(oldPath.length) };
        }
        return n;
      });
    });
  }

  function pathFor(parentId: string | null, name: string): string {
    if (!parentId) return `/${name}`;
    const parent = nodes.find((n) => n.nodeId === parentId);
    return parent ? `${parent.path}/${name}` : `/${name}`;
  }

  function handleAddNode() {
    const trimmed = addName.trim();
    if (!trimmed || !addKind) return;
    const parentId = addParentId;
    const node: NodeDto = {
      nodeId: newNodeId(),
      parentId,
      path: pathFor(parentId, trimmed),
      name: trimmed,
      kind: addKind,
      dataType: addKind === "VARIABLE" ? addType : null,
      valueRank: addKind === "VARIABLE" ? addValueRank : null,
      access: addKind === "VARIABLE" ? addAccess : null,
      unit: addKind === "VARIABLE" ? addUnit || null : null,
      description: addDescription || null,
      accessLevelFull: null,
      minimumSamplingInterval: null,
      writeMask: null,
      historizing: null,
    };
    setNodes((prev) => [...prev, node]);
    if (parentId) setExpandedIds((prev) => new Set(prev).add(parentId));
    if (addKind === "FOLDER") setSelectedId(node.nodeId);
    setAddName("");
    setAddUnit("");
    setAddDescription("");
    setAddKind(null);
  }

  function applySuggestedVariable(name: string) {
    const suggestion = SUGGESTED_VARIABLES.find((variable) => variable.name === name);
    if (!suggestion) return;
    setAddName(suggestion.name);
    setAddType(suggestion.dataType);
    setAddUnit(suggestion.unit ?? "");
    setAddDescription(suggestion.description);
    setSelectedSuggestion("");
  }

  function openAdd(kind: "FOLDER" | "OBJECT" | "VARIABLE") {
    setAddKind(kind);
    setAddParentId(selectedNode && canHaveChildren(selectedNode.kind) ? selectedNode.nodeId : null);
  }

  function addSuggestedVariable(variable: (typeof SUGGESTED_VARIABLES)[number]) {
    const parentId = catalogParentId;
    if (!parentId) return;
    const node: NodeDto = {
      nodeId: newNodeId(), parentId, path: pathFor(parentId, variable.name),
      name: variable.name, kind: "VARIABLE", dataType: variable.dataType, valueRank: "SCALAR",
      access: "READ", unit: variable.unit, description: variable.description,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null,
    };
    setNodes((prev) => [...prev, node]);
    setExpandedIds((prev) => new Set(prev).add(parentId));
  }

  function addStructureTemplate(template: StructureTemplate) {
    const parentId = catalogParentId;
    if (!parentId) return;
    const folderId = newNodeId();
    const folderPath = pathFor(parentId, template.name);
    const folder: NodeDto = {
      nodeId: folderId, parentId, path: folderPath, name: template.name, kind: "FOLDER",
      dataType: null, valueRank: null, access: null, unit: null, description: template.description,
    };
    const variables: NodeDto[] = template.variables.map((variable) => ({
      nodeId: newNodeId(), parentId: folderId, path: `${folderPath}/${variable.name}`, name: variable.name,
      kind: "VARIABLE", dataType: variable.dataType, valueRank: "SCALAR", access: "READ",
      unit: variable.unit ?? null, description: variable.description,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null,
    }));
    setNodes((prev) => [...prev, folder, ...variables]);
    setExpandedIds((prev) => new Set([...prev, parentId, folderId]));
  }

  function handleTemplateSelection(templateName: string) {
    const template = STRUCTURE_TEMPLATES.find((t) => t.name === templateName);
    if (template) {
      addStructureTemplate(template);
      setShowTemplatePickerModal(false);
    }
  }

  const availableTemplates: TemplateInfo[] = STRUCTURE_TEMPLATES.map((template) => ({
    name: template.name,
    group: template.group,
    description: template.description,
    variableCount: template.variables.length,
  }));

  function appendBatchRow() {
    setBatchRows((prev) => [...prev, { id: newNodeId(), name: "", dataType: "FLOAT64", unit: "", description: "" }]);
  }

  function addBatchNodes() {
    const completeRows = batchRows.filter((row) => row.name.trim());
    if (completeRows.length === 0) return;
    const created: NodeDto[] = completeRows.map((row) => ({
      nodeId: newNodeId(), parentId: addParentId, path: pathFor(addParentId, row.name.trim()), name: row.name.trim(),
      kind: "VARIABLE", dataType: row.dataType, valueRank: "SCALAR", access: "READ",
      unit: row.unit.trim() || null, description: row.description.trim() || null,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null,
    }));
    setNodes((prev) => [...prev, ...created]);
    if (addParentId) setExpandedIds((prev) => new Set(prev).add(addParentId));
    setBatchRows([]);
  }

  function handleDeleteNode(nodeId: string) {
    // Removing a folder drops its whole subtree, matching how deleting a scanned
    // folder would leave no orphaned children behind. The mutation itself derives
    // toRemove from the updater's own `prev`, not the outer `nodes` closure, so it's
    // correct even if another pending update hasn't flushed into `nodes` yet; the
    // selection check below recomputes the same (pure, cheap) traversal against the
    // current `nodes` rather than trying to read a value out of the updater — a
    // setState updater's own execution timing isn't something callers can rely on.
    setNodes((prev) => {
      const toRemove = collectSubtreeIds(prev, nodeId);
      return prev.filter((n) => !toRemove.has(n.nodeId));
    });
    if (selectedId && collectSubtreeIds(nodes, nodeId).has(selectedId)) setSelectedId(null);
  }

  function openSaveDialog() {
    if (!hasUnsavedChanges || validationIssues.length > 0) return;
    setSaveAsName(`${name} (copy)`);
    setSaveMode("in-place");
  }

  async function handleSaveInPlace() {
    if (!currentProjectId || !schemaId) return;
    setIsSaving(true);
    try {
      const schema = await updateManualSchema(currentProjectId, schemaId, {
        name,
        description: description || null,
        nodes,
      });
      setSavedSnapshot({ name: schema.name, description: schema.description ?? "", nodes: schema.nodes });
      setNodes(schema.nodes);
      setSaveMode(null);
      push({ tone: "success", title: "Manual schema saved." });
    } catch (err) {
      const title = err instanceof ApiError ? (err.detail ?? err.title) : "Failed to save manual schema";
      push({ tone: "error", title });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleSaveAsNew() {
    const newName = saveAsName.trim();
    if (!currentProjectId || !newName) return;
    setIsSaving(true);
    try {
      const schema = await createManualSchema(currentProjectId, {
        protocol,
        name: newName,
        description: description || null,
        nodes,
      });
      setSaveMode(null);
      push({ tone: "success", title: `Saved as "${newName}".` });
      navigate(`/manual-schemas/${schema.id}`);
    } catch (err) {
      const title = err instanceof ApiError ? (err.detail ?? err.title) : "Failed to save manual schema";
      push({ tone: "error", title });
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <section className="shell-panel px-5 py-5">
        <SharedStatePanel message="Loading manual schema." state="loading" title="Loading…" />
      </section>
    );
  }

  if (loadError) {
    return (
      <section className="shell-panel px-5 py-5">
        <SharedStatePanel message={loadError} state="error" title="Manual schema could not be loaded." />
      </section>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 flex-1 space-y-3">
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Name
              <input
                className="shell-field max-w-md"
                disabled={!access.isAdmin}
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Description
              <input
                className="shell-field max-w-md"
                disabled={!access.isAdmin}
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
          </div>
          <div className="flex flex-col items-end gap-2">
            {hasUnsavedChanges ? <StatusBadge label="Unsaved changes" tone="warning" /> : null}
            <div className="flex gap-2">
              <button className="shell-action" type="button" onClick={() => navigate("/manual-schemas")}>
                Back to list
              </button>
              {access.isAdmin ? (
                <button
                  className="shell-action"
                  disabled={!hasUnsavedChanges || validationIssues.length > 0}
                  type="button"
                  onClick={openSaveDialog}
                >
                  Save
                </button>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      <section className="shell-panel px-5 py-5">
        {access.isAdmin ? (
          <div className="mb-4 rounded-md border border-shell-line bg-shell-base/30 px-4 py-4">
            <p className="text-sm font-medium text-shell-ink">
              {isEmpty ? "Create your OPC UA server structure" : "Continue building this server structure"}
            </p>
            <p className="mt-1 text-sm text-shell-muted">
              A <strong>folder</strong> groups items clients can browse (for example, “Tank 1”). A <strong>variable</strong> is a value clients can read or write (for example, Temperature).
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
            <button className="shell-action" type="button" onClick={() => openAdd("FOLDER")}>
              {isEmpty ? "Start: create a folder" : "Add folder"}
            </button>
            {containers.length > 0 ? (
              <button className="shell-action" type="button" onClick={() => openAdd("OBJECT")}>
                Add object
              </button>
            ) : null}
            {containers.length > 0 ? (
              <button className="shell-action" type="button" onClick={() => openAdd("VARIABLE")}>
                Add variable
              </button>
            ) : null}
            <span className="text-xs text-shell-muted">
              {selectedNode && canHaveChildren(selectedNode.kind)
                ? `New items will be placed inside ${selectedNode.path}.`
                : isEmpty
                  ? "Step 1: create the first folder (for example, Tank 1)."
                  : "Select a folder or object in the tree to add items inside it."}
            </span>
            </div>
          </div>
        ) : null}

        {addKind ? (
          <div className="mb-4 space-y-3 rounded-md border border-shell-line bg-white px-4 py-4">
            <fieldset>
              <legend className="text-sm font-medium text-shell-ink">Choose a node class</legend>
              <p className="mt-1 text-xs text-shell-muted">Folders and objects organize the server tree. Variables hold values clients can read or write.</p>
              <div className="mt-3 flex flex-wrap gap-2" role="radiogroup" aria-label="Node class">
                {(["FOLDER", "OBJECT", "VARIABLE"] as const).map((kind) => (
                  <label key={kind} className={`cursor-pointer rounded-md border px-3 py-2 text-sm ${addKind === kind ? "border-shell-accent bg-shell-accent/5 text-shell-ink" : "border-shell-line text-shell-muted"}`}>
                    <input checked={addKind === kind} className="sr-only" name="node-class" type="radio" value={kind} onChange={() => setAddKind(kind)} />
                    <span className="font-medium">{kind === "FOLDER" ? "Folder" : kind === "OBJECT" ? "Object" : "Variable"}</span>
                    <span className="block text-xs">{kind === "FOLDER" ? "Contains nodes" : kind === "OBJECT" ? "Groups related nodes" : "Stores a value"}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="rounded-md bg-shell-base/60 px-3 py-2 text-xs text-shell-muted">
              <span className="font-medium text-shell-ink">Coming soon: </span>{UPCOMING_NODE_CLASSES.join(", ")}. For now, create folders, objects, and variables to build your server structure.
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                Parent
                <select
                  aria-label="Parent folder for new node"
                  className="shell-field"
                  value={addParentId ?? ""}
                  onChange={(e) => setAddParentId(e.target.value || null)}
                >
                  <option value="">Top level of server</option>
                  {containers.map((container) => (
                    <option key={container.nodeId} value={container.nodeId}>{container.path}</option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                Name
                <input
                  autoFocus
                  className="shell-field"
                  type="text"
                  value={addName}
                  onChange={(e) => setAddName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleAddNode();
                  }}
                />
              </label>
              {addKind === "VARIABLE" ? (
                <>
                  <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                    Suggested parameter
                    <select
                      className="shell-field"
                      value={selectedSuggestion}
                      onChange={(e) => {
                        setSelectedSuggestion(e.target.value);
                        applySuggestedVariable(e.target.value);
                      }}
                    >
                      <option value="">Choose a common parameter…</option>
                      {SUGGESTED_VARIABLES.map((variable) => (
                        <option key={variable.name} value={variable.name}>
                          {variable.name} — {variable.dataType}{variable.unit ? ` (${variable.unit})` : ""}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                    Data type
                    <select className="shell-field" value={addType} onChange={(e) => setAddType(e.target.value)}>
                      {DATA_TYPES.map((t) => <option key={t} value={t}>{formatDataType(t)}</option>)}
                    </select>
                  </label>
                  <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                    Value shape
                    <select className="shell-field" value={addValueRank} onChange={(e) => setAddValueRank(e.target.value)}>
                      {VALUE_RANKS.map((rank) => <option key={rank} value={rank}>{rank === "SCALAR" ? "One value" : "Array"}</option>)}
                    </select>
                  </label>
                  <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                    Client access
                    <select className="shell-field" value={addAccess} onChange={(e) => setAddAccess(e.target.value)}>
                      {ACCESS_LEVELS.map((accessLevel) => <option key={accessLevel} value={accessLevel}>{accessLevel.replace("_", " + ")}</option>)}
                    </select>
                  </label>
                </>
              ) : null}
            </div>
            {addKind === "VARIABLE" ? (
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                  Unit (optional)
                  <input className="shell-field" value={addUnit} onChange={(e) => setAddUnit(e.target.value)} />
                </label>
                <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                  Description (optional)
                  <input className="shell-field" value={addDescription} onChange={(e) => setAddDescription(e.target.value)} />
                </label>
              </div>
            ) : null}
            <div className="flex gap-2">
              <button
                className="shell-action"
                disabled={!addName.trim()}
                type="button"
                onClick={handleAddNode}
              >
                Add
              </button>
              <button
                className="shell-action"
                type="button"
                onClick={() => {
                  setAddKind(null);
                  setAddName("");
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        ) : null}

        {access.isAdmin && containers.length > 0 ? (
          <div className="mb-4 flex flex-wrap gap-2">
            <button className="shell-action" type="button" onClick={() => setShowTemplatePickerModal(true)}>
              Add from template
            </button>
            <button className="shell-text-action" type="button" onClick={() => setShowLibrary((open) => !open)}>
              {showLibrary ? "Hide parameter catalog" : "Choose from parameter catalog"}
            </button>
            <button className="shell-text-action" type="button" onClick={() => setShowBatch((open) => !open)}>
              {showBatch ? "Hide multiple-variable form" : "Add multiple variables"}
            </button>
          </div>
        ) : null}

        {access.isAdmin && containers.length > 0 && showLibrary ? (
          <section className="mb-4 rounded-md border border-shell-line bg-shell-base/30 px-4 py-4">
            <div>
              <div>
                <p className="text-sm font-medium text-shell-ink">Choose a parameter to add</p>
                <p className="mt-1 text-xs text-shell-muted">
                  {catalogParentId
                    ? `Add a parameter inside ${containers.find((container) => container.nodeId === catalogParentId)?.path}.`
                    : "Select the folder that should contain this parameter."}
                </p>
              </div>
              <label className="mt-4 block max-w-xl text-sm text-shell-muted">
                Search known parameters and structures
                <input
                  aria-label="Search parameter catalog"
                  className="shell-field mt-1 w-full"
                  placeholder="For example: simulation, analog, status"
                  value={catalogQuery}
                  onChange={(event) => setCatalogQuery(event.target.value)}
                />
              </label>
              <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                {PARAMETER_GROUPS.map((group) => {
                  const query = catalogQuery.trim().toLowerCase();
                  const matches = SUGGESTED_VARIABLES.filter((variable) => variable.group === group
                    && (!query || `${variable.name} ${variable.dataType} ${variable.description}`.toLowerCase().includes(query)));
                  if (matches.length === 0) return null;
                  return (
                    <section key={group} className="rounded-md border border-shell-line bg-white p-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-shell-muted">{group}</p>
                      <div className="mt-2 space-y-2">
                      {matches.map((variable) => (
                        <button
                          key={variable.name}
                          className="w-full rounded border border-shell-line px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:opacity-45 hover:border-shell-accent"
                          disabled={!catalogParentId}
                          type="button"
                          onClick={() => addSuggestedVariable(variable)}
                        >
                          <span className="block font-medium text-shell-ink">{variable.name}</span>
                          <span className="block text-xs text-shell-muted">{variable.dataType}{variable.unit ? ` · ${variable.unit}` : ""} — {variable.description}</span>
                        </button>
                      ))}
                      </div>
                    </section>
                  );
                })}
              </div>
              <div className="mt-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-shell-muted">Reusable structures</p>
                <div className="mt-2 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {STRUCTURE_TEMPLATES.filter((template) => {
                    const query = catalogQuery.trim().toLowerCase();
                    return !query || `${template.group} ${template.name} ${template.description} ${template.variables.map((variable) => variable.name).join(" ")}`.toLowerCase().includes(query);
                  }).map((template) => (
                    <button
                      key={template.name}
                      className="rounded border border-shell-line px-3 py-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-45 hover:border-shell-accent"
                      disabled={!catalogParentId}
                      type="button"
                      onClick={() => addStructureTemplate(template)}
                    >
                      <span className="block font-medium text-shell-ink">{template.name}</span>
                      <span className="mt-1 block text-xs text-shell-muted">{template.description}</span>
                      <span className="mt-2 block text-xs text-shell-muted">Adds a folder and {template.variables.length} variables.</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {access.isAdmin && containers.length > 0 && showBatch ? (
          <section className="mb-4 rounded-md border border-shell-line bg-white px-4 py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-sm font-medium text-shell-ink">Add multiple variables</p>
                <p className="mt-1 text-xs text-shell-muted">Add one row per variable. Each name and type is entered separately.</p>
              </div>
              <label className="flex min-w-56 flex-col gap-1.5 text-sm text-shell-muted">
                Parent
                <select
                  aria-label="Parent folder for multiple variables"
                  className="shell-field"
                  value={addParentId ?? ""}
                  onChange={(e) => setAddParentId(e.target.value || null)}
                >
                  <option value="">Top level of server</option>
                  {containers.map((container) => (
                    <option key={container.nodeId} value={container.nodeId}>{container.path}</option>
                  ))}
                </select>
              </label>
            </div>
            <div className="mt-4 space-y-3">
              {batchRows.map((row, index) => (
                <div key={row.id} className="grid gap-2 rounded border border-shell-line p-3 md:grid-cols-[minmax(10rem,1fr)_11rem_minmax(8rem,1fr)_minmax(10rem,1fr)_auto]">
                  <label className="text-xs text-shell-muted">Name
                    <input aria-label={`Variable ${index + 1} name`} className="shell-field mt-1 w-full" value={row.name}
                      onChange={(e) => setBatchRows((prev) => prev.map((candidate) => candidate.id === row.id ? { ...candidate, name: e.target.value } : candidate))} />
                  </label>
                  <label className="text-xs text-shell-muted">Type
                    <select aria-label={`Variable ${index + 1} type`} className="shell-field mt-1 w-full" value={row.dataType}
                      onChange={(e) => setBatchRows((prev) => prev.map((candidate) => candidate.id === row.id ? { ...candidate, dataType: e.target.value } : candidate))}>
                      {DATA_TYPES.map((type) => <option key={type} value={type}>{formatDataType(type)}</option>)}
                    </select>
                  </label>
                  <label className="text-xs text-shell-muted">Unit <span className="font-normal">(optional)</span>
                    <input aria-label={`Variable ${index + 1} unit`} className="shell-field mt-1 w-full" value={row.unit}
                      onChange={(e) => setBatchRows((prev) => prev.map((candidate) => candidate.id === row.id ? { ...candidate, unit: e.target.value } : candidate))} />
                  </label>
                  <label className="text-xs text-shell-muted">Description <span className="font-normal">(optional)</span>
                    <input aria-label={`Variable ${index + 1} description`} className="shell-field mt-1 w-full" value={row.description}
                      onChange={(e) => setBatchRows((prev) => prev.map((candidate) => candidate.id === row.id ? { ...candidate, description: e.target.value } : candidate))} />
                  </label>
                  <button className="shell-text-action self-end" type="button" onClick={() => setBatchRows((prev) => prev.filter((candidate) => candidate.id !== row.id))}>Remove</button>
                </div>
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <button className="shell-action" type="button" onClick={appendBatchRow}>+ Add row</button>
              <button className="shell-action" disabled={!batchRows.some((row) => row.name.trim())} type="button" onClick={addBatchNodes}>Add variables</button>
            </div>
          </section>
        ) : null}

        {validationIssues.length > 0 ? (
          <section aria-live="polite" className="mb-4 rounded-md border border-shell-danger/40 bg-shell-danger/5 px-4 py-3">
            <p className="text-sm font-semibold text-shell-ink">Fix {validationIssues.length} {validationIssues.length === 1 ? "issue" : "issues"} before saving</p>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-shell-muted">
              {validationIssues.slice(0, 4).map((issue, index) => {
                const node = nodes.find((candidate) => candidate.nodeId === issue.nodeId);
                return <li key={`${issue.nodeId}-${index}`}><span className="font-medium text-shell-ink">{node?.name || "Unnamed node"}:</span> {issue.message}</li>;
              })}
              {validationIssues.length > 4 ? <li>And {validationIssues.length - 4} more issues.</li> : null}
            </ul>
          </section>
        ) : null}

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(18rem,1fr)]">
          <div className="space-y-3">
            <span className="text-sm text-shell-muted">{variableCount} variables</span>
            <div className="overflow-hidden rounded-md border border-shell-line bg-white max-h-[32rem] overflow-y-auto">
              {treeRoots.length === 0 ? (
                <p className="px-4 py-6 text-center text-sm text-shell-muted">
                  Step 1: create a folder. Give it a device or area name, such as “Tank 1” or “Pump”.
                </p>
              ) : (
                <ManualSchemaTree
                  depth={0}
                  expandedIds={expandedIds}
                  nodes={treeRoots}
                  selectedId={selectedId}
                  onSelect={setSelectedId}
                  onToggle={toggleExpand}
                />
              )}
            </div>
          </div>

          {selectedNode ? (
            <section className="rounded-md border border-shell-line bg-white px-4 py-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                    {selectedNode.kind === "FOLDER" ? "Folder" : selectedNode.kind === "OBJECT" ? "Object" : "Variable"}
                  </p>
                  <p className="mt-2 truncate font-mono text-sm text-shell-ink">{selectedNode.path}</p>
                  {selectedNode.parentId && nodes.find((n) => n.nodeId === selectedNode.parentId)?.kind === "VARIABLE" ? (
                    <p className="mt-1 text-xs text-shell-muted">
                      Parent: <span className="font-mono">{nodes.find((n) => n.nodeId === selectedNode.parentId)?.name}</span> (VARIABLE)
                    </p>
                  ) : null}
                </div>
                {access.isAdmin ? (
                  <button
                    className="shrink-0 text-xs text-shell-danger hover:underline"
                    type="button"
                    onClick={() => handleDeleteNode(selectedNode.nodeId)}
                  >
                    Delete
                  </button>
                ) : null}
              </div>
              <div className="mt-4 space-y-3">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Name
                  <input
                    className="shell-field"
                    disabled={!access.isAdmin}
                    type="text"
                    value={selectedNode.name}
                    onChange={(e) => updateSelectedNode({ name: e.target.value })}
                  />
                </label>
                {selectedNode.kind === "VARIABLE" ? (
                  <>
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Data type
                      <select
                        className="shell-field"
                        disabled={!access.isAdmin}
                        value={selectedNode.dataType ?? "FLOAT64"}
                        onChange={(e) => updateSelectedNode({ dataType: e.target.value })}
                      >
                        {DATA_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {typeLabel(t)}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Value shape
                      <select className="shell-field" disabled={!access.isAdmin} value={selectedNode.valueRank ?? "SCALAR"} onChange={(e) => updateSelectedNode({ valueRank: e.target.value })}>
                        {VALUE_RANKS.map((rank) => <option key={rank} value={rank}>{rank === "SCALAR" ? "One value" : "Array"}</option>)}
                      </select>
                    </label>
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Client access
                      <select className="shell-field" disabled={!access.isAdmin} value={selectedNode.access ?? "READ"} onChange={(e) => updateSelectedNode({ access: e.target.value })}>
                        {ACCESS_LEVELS.map((accessLevel) => <option key={accessLevel} value={accessLevel}>{accessLevel.replace("_", " + ")}</option>)}
                      </select>
                    </label>
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Unit
                      <input
                        className="shell-field"
                        disabled={!access.isAdmin}
                        type="text"
                        value={selectedNode.unit ?? ""}
                        onChange={(e) => updateSelectedNode({ unit: e.target.value || null })}
                      />
                    </label>
                  </>
                ) : null}
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Description
                  <input
                    className="shell-field"
                    disabled={!access.isAdmin}
                    type="text"
                    value={selectedNode.description ?? ""}
                    onChange={(e) => updateSelectedNode({ description: e.target.value || null })}
                  />
                </label>
                {selectedNode.parentId && nodes.find((n) => n.nodeId === selectedNode.parentId)?.kind === "VARIABLE" ? (() => {
                  const parent = nodes.find((n) => n.nodeId === selectedNode.parentId);
                  const childRef = parent?.references?.find((ref) => ref.targetNodeId === selectedNode.nodeId);
                  return (
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Reference type <span className="font-normal text-shell-danger">(required)</span>
                      <select
                        className="shell-field"
                        disabled={!access.isAdmin}
                        value={childRef?.type ?? "HAS_PROPERTY"}
                        onChange={(e) => {
                          const refType = e.target.value as ReferenceDto["type"];
                          if (!parent) return;
                          // Update or create the reference in the parent
                          const parentRefs = parent.references ?? [];
                          const existingRefIndex = parentRefs.findIndex((ref) => ref.targetNodeId === selectedNode.nodeId);
                          let updated: ReferenceDto[];
                          if (existingRefIndex >= 0) {
                            updated = parentRefs.map((ref, i) => i === existingRefIndex ? { ...ref, type: refType } : ref);
                          } else {
                            updated = [...parentRefs, { targetNodeId: selectedNode.nodeId, type: refType, forward: true }];
                          }
                          // Update parent node
                          setNodes((prev) => prev.map((n) => n.nodeId === parent.nodeId ? { ...n, references: updated } : n));
                        }}
                      >
                        <option value="HAS_PROPERTY">Has property</option>
                        <option value="HAS_COMPONENT">Has component</option>
                      </select>
                    </label>
                  );
                })() : null}
                <div className="flex flex-col gap-2 text-sm text-shell-muted">
                  References
                  {(selectedNode.references ?? []).length > 0 ? (
                    <ul className="space-y-1.5">
                      {(selectedNode.references ?? []).map((reference, index) => {
                        const target = nodes.find((n) => n.nodeId === reference.targetNodeId);
                        return (
                          <li
                            key={`${reference.targetNodeId}-${reference.type}-${index}`}
                            className="flex items-center justify-between gap-2 rounded-md border border-shell-line bg-shell-base/40 px-3 py-2 text-xs"
                          >
                            <span className="min-w-0 truncate text-shell-ink">
                              <span className="font-medium">{referenceTypeLabel(reference.type)}</span>
                              {" → "}
                              <span className="font-mono">{target ? target.path : reference.targetNodeId}</span>
                            </span>
                            {access.isAdmin ? (
                              <button
                                className="shrink-0 text-shell-danger hover:underline"
                                type="button"
                                onClick={() => removeReference(index)}
                              >
                                Remove
                              </button>
                            ) : null}
                          </li>
                        );
                      })}
                    </ul>
                  ) : (
                    <p className="text-xs text-shell-muted">No references yet.</p>
                  )}
                  {access.isAdmin ? (
                    <div className="flex flex-wrap items-end gap-2">
                      <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                        Target node
                        <select
                          aria-label="Reference target node"
                          className="shell-field"
                          value={addRefTargetId}
                          onChange={(e) => setAddRefTargetId(e.target.value)}
                        >
                          <option value="">Choose a node…</option>
                          {nodes
                            .filter((n) => n.nodeId !== selectedNode.nodeId)
                            .map((n) => (
                              <option key={n.nodeId} value={n.nodeId}>{n.path}</option>
                            ))}
                        </select>
                      </label>
                      <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                        Reference type
                        <select
                          aria-label="Reference type"
                          className="shell-field"
                          value={addRefType}
                          onChange={(e) => setAddRefType(e.target.value as ReferenceDto["type"])}
                        >
                          {REFERENCE_TYPES.map((type) => (
                            <option key={type} value={type}>{referenceTypeLabel(type)}</option>
                          ))}
                        </select>
                      </label>
                      <button
                        className="shell-action"
                        disabled={!addRefTargetId || addRefTargetId === selectedNode.nodeId}
                        type="button"
                        onClick={addReference}
                      >
                        Add reference
                      </button>
                    </div>
                  ) : null}
                </div>
                {selectedNode.kind === "VARIABLE" ? (
                  <div className="flex flex-col gap-2 text-sm text-shell-muted">
                    <button
                      className="flex items-center justify-between rounded-md border border-shell-line bg-shell-base/30 px-3 py-2 text-left hover:bg-shell-base/50"
                      type="button"
                      onClick={() => setShowOpcUaAttributes(!showOpcUaAttributes)}
                    >
                      <span className="font-medium text-shell-ink">OPC UA Attributes</span>
                      <span className="text-xs text-shell-muted">{showOpcUaAttributes ? "▾" : "▸"}</span>
                    </button>
                    {showOpcUaAttributes ? (
                      <div className="space-y-3 rounded-md border border-shell-line bg-shell-base/20 p-3">
                        <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                          Access level (0-255)
                          <input
                            className="shell-field"
                            disabled={!access.isAdmin}
                            type="number"
                            min="0"
                            max="255"
                            value={selectedNode.accessLevelFull ?? ""}
                            placeholder="Not specified"
                            onChange={(e) => {
                              const val = e.target.value ? parseInt(e.target.value, 10) : null;
                              if (val === null || (val >= 0 && val <= 255)) {
                                updateSelectedNode({ accessLevelFull: val });
                              }
                            }}
                          />
                        </label>
                        <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                          Minimum sampling interval (ms)
                          <input
                            className="shell-field"
                            disabled={!access.isAdmin}
                            type="number"
                            value={selectedNode.minimumSamplingInterval ?? ""}
                            placeholder="Not specified (-1=indeterminate, 0=continuous)"
                            onChange={(e) => {
                              const val = e.target.value ? parseInt(e.target.value, 10) : null;
                              updateSelectedNode({ minimumSamplingInterval: val });
                            }}
                          />
                        </label>
                        <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                          Write mask (0-255)
                          <input
                            className="shell-field"
                            disabled={!access.isAdmin}
                            type="number"
                            min="0"
                            max="255"
                            value={selectedNode.writeMask ?? ""}
                            placeholder="Not specified"
                            onChange={(e) => {
                              const val = e.target.value ? parseInt(e.target.value, 10) : null;
                              if (val === null || (val >= 0 && val <= 255)) {
                                updateSelectedNode({ writeMask: val });
                              }
                            }}
                          />
                        </label>
                        <label className="flex items-center gap-2 text-sm text-shell-muted">
                          <input
                            type="checkbox"
                            checked={selectedNode.historizing ?? false}
                            disabled={!access.isAdmin}
                            onChange={(e) => updateSelectedNode({ historizing: e.target.checked || null })}
                          />
                          <span>Historizing</span>
                        </label>
                      </div>
                    ) : null}
                  </div>
                ) : null}
                {selectedIssues.length > 0 ? (
                  <div className="rounded-md border border-shell-danger/30 bg-shell-danger/5 px-3 py-2 text-xs text-shell-muted">
                    {selectedIssues.map((issue, index) => <p key={`${issue.message}-${index}`}>{issue.message}</p>)}
                  </div>
                ) : null}
              </div>
            </section>
          ) : (
            <section className="rounded-md border border-shell-line bg-white px-4 py-6">
              <p className="text-center text-sm text-shell-muted">
                Select a node from the tree to inspect or edit its details.
              </p>
            </section>
          )}
        </div>
      </section>

      {saveMode ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
          <button
            aria-label="Close save dialog"
            className="absolute inset-0"
            type="button"
            onClick={() => (isSaving ? undefined : setSaveMode(null))}
          />
          <div className="relative z-10 w-full max-w-md rounded-lg border border-shell-line bg-white shadow-panel">
            <div className="border-b border-shell-line px-5 py-4">
              <h2 className="text-lg font-semibold text-shell-ink">Save changes</h2>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Save your edits into this schema, or keep the original and save a new copy instead.
              </p>
            </div>
            <div className="space-y-3 px-5 py-4">
              <label className="grid grid-cols-[1.25rem_minmax(0,1fr)] items-start gap-x-2 text-sm text-shell-ink">
                <input
                  checked={saveMode === "in-place"}
                  className="mt-1"
                  name="save-mode"
                  type="radio"
                  onChange={() => setSaveMode("in-place")}
                />
                <span className="min-w-0">
                  <span className="block font-medium">Save in this schema</span>
                  <span className="block text-xs text-shell-muted">
                    Overwrites "{savedSnapshot.name}". Data sources already created from it are unaffected.
                  </span>
                </span>
              </label>
              <label className="grid grid-cols-[1.25rem_minmax(0,1fr)] items-start gap-x-2 text-sm text-shell-ink">
                <input
                  checked={saveMode === "save-as"}
                  className="mt-1"
                  name="save-mode"
                  type="radio"
                  onChange={() => setSaveMode("save-as")}
                />
                <span className="min-w-0">
                  <span className="block font-medium">Save as a new schema</span>
                  <input
                    className="shell-field mt-2 block w-full"
                    disabled={saveMode !== "save-as"}
                    type="text"
                    value={saveAsName}
                    onChange={(e) => setSaveAsName(e.target.value)}
                  />
                </span>
              </label>
            </div>
            <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
              <button className="shell-action" disabled={isSaving} type="button" onClick={() => setSaveMode(null)}>
                Cancel
              </button>
              <button
                className="shell-action"
                disabled={isSaving || (saveMode === "save-as" && !saveAsName.trim())}
                type="button"
                onClick={() => void (saveMode === "in-place" ? handleSaveInPlace() : handleSaveAsNew())}
              >
                {isSaving ? "Saving…" : "Save"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <TemplatePickerModal
        open={showTemplatePickerModal}
        templates={availableTemplates}
        onSelectTemplate={handleTemplateSelection}
        onClose={() => setShowTemplatePickerModal(false)}
      />
    </div>
  );
}

function ManualSchemaTree({
  nodes,
  depth,
  selectedId,
  expandedIds,
  onSelect,
  onToggle,
}: {
  nodes: (NodeDto & { children: unknown[] })[];
  depth: number;
  selectedId: string | null;
  expandedIds: Set<string>;
  onSelect: (id: string) => void;
  onToggle: (id: string) => void;
}) {
  return (
    <ul className={depth === 0 ? "py-1" : undefined}>
      {nodes.map((node) => {
        const children = node.children as (NodeDto & { children: unknown[] })[];
        const isExpanded = expandedIds.has(node.nodeId);
        const isSelected = selectedId === node.nodeId;
        const isFolder = canHaveChildren(node.kind);
        return (
          <li key={node.nodeId}>
            <button
              className={`flex w-full items-center gap-1.5 px-3 py-2 text-left text-sm transition ${
                isSelected ? "bg-shell-accent/5" : "hover:bg-shell-base/50"
              }`}
              style={{ paddingLeft: `${12 + depth * 16}px` }}
              type="button"
              onClick={() => {
                if (isFolder) onToggle(node.nodeId);
                onSelect(node.nodeId);
              }}
            >
              <span
                className="w-3 shrink-0 text-center text-xs text-shell-muted"
                onClick={
                  children.length > 0
                    ? (e) => {
                        e.stopPropagation();
                        onToggle(node.nodeId);
                      }
                    : undefined
                }
              >
                {children.length > 0 ? (isExpanded ? "▾" : "▸") : ""}
              </span>
              <span
                className={
                  isFolder
                    ? "truncate font-medium text-shell-muted"
                    : "min-w-0 flex-1 truncate font-mono text-shell-ink"
                }
              >
                {node.name}
              </span>
              {!isFolder && node.dataType ? (
                <span className="shrink-0 text-xs text-shell-muted">{typeLabel(node.dataType)}</span>
              ) : null}
            </button>
            {isExpanded && children.length > 0 ? (
              <ManualSchemaTree
                depth={depth + 1}
                expandedIds={expandedIds}
                nodes={children}
                selectedId={selectedId}
                onSelect={onSelect}
                onToggle={onToggle}
              />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
