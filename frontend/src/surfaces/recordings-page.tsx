import { useEffect, useState } from "react";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { RecordingExportDialog } from "./recording-export-dialog";
import { RecordingImportDialog } from "./recording-import-dialog";

// Display-layer shape for the recordings list — derived from live RecordingResponse via artifacts-store
export type RecordingRow = {
  id: string;
  sourceId: string;
  origin: "captured" | "imported";
  valueCount: number;
  capturedAt: string;
  capturedBy: string;
};

type OriginFilter = "all" | "captured" | "imported";

function originLabel(origin: "captured" | "imported"): string {
  return origin === "captured" ? "Recorded" : "Imported";
}

// ─── Fitness warning component ────────────────────────────────────────────────

type FitnessWarningProps = {
  level: "warn" | "info";
  message: string;
};

export function FitnessWarning({ level, message }: FitnessWarningProps) {
  const isWarn = level === "warn";
  return (
    <div
      className={`flex items-start gap-2 rounded px-3 py-2 text-xs ${
        isWarn
          ? "bg-yellow-50 border border-yellow-200 text-yellow-800"
          : "bg-blue-50 border border-blue-200 text-blue-800"
      }`}
      role={isWarn ? "alert" : "status"}
    >
      <span className="shrink-0 font-semibold">
        {isWarn ? "Warning:" : "Note:"}
      </span>
      <span>{message}</span>
    </div>
  );
}

// ─── Preview panel ────────────────────────────────────────────────────────────

