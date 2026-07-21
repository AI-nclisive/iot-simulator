import { useEffect, useState } from "react";
import { ApiError } from "../api";
import { resolveAccess } from "../shell/access-policy";
import { useManualSchemasStore, type ManualSchemaResponse } from "../shell/manual-schemas-store";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";

function variableCount(schema: ManualSchemaResponse): number {
  return schema.nodes.filter((n) => n.kind === "VARIABLE").length;
}

function protocolLabel(protocol: string): string {
  return protocol === "OPC_UA" ? "OPC UA" : protocol === "MODBUS_TCP" ? "Modbus TCP" : protocol;
}

export function ManualSchemasPage() {
  const accessMode = useShellStore((s) => s.accessMode);
  const sharedRole = useShellStore((s) => s.sharedRole);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  const schemas = useManualSchemasStore((s) => s.schemas);
  const isLoading = useManualSchemasStore((s) => s.isLoading);
  const storeError = useManualSchemasStore((s) => s.error);
  const loadManualSchemas = useManualSchemasStore((s) => s.loadManualSchemas);
  const createManualSchema = useManualSchemasStore((s) => s.createManualSchema);
  const duplicateManualSchema = useManualSchemasStore((s) => s.duplicateManualSchema);
  const deleteManualSchema = useManualSchemasStore((s) => s.deleteManualSchema);
  const push = useNotificationStore((s) => s.push);

  const [searchQuery, setSearchQuery] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createProtocol, setCreateProtocol] = useState("OPC_UA");
  const [isCreating, setIsCreating] = useState(false);
  const [duplicateRequest, setDuplicateRequest] = useState<ManualSchemaResponse | null>(null);
  const [duplicateName, setDuplicateName] = useState("");
  const [isDuplicating, setIsDuplicating] = useState(false);
  const [deleteRequest, setDeleteRequest] = useState<ManualSchemaResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    if (currentProjectId) void loadManualSchemas(currentProjectId);
  }, [currentProjectId, loadManualSchemas]);

  const filtered = schemas.filter((s) => {
    if (searchQuery.trim() === "") return true;
    return s.name.toLowerCase().includes(searchQuery.toLowerCase());
  });

  async function confirmCreate() {
    const name = createName.trim();
    if (!name || !currentProjectId) return;
    setIsCreating(true);
    try {
      await createManualSchema(currentProjectId, {
        protocol: createProtocol,
        name,
      });
      setCreateOpen(false);
      setCreateName("");
      push({ tone: "success", title: `Created "${name}".` });
    } catch (err) {
      const title =
        err instanceof ApiError ? (err.detail ?? err.title) : "Failed to create manual schema";
      push({ tone: "error", title });
    } finally {
      setIsCreating(false);
    }
  }

  async function confirmDuplicate() {
    const name = duplicateName.trim();
    if (!duplicateRequest || !name || !currentProjectId) return;
    setIsDuplicating(true);
    try {
      await duplicateManualSchema(currentProjectId, duplicateRequest.id, name);
      setDuplicateRequest(null);
    } catch (err) {
      const title =
        err instanceof ApiError ? (err.detail ?? err.title) : "Failed to duplicate manual schema";
      push({ tone: "error", title });
    } finally {
      setIsDuplicating(false);
    }
  }

  async function confirmDelete() {
    if (!deleteRequest || !currentProjectId) return;
    setIsDeleting(true);
    try {
      await deleteManualSchema(currentProjectId, deleteRequest.id);
      setDeleteRequest(null);
    } catch (err) {
      const title =
        err instanceof ApiError ? (err.detail ?? err.title) : "Failed to delete manual schema";
      push({ tone: "error", title });
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-2xl font-semibold text-shell-ink">Manual Schemas</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Reusable, hand-authored structure — no data source required. Use one as the
              parameter set for a synthetic data source.{" "}
              {schemas.length} schema{schemas.length !== 1 ? "s" : ""} in this project.
            </p>
          </div>
          {access.isAdmin ? (
            <div className="flex flex-wrap gap-2">
              <button className="shell-action" type="button" onClick={() => setCreateOpen(true)}>
                Create manual schema
              </button>
            </div>
          ) : null}
        </div>
      </section>

      {isLoading ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Loading manual schemas from the project."
            state="loading"
            title="Loading manual schemas…"
          />
        </section>
      ) : storeError ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message={storeError}
            state="error"
            title="Manual schemas could not be loaded."
          />
        </section>
      ) : null}

      <section className="shell-panel px-5 py-5">
        <div className="flex flex-wrap items-center gap-3">
          <input
            className="shell-field min-w-0 basis-48"
            placeholder="Search by name"
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <span className="text-sm text-shell-muted">
            {filtered.length} result{filtered.length !== 1 ? "s" : ""}
          </span>
        </div>

        <div className="mt-5">
          {filtered.length === 0 && !isLoading ? (
            <SharedStatePanel
              message="No manual schemas match the active filters."
              state="empty"
              title="No results."
            />
          ) : (
            <div className="overflow-hidden rounded-md border border-shell-line bg-white">
              <ul className="divide-y divide-shell-line">
                {filtered.map((schema) => (
                  <li key={schema.id} className="relative">
                    <div className="w-full px-4 py-4 pr-32">
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="font-medium text-sm text-shell-ink">{schema.name}</p>
                          {schema.description ? (
                            <p className="mt-0.5 text-xs text-shell-muted">{schema.description}</p>
                          ) : null}
                        </div>
                      </div>
                      <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-shell-muted">
                        <div>
                          <dt className="font-semibold uppercase tracking-wide">Protocol</dt>
                          <dd className="mt-1 text-shell-ink">{protocolLabel(schema.protocol)}</dd>
                        </div>
                        <div>
                          <dt className="font-semibold uppercase tracking-wide">Variables</dt>
                          <dd className="mt-1 text-shell-ink">{variableCount(schema)}</dd>
                        </div>
                      </dl>
                    </div>
                    {access.isAdmin ? (
                      <div className="absolute right-4 top-4 flex gap-3">
                        <button
                          className="shell-text-action"
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setDuplicateName(`${schema.name} (copy)`);
                            setDuplicateRequest(schema);
                          }}
                        >
                          Duplicate
                        </button>
                        <button
                          aria-label={`Delete manual schema ${schema.name}`}
                          className="shell-text-action-danger"
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setDeleteRequest(schema);
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </section>

      {createOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
          <button
            aria-label="Close create dialog"
            className="absolute inset-0"
            type="button"
            onClick={() => (isCreating ? undefined : setCreateOpen(false))}
          />
          <div className="relative z-10 w-full max-w-md rounded-lg border border-shell-line bg-white shadow-panel">
            <div className="border-b border-shell-line px-5 py-4">
              <h2 className="text-lg font-semibold text-shell-ink">Create manual schema</h2>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Start from an empty structure, then add folders and variables in the editor.
              </p>
            </div>
            <div className="space-y-3 px-5 py-4">
              <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                Name
                <input
                  autoFocus
                  className="shell-field"
                  placeholder="Boiler layout"
                  type="text"
                  value={createName}
                  onChange={(e) => setCreateName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") void confirmCreate();
                  }}
                />
              </label>
              <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                Protocol
                <select
                  className="shell-field"
                  value={createProtocol}
                  onChange={(e) => setCreateProtocol(e.target.value)}
                >
                  <option value="OPC_UA">OPC UA</option>
                </select>
              </label>
            </div>
            <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
              <button
                className="shell-action"
                disabled={isCreating}
                type="button"
                onClick={() => setCreateOpen(false)}
              >
                Cancel
              </button>
              <button
                className="shell-action"
                disabled={isCreating || !createName.trim()}
                type="button"
                onClick={() => void confirmCreate()}
              >
                {isCreating ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {duplicateRequest ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-shell-ink/45 px-4 py-8">
          <button
            aria-label="Close duplicate dialog"
            className="absolute inset-0"
            type="button"
            onClick={() => (isDuplicating ? undefined : setDuplicateRequest(null))}
          />
          <div className="relative z-10 w-full max-w-md rounded-lg border border-shell-line bg-white shadow-panel">
            <div className="border-b border-shell-line px-5 py-4">
              <h2 className="text-lg font-semibold text-shell-ink">Duplicate manual schema</h2>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Creates an independent copy of "{duplicateRequest.name}" with a new name.
              </p>
            </div>
            <div className="px-5 py-4">
              <label className="flex flex-col gap-1.5 text-sm text-shell-muted">
                New name
                <input
                  autoFocus
                  className="shell-field"
                  type="text"
                  value={duplicateName}
                  onChange={(e) => setDuplicateName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") void confirmDuplicate();
                  }}
                />
              </label>
            </div>
            <div className="flex flex-col-reverse gap-2 border-t border-shell-line px-5 py-4 sm:flex-row sm:items-center sm:justify-end">
              <button
                className="shell-action"
                disabled={isDuplicating}
                type="button"
                onClick={() => setDuplicateRequest(null)}
              >
                Cancel
              </button>
              <button
                className="shell-action"
                disabled={isDuplicating || !duplicateName.trim()}
                type="button"
                onClick={() => void confirmDuplicate()}
              >
                {isDuplicating ? "Duplicating…" : "Duplicate"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <ConfirmationDialog
        confirmLabel="Delete manual schema"
        isProcessing={isDeleting}
        message="Deleting a manual schema removes it from the library. Data sources already created from it keep their own copy of the structure and are not affected."
        objectLabel={deleteRequest?.name}
        open={deleteRequest !== null}
        reversibilityLabel="This action is not reversible. The schema must be recreated if removed."
        title="Delete this manual schema?"
        tone="danger"
        onClose={() => (isDeleting ? undefined : setDeleteRequest(null))}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
