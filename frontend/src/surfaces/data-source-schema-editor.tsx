import { useEffect, useRef, useState } from "react";
import { EditLockBanner, EditLockState } from "../ui/edit-lock-banner";
import { StatusBadge } from "../ui/status-badge";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { apiFetch, ApiError, mapDataType } from "../api";
import { useEditLease } from "../shell/use-edit-lease";
import type { DataSourceRow } from "./mock-data-sources";
import type { ParameterType, SchemaParameter } from "./mock-schema-parameters";

export type ReferenceDto = {
  targetNodeId: string;
  type: "ORGANIZES" | "HAS_COMPONENT" | "HAS_PROPERTY" | "HAS_TYPE_DEFINITION" | "GENERIC";
  forward: boolean;
};

// Backend schema shapes from GET/PUT /api/v1/projects/{pid}/data-sources/{id}/schema
export type NodeDto = {
  nodeId: string;
  parentId: string | null;
  path: string;
  name: string;
  kind: "FOLDER" | "OBJECT" | "VARIABLE";
  dataType: string | null;
  valueRank: string | null;
  access: string | null;
  unit: string | null;
  description: string | null;
  arrayDimensions?: number[];
  typeDefinition?: string | null;
  references?: ReferenceDto[];
};

/** FOLDER and OBJECT nodes can contain children; only they're valid parents (backend rule). */
export function canHaveChildren(kind: NodeDto["kind"]): boolean {
  return kind === "FOLDER" || kind === "OBJECT";
}

type SchemaResponse = {
  id: string;
  dataSourceId: string;
  version: number;
  nodes: NodeDto[];
};

type TreeNode = NodeDto & { children: TreeNode[] };

