import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, mapDataType } from "../api";
import { resolveAccess } from "../shell/access-policy";
import { useManualSchemasStore } from "../shell/manual-schemas-store";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { buildTree, type NodeDto } from "./data-source-schema-editor";

const DATA_TYPES = [
  "FLOAT64",
  "FLOAT32",
  "INT32",
  "INT64",
  "UINT32",
  "INT16",
  "BOOL",
  "STRING",
  "DATETIME",
] as const;

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
  const [addKind, setAddKind] = useState<"FOLDER" | "VARIABLE" | null>(null);
  const [addName, setAddName] = useState("");
  const [addType, setAddType] = useState<string>("FLOAT64");
  const [saveMode, setSaveMode] = useState<SaveMode | null>(null);
  const [saveAsName, setSaveAsName] = useState("");
  const [isSaving, setIsSaving] = useState(false);

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

  function toggleExpand(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
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
    const parentId = selectedNode?.kind === "FOLDER" ? selectedNode.nodeId : null;
    const node: NodeDto = {
      nodeId: newNodeId(),
      parentId,
      path: pathFor(parentId, trimmed),
      name: trimmed,
      kind: addKind,
      dataType: addKind === "VARIABLE" ? addType : null,
      valueRank: addKind === "VARIABLE" ? "SCALAR" : null,
      access: addKind === "VARIABLE" ? "READ" : null,
      unit: null,
      description: null,
    };
    setNodes((prev) => [...prev, node]);
    if (parentId) setExpandedIds((prev) => new Set(prev).add(parentId));
    setAddName("");
    setAddKind(null);
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
    if (!hasUnsavedChanges) return;
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
                  disabled={!hasUnsavedChanges}
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
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <span className="text-xs text-shell-muted">
              {selectedNode?.kind === "FOLDER"
                ? `Adding under "${selectedNode.name}"`
                : "Adding at root — select a folder to nest under it"}
            </span>
            <button className="shell-action" type="button" onClick={() => setAddKind("FOLDER")}>
              + Add folder
            </button>
            <button className="shell-action" type="button" onClick={() => setAddKind("VARIABLE")}>
              + Add variable
            </button>
          </div>
        ) : null}

        {addKind ? (
          <div className="mb-4 space-y-3 rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-sm font-medium text-shell-ink">
              New {addKind === "FOLDER" ? "folder" : "variable"}
            </p>
            <div className="grid gap-3 sm:grid-cols-3">
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
                <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                  Type
                  <select
                    className="shell-field"
                    value={addType}
                    onChange={(e) => setAddType(e.target.value)}
                  >
                    {DATA_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {typeLabel(t)}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
            </div>
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

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(18rem,1fr)]">
          <div className="space-y-3">
            <span className="text-sm text-shell-muted">{variableCount} variables</span>
            <div className="overflow-hidden rounded-md border border-shell-line bg-white max-h-[32rem] overflow-y-auto">
              {treeRoots.length === 0 ? (
                <p className="px-4 py-6 text-center text-sm text-shell-muted">
                  No nodes yet. Add a folder or variable to get started.
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
                    {selectedNode.kind === "FOLDER" ? "Folder" : "Variable"}
                  </p>
                  <p className="mt-2 truncate font-mono text-sm text-shell-ink">{selectedNode.path}</p>
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
                      Type
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
              <label className="flex items-start gap-2 text-sm text-shell-ink">
                <input
                  checked={saveMode === "in-place"}
                  className="mt-1"
                  name="save-mode"
                  type="radio"
                  onChange={() => setSaveMode("in-place")}
                />
                <span>
                  <span className="font-medium">Save in this schema</span>
                  <span className="block text-xs text-shell-muted">
                    Overwrites "{savedSnapshot.name}". Data sources already created from it are unaffected.
                  </span>
                </span>
              </label>
              <label className="flex items-start gap-2 text-sm text-shell-ink">
                <input
                  checked={saveMode === "save-as"}
                  className="mt-1"
                  name="save-mode"
                  type="radio"
                  onChange={() => setSaveMode("save-as")}
                />
                <span className="flex-1">
                  <span className="font-medium">Save as a new schema</span>
                  <input
                    className="shell-field mt-2"
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
        const isFolder = node.kind === "FOLDER";
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
