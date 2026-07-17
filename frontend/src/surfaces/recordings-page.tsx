import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { RecordingImportDialog } from "./recording-import-dialog";

export type RecordingRow = {
  id: string;
  name?: string;
  sourceId: string;
  sourceName: string;
  origin: "captured" | "imported";
  valueCount: number;
  capturedAt: string;
  capturedBy: string | undefined;
};

type OriginFilter = "all" | "captured" | "imported";

function originLabel(origin: "captured" | "imported"): string {
  return origin === "captured" ? "Recorded" : "Imported";
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

export function RecordingsPage() {
  const accessMode = useShellStore((s) => s.accessMode);
  const sharedRole = useShellStore((s) => s.sharedRole);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);
  const navigate = useNavigate();

  const storeArtifacts = useArtifactsStore((s) => s.artifacts);
  const isLoading = useArtifactsStore((s) => s.isLoading);
  const storeError = useArtifactsStore((s) => s.error);
  const loadRecordings = useArtifactsStore((s) => s.loadRecordings);
  const deleteRecording = useArtifactsStore((s) => s.deleteRecording);
  const push = useNotificationStore((s) => s.push);

  const dataSources = useDataSourcesStore((s) => s.dataSources);
  const loadDataSources = useDataSourcesStore((s) => s.loadDataSources);
  const sourceNameById = new Map(dataSources.map((ds) => [ds.id, ds.name]));

  const [originFilter, setOriginFilter] = useState<OriginFilter>("all");
  const [sourceFilter, setSourceFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);
  const [deleteRequest, setDeleteRequest] = useState<RecordingRow | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  useEffect(() => {
    if (currentProjectId) {
      void loadRecordings(currentProjectId);
      void loadDataSources(currentProjectId);
    }
  }, [currentProjectId, loadRecordings, loadDataSources]);

  const storeRows: RecordingRow[] = storeArtifacts.map((a) => ({
    id: a.id,
    name: a.name,
    sourceId: a.sourceId ?? "",
    sourceName: sourceNameById.get(a.sourceId ?? "") ?? "",
    origin: a.origin ?? "captured",
    valueCount: a.valueCount,
    capturedAt: a.createdAt,
    capturedBy: a.createdBy,
  }));

  const allRows: RecordingRow[] = storeRows;

  const uniqueSources = Array.from(
    new Map(allRows.map((r) => [r.sourceId, r.sourceName || r.sourceId])).entries(),
  );

  const filtered = allRows.filter((row) => {
    if (originFilter !== "all" && row.origin !== originFilter) return false;
    if (sourceFilter !== "all" && row.sourceId !== sourceFilter) return false;
    if (searchQuery.trim() !== "") {
      const q = searchQuery.toLowerCase();
      const capturedByMatch = (row.capturedBy ?? "").toLowerCase().includes(q);
      const sourceMatch = (row.sourceName || row.sourceId).toLowerCase().includes(q);
      const recordingNameMatch = (row.name ?? "").toLowerCase().includes(q);
      if (!capturedByMatch && !sourceMatch && !recordingNameMatch) return false;
    }
    return true;
  });

  async function confirmDelete() {
    if (!deleteRequest || !currentProjectId) {
      return;
    }
    setIsDeleting(true);
    try {
      await deleteRecording(currentProjectId, deleteRequest.id);
      setDeleteRequest(null);
    } catch (err) {
      const title =
        err instanceof ApiError ? (err.detail ?? err.title) : "Failed to delete recording";
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
            <h2 className="text-2xl font-semibold text-shell-ink">Recordings</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Captured data for replay and reuse.{" "}
              {allRows.length} recording{allRows.length !== 1 ? "s" : ""} in this project.
            </p>
          </div>
          {access.canCreateSource ? (
            <div className="flex flex-wrap gap-2">
              <button
                className="shell-action"
                type="button"
                onClick={() => navigate("/recordings/new")}
              >
                Create recording
              </button>
              <button
                className="shell-action"
                type="button"
                onClick={() => setImportDialogOpen(true)}
              >
                Import recording
              </button>
            </div>
          ) : null}
        </div>
      </section>

      {isLoading ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Loading recordings from the project."
            state="loading"
            title="Loading recordings…"
          />
        </section>
      ) : storeError ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message={storeError}
            state="error"
            title="Recordings could not be loaded."
          />
        </section>
      ) : null}

      <section className="shell-panel px-5 py-5">
        <div className="flex flex-wrap items-center gap-3">
          <input
            className="shell-field min-w-0 basis-48"
            placeholder="Search by source or author"
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <label className="flex items-center gap-2 text-sm text-shell-muted">
            Origin
            <select
              className="shell-field py-1.5"
              value={originFilter}
              onChange={(e) => setOriginFilter(e.target.value as OriginFilter)}
            >
              <option value="all">All origins</option>
              <option value="captured">Recorded</option>
              <option value="imported">Imported</option>
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-shell-muted">
            Source
            <select
              className="shell-field py-1.5"
              value={sourceFilter}
              onChange={(e) => setSourceFilter(e.target.value)}
            >
              <option value="all">All sources</option>
              {uniqueSources.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <span className="text-sm text-shell-muted">
            {filtered.length} result{filtered.length !== 1 ? "s" : ""}
          </span>
        </div>

        <div className="mt-5">
          {filtered.length === 0 && !isLoading ? (
            <SharedStatePanel
              message="No recordings match the active filters."
              state="empty"
              title="No results."
            />
          ) : (
            <div className="overflow-hidden rounded-md border border-shell-line bg-white">
              <ul className="divide-y divide-shell-line">
                {filtered.map((row) => (
                  <li key={row.id} className="relative">
                    <button
                      className="w-full px-4 py-4 text-left transition hover:bg-shell-base/50"
                      type="button"
                      onClick={() => navigate(`/recordings/${row.id}`)}
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2 pr-20">
                        <div className="min-w-0">
                          <p className="font-medium text-sm text-shell-ink">
                            {row.name || row.sourceName || row.sourceId || `Recording ${row.id.slice(0, 8)}`}
                          </p>
                          {row.name && row.sourceName ? (
                            <p className="mt-0.5 text-xs text-shell-muted">{row.sourceName}</p>
                          ) : null}
                          <p className="mt-1 text-xs text-shell-muted">
                            {formatDate(row.capturedAt)} · {row.capturedBy}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-1 shrink-0">
                          <StatusBadge label={originLabel(row.origin)} tone="neutral" />
                        </div>
                      </div>
                      <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-shell-muted">
                        <div>
                          <dt className="font-semibold uppercase tracking-wide">Values</dt>
                          <dd className="mt-1 text-shell-ink">{row.valueCount.toLocaleString()}</dd>
                        </div>
                        <div>
                          <dt className="font-semibold uppercase tracking-wide">Date</dt>
                          <dd className="mt-1 text-shell-ink">{formatDate(row.capturedAt)}</dd>
                        </div>
                      </dl>
                    </button>
                    {access.canCreateSource ? (
                      <button
                        aria-label={`Delete recording ${row.name || row.id}`}
                        className="shell-action-danger absolute right-4 top-4"
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setDeleteRequest(row);
                        }}
                      >
                        Delete
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </section>

      <RecordingImportDialog
        canImport={access.canCreateSource}
        open={importDialogOpen}
        projectId={currentProjectId ?? ""}
        onClose={() => setImportDialogOpen(false)}
        onImported={() => {
          if (currentProjectId) void loadRecordings(currentProjectId);
          setImportDialogOpen(false);
        }}
      />

      <ConfirmationDialog
        confirmLabel="Delete recording"
        impacts={[
          { label: "Source", value: deleteRequest?.sourceName || deleteRequest?.sourceId || "—" },
          { label: "Values", value: (deleteRequest?.valueCount ?? 0).toLocaleString() },
        ]}
        isProcessing={isDeleting}
        message="Deleting a recording removes it and its captured values from the project. It cannot be used if referenced by a scenario replay step or an active run."
        objectLabel={deleteRequest?.name || deleteRequest?.sourceName || deleteRequest?.id}
        open={deleteRequest !== null}
        reversibilityLabel="This action is not reversible. The recording must be captured or imported again if removed."
        title="Delete this recording?"
        tone="danger"
        onClose={() => (isDeleting ? undefined : setDeleteRequest(null))}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