export function buildTree(nodes: NodeDto[]): TreeNode[] {
  const byId = new Map<string, TreeNode>();
  for (const n of nodes) byId.set(n.nodeId, { ...n, children: [] });
  const roots: TreeNode[] = [];
  for (const n of byId.values()) {
    if (n.parentId === null) {
      roots.push(n);
    } else {
      byId.get(n.parentId)?.children.push(n);
    }
  }
  // Sort: folders first, then by name within each group
  function sortChildren(node: TreeNode) {
    node.children.sort((a, b) => {
      if (canHaveChildren(a.kind) !== canHaveChildren(b.kind)) return canHaveChildren(a.kind) ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    node.children.forEach(sortChildren);
  }
  roots.sort((a, b) => {
    if (canHaveChildren(a.kind) !== canHaveChildren(b.kind)) return canHaveChildren(a.kind) ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  roots.forEach(sortChildren);
  return roots;
}

function mapNode(node: NodeDto): SchemaParameter {
  const rawType = node.dataType
    ? mapDataType(node.dataType as Parameters<typeof mapDataType>[0])
    : null;
  const type: ParameterType = rawType ?? "string";
  return {
    id: node.nodeId,
    name: node.name,
    path: node.path,
    type,
    unit: node.unit ?? "",
    description: node.description ?? "",
  };
}

type EditBuffer = {
  description: string;
  unit: string;
};

function bufferFromParam(param: SchemaParameter): EditBuffer {
  return {
    description: param.description,
    unit: param.unit,
  };
}

export function detectDependencyWarnings(
  _param: SchemaParameter,
  buffer: EditBuffer,
  original: EditBuffer,
): string[] {
  const warnings: string[] = [];

  if (buffer.description !== original.description) {
    warnings.push(
      "Renaming this parameter may break existing recordings, replay configurations, or scenarios that reference it by its current identifier.",
    );
  }

  return warnings;
}

function TreeNodeRow({
  node,
  depth,
  selectedId,
  expandedIds,
  onToggle,
  onSelect,
}: {
  node: TreeNode;
  depth: number;
  selectedId: string | null;
  expandedIds: Set<string>;
  onToggle: (id: string) => void;
  onSelect: (node: NodeDto) => void;
}) {
  const isExpanded = expandedIds.has(node.nodeId);
  const isSelected = selectedId === node.nodeId;
  const indent = depth * 16;
  const isFolder = canHaveChildren(node.kind);
  const hasChildren = node.children.length > 0;

  // A Variable can itself have children (e.g. a structured value's component
  // Variables) — not just Folders — so the expand toggle is keyed off
  // hasChildren, independent of kind. A Variable row stays selectable even
  // when it also has children.
  return (
    <>
      <li>
        <button
          className={`flex w-full items-center gap-1.5 px-3 py-2 text-left text-sm transition ${
            !isFolder && isSelected ? "bg-shell-accent/5" : "hover:bg-shell-base/50"
          }`}
          style={{ paddingLeft: `${12 + indent}px` }}
          type="button"
          onClick={() => (isFolder ? onToggle(node.nodeId) : onSelect(node))}
        >
          <span
            className="shrink-0 text-xs text-shell-muted w-3 text-center"
            onClick={
              hasChildren && !isFolder
                ? (e) => {
                    e.stopPropagation();
                    onToggle(node.nodeId);
                  }
                : undefined
            }
          >
            {hasChildren ? (isExpanded ? "▾" : "▸") : ""}
          </span>
          <span
            className={
              isFolder
                ? "text-shell-muted font-medium truncate"
                : "min-w-0 flex-1 truncate font-mono text-shell-ink"
            }
          >
            {node.name}
          </span>
          {isFolder && hasChildren && (
            <span className="ml-auto shrink-0 text-xs text-shell-muted">
              {node.children.filter((c) => c.kind === "VARIABLE").length || ""}
            </span>
          )}
          {!isFolder && node.dataType && (
            <span className="shrink-0 text-xs text-shell-muted">
              {node.dataType.charAt(0).toUpperCase() + node.dataType.slice(1).toLowerCase()}
            </span>
          )}
        </button>
      </li>
      {isExpanded &&
        hasChildren &&
        node.children.map((child) => (
          <TreeNodeRow
            key={child.nodeId}
            node={child}
            depth={depth + 1}
            selectedId={selectedId}
            expandedIds={expandedIds}
            onToggle={onToggle}
            onSelect={onSelect}
          />
        ))}
    </>
  );
}

export function DataSourceSchemaEditor({
  source,
  projectId,
  onUnsavedChanges,
}: {
  source: DataSourceRow;
  projectId?: string;
  onUnsavedChanges?: (has: boolean) => void;
}) {
  const [selectedParam, setSelectedParam] = useState<SchemaParameter | null>(null);
  const [originalBuffer, setOriginalBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [editBuffer, setEditBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
  });
  const [saving, setSaving] = useState(false);
  const [confirmSaveOpen, setConfirmSaveOpen] = useState(false);
  const [allParams, setAllParams] = useState<SchemaParameter[]>([]);
  const [treeRoots, setTreeRoots] = useState<TreeNode[]>([]);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [addName, setAddName] = useState("");
  const [addType, setAddType] = useState("FLOAT64");
  const [addUnit, setAddUnit] = useState("");

  const rawNodesRef = useRef<NodeDto[]>([]);

  useEffect(() => {
    if (!projectId || !source.id) return;
    setSchemaLoading(true);
    setSchemaError(null);
    apiFetch<SchemaResponse>(
      `/api/v1/projects/${projectId}/data-sources/${source.id}/schema`,
    )
      .then((resp) => {
        rawNodesRef.current = resp.nodes;
        setAllParams(resp.nodes.filter((n) => n.kind === "VARIABLE").map(mapNode));
        const roots = buildTree(resp.nodes);
        setTreeRoots(roots);
        // Auto-expand top-level folders
        setExpandedIds(
          new Set(roots.filter((n) => canHaveChildren(n.kind)).map((n) => n.nodeId)),
        );
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          // No schema yet — normal for a freshly created manual source
          setSchemaLoading(false);
          return;
        }
        const msg = err instanceof ApiError ? err.title : "Failed to load schema";
        setSchemaError(msg);
      })
      .finally(() => setSchemaLoading(false));
  }, [source.id, projectId]);

  useEffect(() => {
    onUnsavedChanges?.(hasUnsavedChanges);
  }, [hasUnsavedChanges, onUnsavedChanges]);

  // UI-459: wire edit lock to the backend lease API (IS-081).
  const { leaseState, lockedByHolder } = useEditLease(
    "data-sources",
    source.id,
    projectId ?? "",
  );

  const isLockedByOther = leaseState === "locked-by-other";

  const lockState: EditLockState = isLockedByOther
    ? { kind: "locked-by-other", owner: lockedByHolder ?? "another user", since: "" }
    : { kind: "unlocked" };

  const filteredParams = allParams.filter(
    (p) =>
      searchQuery === "" ||
      p.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.name.toLowerCase().includes(searchQuery.toLowerCase()),
  );

  const dependencyWarnings: string[] =
    hasUnsavedChanges && selectedParam
      ? detectDependencyWarnings(selectedParam, editBuffer, originalBuffer)
      : [];

  function handleToggle(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleSelectNode(node: NodeDto) {
    const param = allParams.find((p) => p.id === node.nodeId);
    if (!param) return;
    setSelectedParam(param);
    const buf = bufferFromParam(param);
    setEditBuffer(buf);
    setOriginalBuffer(buf);
    setHasUnsavedChanges(false);
  }

  function handleSelectParam(param: SchemaParameter) {
    setSelectedParam(param);
    const buf = bufferFromParam(param);
    setEditBuffer(buf);
    setOriginalBuffer(buf);
    setHasUnsavedChanges(false);
  }

  function updateEditBuffer(patch: Partial<EditBuffer>) {
    setEditBuffer((curr) => ({ ...curr, ...patch }));
  }

  async function executeSave() {
    if (!projectId || !source.id || !selectedParam) return;
    setSaving(true);
    try {
      const updatedNodes: NodeDto[] = rawNodesRef.current.map((raw) => {
        if (raw.nodeId === selectedParam.id) {
          return {
            ...raw,
            unit: editBuffer.unit,
            description: editBuffer.description,
          };
        }
        const param = allParams.find((p) => p.id === raw.nodeId);
        if (param) {
          return { ...raw, unit: param.unit, description: param.description };
        }
        return raw;
      });
      await apiFetch<SchemaResponse>(
        `/api/v1/projects/${projectId}/data-sources/${source.id}/schema`,
        {
          method: "PUT",
          body: JSON.stringify({ nodes: updatedNodes }),
        },
      );
      setAllParams((prev) =>
        prev.map((p) =>
          p.id === selectedParam.id
            ? { ...p, description: editBuffer.description, unit: editBuffer.unit }
            : p,
        ),
      );
      rawNodesRef.current = rawNodesRef.current.map((raw) =>
        raw.nodeId === selectedParam.id
          ? { ...raw, unit: editBuffer.unit, description: editBuffer.description }
          : raw,
      );
      setHasUnsavedChanges(false);
      setOriginalBuffer(editBuffer);
    } catch (err) {
      const msg = err instanceof ApiError ? err.title : "Failed to save schema";
      setSchemaError(msg);
    } finally {
      setSaving(false);
    }
  }

  function handleSave() {
    if (dependencyWarnings.length > 0) {
      setConfirmSaveOpen(true);
    } else {
      executeSave();
    }
  }

  function handleConfirmSave() {
    setConfirmSaveOpen(false);
    executeSave();
  }

  function handleDiscard() {
    if (selectedParam) {
      setEditBuffer(originalBuffer);
    }
    setHasUnsavedChanges(false);
  }

  async function handleDeleteParameter(paramId: string) {
    if (!projectId) return;
    const nextNodes = rawNodesRef.current.filter((n) => n.nodeId !== paramId);
    try {
      const resp = await apiFetch<SchemaResponse>(
        `/api/v1/projects/${projectId}/data-sources/${source.id}/schema`,
        { method: "PUT", body: JSON.stringify({ nodes: nextNodes }) },
      );
      rawNodesRef.current = resp.nodes;
      const variables = resp.nodes.filter((n) => n.kind === "VARIABLE");
      setAllParams(variables.map(mapNode));
      setTreeRoots(buildTree(resp.nodes));
      if (selectedParam?.id === paramId) {
        setSelectedParam(null);
        setHasUnsavedChanges(false);
      }
    } catch (err) {
      const msg = err instanceof ApiError ? err.title : "Failed to delete parameter";
      setSchemaError(msg);
    }
  }

  async function handleAddParameter() {
    const name = addName.trim();
    if (!name || !projectId) return;
    const nodeId = `manual-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const newNode: NodeDto = {
      nodeId,
      parentId: null,
      path: `/${name}`,
      name,
      kind: "VARIABLE",
      dataType: addType,
      valueRank: "SCALAR",
      access: "READ_WRITE",
      unit: addUnit.trim() || null,
      description: null,
    };
    const nextNodes = [...rawNodesRef.current, newNode];
    try {
      const resp = await apiFetch<SchemaResponse>(
        `/api/v1/projects/${projectId}/data-sources/${source.id}/schema`,
        { method: "PUT", body: JSON.stringify({ nodes: nextNodes }) },
      );
      rawNodesRef.current = resp.nodes;
      const variables = resp.nodes.filter((n) => n.kind === "VARIABLE");
      setAllParams(variables.map(mapNode));
      setTreeRoots(buildTree(resp.nodes));
      setAddName("");
      setAddUnit("");
      setAddOpen(false);
    } catch (err) {
      const msg = err instanceof ApiError ? err.title : "Failed to add parameter";
      setSchemaError(msg);
    }
  }

  if (schemaLoading) {
    return <p className="text-sm text-shell-muted">Loading schema…</p>;
  }

  if (schemaError) {
    return (
      <p className="text-sm text-shell-danger" role="alert">
        {schemaError}
      </p>
    );
  }

  return (
    <div className="space-y-4">
      {lockState.kind !== "unlocked" ? <EditLockBanner lock={lockState} /> : null}

      {source.status === "Active" ? (
        <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-700">
            This source is currently active.
          </p>
          <p className="mt-1 text-sm leading-6 text-shell-muted">
            Schema changes will not affect the running session. Restart the source to
            apply edits.
          </p>
        </section>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        {hasUnsavedChanges ? (
          <StatusBadge label="Unsaved changes" tone="warning" />
        ) : null}
        <button
          className="shell-action"
          disabled={!hasUnsavedChanges || isLockedByOther || saving}
          type="button"
          onClick={handleSave}
        >
          Save schema
        </button>
        <button
          className="shell-action"
          disabled={!hasUnsavedChanges || isLockedByOther}
          type="button"
          onClick={handleDiscard}
        >
          Discard changes
        </button>
        {!isLockedByOther && source.status !== "Active" ? (
          <button
            className="shell-action"
            type="button"
            onClick={() => setAddOpen((v) => !v)}
          >
            + Add parameter
          </button>
        ) : null}
        {saving ? <StatusBadge label="Saving…" tone="accent" /> : null}
      </div>

      {addOpen ? (
        <div className="rounded-md border border-shell-line bg-white px-4 py-4 space-y-3">
          <p className="text-sm font-medium text-shell-ink">New parameter</p>
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Name
              <input
                autoFocus
                className="shell-field"
                placeholder="Temperature"
                type="text"
                value={addName}
                onChange={(e) => setAddName(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") handleAddParameter(); }}
              />
            </label>
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Type
              <select
                className="shell-field"
                value={addType}
                onChange={(e) => setAddType(e.target.value)}
              >
                <option value="FLOAT64">Float64 (decimal)</option>
                <option value="FLOAT32">Float32</option>
                <option value="INT32">Int32</option>
                <option value="INT64">Int64</option>
                <option value="UINT32">UInt32</option>
                <option value="INT16">Int16</option>
                <option value="BOOL">Boolean</option>
                <option value="STRING">String</option>
                <option value="DATETIME">DateTime</option>
              </select>
            </label>
            <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
              Unit
              <input
                className="shell-field"
                placeholder="°C, bar, rpm…"
                type="text"
                value={addUnit}
                onChange={(e) => setAddUnit(e.target.value)}
              />
            </label>
          </div>
          <div className="flex gap-2">
            <button
              className="shell-action"
              disabled={!addName.trim()}
              type="button"
              onClick={handleAddParameter}
            >
              Add
            </button>
            <button
              className="shell-action"
              type="button"
              onClick={() => { setAddOpen(false); setAddName(""); setAddUnit(""); }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(18rem,1fr)]">
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <input
              className="shell-field min-w-0 flex-1"
              placeholder="Search parameters…"
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <span className="whitespace-nowrap text-sm text-shell-muted">
              {allParams.length} parameters
            </span>
          </div>

          <div className="overflow-hidden rounded-md border border-shell-line bg-white max-h-[32rem] overflow-y-auto">
            {searchQuery ? (
              // Flat filtered list when searching
              filteredParams.length === 0 ? (
                <p className="px-4 py-6 text-center text-sm text-shell-muted">
                  No parameters match the search.
                </p>
              ) : (
                <ul className="divide-y divide-shell-line">
                  {filteredParams.map((param) => (
                    <li key={param.id}>
                      <button
                        className={`w-full px-4 py-3 text-left transition ${
                          selectedParam?.id === param.id
                            ? "bg-shell-accent/5"
                            : "hover:bg-shell-base/50"
                        }`}
                        type="button"
                        onClick={() => handleSelectParam(param)}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="min-w-0 flex-1 truncate font-mono text-sm text-shell-ink">
                            {param.path}
                          </span>
                          <span className="shrink-0 text-xs text-shell-muted">
                            {param.type.charAt(0).toUpperCase() + param.type.slice(1)}
                          </span>
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )
            ) : treeRoots.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-shell-muted">
                No schema nodes found.
              </p>
            ) : (
              // Tree view
              <ul className="py-1">
                {treeRoots.map((node) => (
                  <TreeNodeRow
                    key={node.nodeId}
                    node={node}
                    depth={0}
                    selectedId={selectedParam?.id ?? null}
                    expandedIds={expandedIds}
                    onToggle={handleToggle}
                    onSelect={handleSelectNode}
                  />
                ))}
              </ul>
            )}
          </div>
        </div>

        {selectedParam ? (
          <div className="space-y-4">
            <section className="rounded-md border border-shell-line bg-white px-4 py-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                    Parameter details
                  </p>
                  <p className="mt-2 font-mono text-sm text-shell-ink">
                    {selectedParam.path}
                  </p>
                </div>
                {!isLockedByOther && source.status !== "Active" ? (
                  <button
                    className="shrink-0 text-xs text-shell-danger hover:underline"
                    type="button"
                    onClick={() => { void handleDeleteParameter(selectedParam.id); }}
                  >
                    Delete
                  </button>
                ) : null}
              </div>
              <div className="mt-4 space-y-3">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Description
                  <input
                    className="shell-field"
                    disabled={isLockedByOther}
                    type="text"
                    value={editBuffer.description}
                    onChange={(e) => {
                      updateEditBuffer({ description: e.target.value });
                      setHasUnsavedChanges(true);
                    }}
                  />
                </label>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="flex flex-col gap-2 text-sm text-shell-muted">
                    Unit
                    <input
                      className="shell-field"
                      disabled={isLockedByOther}
                      type="text"
                      value={editBuffer.unit}
                      onChange={(e) => {
                        updateEditBuffer({ unit: e.target.value });
                        setHasUnsavedChanges(true);
                      }}
                    />
                  </label>
                  <div className="flex flex-col gap-2 text-sm text-shell-muted">
                    Type
                    <div className="shell-field cursor-not-allowed bg-shell-base/30 text-shell-ink">
                      {selectedParam.type.charAt(0).toUpperCase() + selectedParam.type.slice(1)}
                    </div>
                  </div>
                </div>

                {dependencyWarnings.length > 0 ? (
                  <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-amber-700">
                      Dependency impact
                    </p>
                    <ul className="mt-2 space-y-2">
                      {dependencyWarnings.map((warning, i) => (
                        <li key={i} className="flex items-start gap-2 text-sm text-shell-muted">
                          <span className="shrink-0 text-amber-600" aria-hidden="true">⚠</span>
                          <span>{warning}</span>
                        </li>
                      ))}
                    </ul>
                  </section>
                ) : null}
              </div>
            </section>
          </div>
        ) : (
          <section className="rounded-md border border-shell-line bg-white px-4 py-6">
            <p className="text-center text-sm text-shell-muted">
              Select a parameter from the tree to inspect or edit its details.
            </p>
          </section>
        )}
      </div>

      <p className="text-xs text-shell-muted">
        Schema changes take effect after the source is restarted. Type changes may
        break dependent parameters.
      </p>

      <ConfirmationDialog
        open={confirmSaveOpen}
        tone="warning"
        title="Save with dependency impact"
        message="This change may affect dependent artifacts. Confirm to save anyway."
        reversibilityLabel="Schema changes take effect after the source is restarted. They can be reverted by restoring previous values."
        confirmLabel="Confirm"
        cancelLabel="Cancel"
        onConfirm={handleConfirmSave}
        onClose={() => setConfirmSaveOpen(false)}
      />
    </div>
  );
}