function RecordingPreviewPanel({
  selected,
  isAdmin,
  onExport,
}: {
  selected: RecordingRow;
  isAdmin: boolean;
  onExport: () => void;
}) {
  return (
    <div className="space-y-4">
      {/* Header + metadata */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
          Preview
        </p>
        <p className="mt-2 font-medium text-shell-ink">{selected.id}</p>
        <dl className="mt-4 space-y-3 text-sm">
          <div>
            <dt className="text-shell-muted">Source ID</dt>
            <dd className="mt-1 text-shell-ink">{selected.sourceId}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Type</dt>
            <dd className="mt-1 text-shell-ink">{originLabel(selected.origin)}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Recorded by</dt>
            <dd className="mt-1 text-shell-ink">{selected.capturedBy}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Recorded at</dt>
            <dd className="mt-1 text-shell-ink">{selected.capturedAt}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Values recorded</dt>
            <dd className="mt-1 text-shell-ink">{selected.valueCount.toLocaleString()}</dd>
          </div>
        </dl>
      </section>

      {/* Replay readiness */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
          Replay readiness
        </p>
        <p className="mt-2 text-xs text-shell-muted">
          No protocol information available until API is wired.
        </p>
      </section>

      {/* Action buttons */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <div className="flex flex-wrap gap-2">
          <button className="shell-action" disabled={!isAdmin} type="button">
            Assign to replay
          </button>
          <button
            className="shell-action"
            disabled={!isAdmin}
            type="button"
            onClick={isAdmin ? onExport : undefined}
          >
            Export
          </button>
        </div>
        {!isAdmin ? (
          <p className="mt-3 text-xs text-shell-muted">
            Export and assign actions are available to Admins only in shared
            mode.
          </p>
        ) : null}
      </section>
    </div>
  );
}

// ─── Samples section ──────────────────────────────────────────────────────────

function SamplesSection({
  projectId,
  recordingsById,
  isAdmin,
}: {
  projectId: string;
  recordingsById: Map<string, RecordingRow>;
  isAdmin: boolean;
}) {
  const samples = useArtifactsStore((s) => s.samples);
  const isSamplesLoading = useArtifactsStore((s) => s.isSamplesLoading);
  const samplesError = useArtifactsStore((s) => s.samplesError);
  const loadSamples = useArtifactsStore((s) => s.loadSamples);
  const deleteSample = useArtifactsStore((s) => s.deleteSample);

  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (projectId) {
      void loadSamples(projectId);
    }
  }, [projectId, loadSamples]);

  async function handleDelete(sampleId: string) {
    setDeletingId(sampleId);
    setDeleteError(null);
    try {
      await deleteSample(projectId, sampleId);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Delete failed";
      setDeleteError(msg);
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <section className="shell-panel px-5 py-5" data-testid="samples-section">
      <h3 className="text-lg font-semibold text-shell-ink">Samples</h3>
      <p className="mt-1 text-sm text-shell-muted">
        Named subsets derived from recordings.
      </p>

      {deleteError ? (
        <p className="mt-4 text-sm text-red-600" role="alert">{deleteError}</p>
      ) : null}
      {isSamplesLoading ? (
        <p className="mt-4 text-sm text-shell-muted">Loading samples…</p>
      ) : samplesError ? (
        <p className="mt-4 text-sm text-red-600" role="alert">{samplesError}</p>
      ) : samples.length === 0 ? (
        <SharedStatePanel
          message="No samples have been created for this project yet."
          state="empty"
          title="No samples."
        />
      ) : (
        <div className="mt-4 overflow-hidden rounded-md border border-shell-line bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-shell-line bg-shell-base/60 text-left text-xs font-semibold uppercase tracking-wide text-shell-muted">
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Source recording</th>
                <th className="px-4 py-3">Tags</th>
                <th className="px-4 py-3">Created at</th>
                <th className="px-4 py-3">Created by</th>
                {isAdmin ? <th className="px-4 py-3 sr-only">Actions</th> : null}
              </tr>
            </thead>
            <tbody className="divide-y divide-shell-line">
              {samples.map((sample) => {
                const sourceRecording = recordingsById.get(sample.derivedFromRecordingId);
                return (
                  <tr key={sample.id} className="hover:bg-shell-base/40">
                    <td className="px-4 py-3 font-medium text-shell-ink">
                      {sample.name}
                    </td>
                    <td className="px-4 py-3 text-shell-muted">
                      {sourceRecording
                        ? sourceRecording.sourceId
                        : sample.derivedFromRecordingId}
                    </td>
                    <td className="px-4 py-3">
                      {sample.tags.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {sample.tags.map((tag) => (
                            <span
                              key={tag}
                              className="rounded bg-shell-base px-1.5 py-0.5 text-xs text-shell-muted"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-shell-muted">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-shell-muted">
                      {sample.createdAt}
                    </td>
                    <td className="px-4 py-3 text-shell-muted">
                      {sample.createdBy}
                    </td>
                    {isAdmin ? (
                      <td className="px-4 py-3">
                        <button
                          className="text-xs text-red-600 hover:underline disabled:opacity-50"
                          disabled={deletingId === sample.id}
                          type="button"
                          onClick={() => void handleDelete(sample.id)}
                        >
                          {deletingId === sample.id ? "Deleting…" : "Delete"}
                        </button>
                      </td>
                    ) : null}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function RecordingsPage() {
  const accessMode = useShellStore((s) => s.accessMode);
  const sharedRole = useShellStore((s) => s.sharedRole);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  const storeArtifacts = useArtifactsStore((s) => s.artifacts);
  const isLoading = useArtifactsStore((s) => s.isLoading);
  const storeError = useArtifactsStore((s) => s.error);
  const loadRecordings = useArtifactsStore((s) => s.loadRecordings);

  const [originFilter, setOriginFilter] = useState<OriginFilter>("all");
  const [sourceFilter, setSourceFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);
  const [exportDialogOpen, setExportDialogOpen] = useState<boolean>(false);
  // localRecordings holds artifacts appended from import (before next reload)
  const [importedRows, setImportedRows] = useState<RecordingRow[]>([]);

  // Load live recordings from API on mount / project change
  useEffect(() => {
    if (currentProjectId) {
      void loadRecordings(currentProjectId);
    }
  }, [currentProjectId, loadRecordings]);

  // Map store artifacts to RecordingRow for display
  const storeRows: RecordingRow[] = storeArtifacts.map((a) => ({
    id: a.id,
    sourceId: a.sourceId ?? "",
    origin: a.origin ?? "captured",
    valueCount: a.valueCount,
    capturedAt: a.createdAt,
    capturedBy: a.createdBy,
  }));

  // Merge store rows with any locally imported rows (deduplicate by id)
  const storeIds = new Set(storeRows.map((r) => r.id));
  const localRecordings: RecordingRow[] = [
    ...storeRows,
    ...importedRows.filter((r) => !storeIds.has(r.id)),
  ];

  // Build a lookup map for samples section
  const recordingsById = new Map<string, RecordingRow>(
    localRecordings.map((r) => [r.id, r]),
  );

  const uniqueSourceIds = Array.from(new Set(localRecordings.map((r) => r.sourceId)));

  const filtered = localRecordings.filter((row) => {
    if (originFilter !== "all" && row.origin !== originFilter) return false;
    if (sourceFilter !== "all" && row.sourceId !== sourceFilter) return false;
    if (searchQuery.trim() !== "") {
      const q = searchQuery.toLowerCase();
      const capturedByMatch = row.capturedBy.toLowerCase().includes(q);
      const sourceIdMatch = row.sourceId.toLowerCase().includes(q);
      if (!capturedByMatch && !sourceIdMatch) return false;
    }
    return true;
  });

  const selected: RecordingRow | null =
    localRecordings.find((r) => r.id === selectedId) ?? null;

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-2xl font-semibold text-shell-ink">
              Recordings &amp; Samples
            </h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Reusable captured data for replay and comparison.{" "}
              {localRecordings.length} artifact
              {localRecordings.length !== 1 ? "s" : ""} in this project.
            </p>
          </div>
          {access.canCreateSource ? (
            <button
              className="shell-action"
              type="button"
              onClick={() => setImportDialogOpen(true)}
            >
              Import artifact
            </button>
          ) : null}
        </div>
      </section>

      {isLoading ? (
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Loading recordings and samples from the project."
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
        {/* Filters */}
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
              onChange={(e) =>
                setOriginFilter(e.target.value as OriginFilter)
              }
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
              {uniqueSourceIds.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
          <span className="text-sm text-shell-muted">
            {filtered.length} result{filtered.length !== 1 ? "s" : ""}
          </span>
        </div>

        {/* List + preview grid */}
        <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(18rem,1fr)]">
          {/* List */}
          <div>
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
                    <li key={row.id}>
                      <button
                        className={`w-full px-4 py-4 text-left transition ${
                          selectedId === row.id
                            ? "bg-shell-accent/5"
                            : "hover:bg-shell-base/50"
                        }`}
                        type="button"
                        onClick={() =>
                          setSelectedId(
                            row.id === selectedId ? null : row.id,
                          )
                        }
                      >
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="font-medium text-sm text-shell-ink">
                              {row.sourceId}
                            </p>
                            <p className="mt-1 text-xs text-shell-muted">
                              {row.capturedAt} · {row.capturedBy}
                            </p>
                          </div>
                          <div className="flex flex-wrap gap-1 shrink-0">
                            <StatusBadge label={originLabel(row.origin)} tone="neutral" />
                          </div>
                        </div>
                        <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-shell-muted">
                          <div>
                            <dt className="font-semibold uppercase tracking-wide">
                              Values
                            </dt>
                            <dd className="mt-1 text-shell-ink">
                              {row.valueCount.toLocaleString()}
                            </dd>
                          </div>
                          <div>
                            <dt className="font-semibold uppercase tracking-wide">
                              Date
                            </dt>
                            <dd className="mt-1 text-shell-ink">
                              {row.capturedAt}
                            </dd>
                          </div>
                        </dl>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Preview panel */}
          {selected ? (
            <RecordingPreviewPanel
              selected={selected}
              isAdmin={access.isAdmin}
              onExport={() => setExportDialogOpen(true)}
            />
          ) : (
            <section className="hidden rounded-md border border-shell-line bg-white px-4 py-6 xl:block">
              <p className="text-sm text-center text-shell-muted">
                Select a recording to preview its details and
                available actions.
              </p>
            </section>
          )}
        </div>
      </section>

      {/* Samples section */}
      <SamplesSection
        projectId={currentProjectId}
        recordingsById={recordingsById}
        isAdmin={access.isAdmin}
      />

      {/* Export dialog */}
      {selected && access.isAdmin ? (
        <RecordingExportDialog
          open={exportDialogOpen}
          recording={selected}
          onClose={() => setExportDialogOpen(false)}
        />
      ) : null}

      <RecordingImportDialog
        canImport={access.canCreateSource}
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        onImported={(artifact) => {
          setImportedRows((prev) => [artifact, ...prev]);
          setImportDialogOpen(false);
        }}
      />
    </div>
  );
}
