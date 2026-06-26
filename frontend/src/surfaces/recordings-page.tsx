import { useState } from "react";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { mockRecordings, type RecordingRow } from "./mock-recordings";

type TypeFilter = "all" | "recording" | "sample";
type OriginFilter = "all" | "captured" | "imported" | "synthetic";

export function RecordingsPage() {
  const { accessMode, sharedRole } = useShellStore();
  const access = resolveAccess(accessMode, sharedRole);

  const [typeFilter, setTypeFilter] = useState<TypeFilter>("all");
  const [originFilter, setOriginFilter] = useState<OriginFilter>("all");
  const [sourceFilter, setSourceFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);

  const uniqueSources = Array.from(
    new Set(mockRecordings.map((r) => r.sourceName)),
  );

  const filtered = mockRecordings.filter((row) => {
    if (typeFilter !== "all" && row.type !== typeFilter) return false;
    if (originFilter !== "all" && row.origin !== originFilter) return false;
    if (sourceFilter !== "all" && row.sourceName !== sourceFilter) return false;
    if (searchQuery.trim() !== "") {
      const q = searchQuery.toLowerCase();
      const nameMatch = row.name.toLowerCase().includes(q);
      const tagMatch = row.tags.some((t) => t.toLowerCase().includes(q));
      if (!nameMatch && !tagMatch) return false;
    }
    return true;
  });

  const selected: RecordingRow | null =
    mockRecordings.find((r) => r.id === selectedId) ?? null;

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
              {mockRecordings.length} artifact
              {mockRecordings.length !== 1 ? "s" : ""} in this project.
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

      <section className="shell-panel px-5 py-5">
        {/* Filters */}
        <div className="flex flex-wrap items-center gap-3">
          <input
            className="shell-field min-w-0 basis-48"
            placeholder="Search by name or tag…"
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <label className="flex items-center gap-2 text-sm text-shell-muted">
            Type
            <select
              className="shell-field py-1.5"
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
            >
              <option value="all">All types</option>
              <option value="recording">Recordings</option>
              <option value="sample">Samples</option>
            </select>
          </label>
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
              <option value="captured">Captured</option>
              <option value="imported">Imported</option>
              <option value="synthetic">Synthetic</option>
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
              {uniqueSources.map((name) => (
                <option key={name} value={name}>
                  {name}
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
            {filtered.length === 0 ? (
              <SharedStatePanel
                message="No recordings or samples match the active filters."
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
                              {row.name}
                            </p>
                            <p className="mt-1 text-xs text-shell-muted">
                              {row.sourceName} · {row.protocol}
                            </p>
                          </div>
                          <div className="flex flex-wrap gap-1 shrink-0">
                            <StatusBadge label={row.type} tone="neutral" />
                            <StatusBadge
                              label={row.origin}
                              tone={
                                row.origin === "synthetic"
                                  ? "warning"
                                  : "neutral"
                              }
                            />
                          </div>
                        </div>
                        <dl className="mt-3 grid grid-cols-3 gap-2 text-xs text-shell-muted">
                          <div>
                            <dt className="font-semibold uppercase tracking-wide">
                              Parameters
                            </dt>
                            <dd className="mt-1 text-shell-ink">
                              {row.parameterCount.toLocaleString()}
                            </dd>
                          </div>
                          <div>
                            <dt className="font-semibold uppercase tracking-wide">
                              Duration
                            </dt>
                            <dd className="mt-1 text-shell-ink">
                              {row.duration}
                            </dd>
                          </div>
                          <div>
                            <dt className="font-semibold uppercase tracking-wide">
                              Captured
                            </dt>
                            <dd className="mt-1 text-shell-ink">
                              {row.capturedAt}
                            </dd>
                          </div>
                        </dl>
                        {row.tags.length > 0 ? (
                          <div className="mt-2 flex flex-wrap gap-1">
                            {row.tags.map((tag) => (
                              <span
                                key={tag}
                                className="rounded bg-shell-base/80 px-2 py-0.5 text-xs text-shell-muted"
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        ) : null}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Preview panel */}
          {selected ? (
            <div className="space-y-4">
              <section className="rounded-md border border-shell-line bg-white px-4 py-4">
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Preview
                </p>
                <p className="mt-2 font-medium text-shell-ink">
                  {selected.name}
                </p>
                <dl className="mt-4 space-y-3 text-sm">
                  <div>
                    <dt className="text-shell-muted">Source</dt>
                    <dd className="mt-1 text-shell-ink">
                      {selected.sourceName}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-shell-muted">Captured by</dt>
                    <dd className="mt-1 text-shell-ink">
                      {selected.capturedBy}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-shell-muted">Last used</dt>
                    <dd className="mt-1 text-shell-ink">
                      {selected.lastUsedAt ?? "Not yet used"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-shell-muted">Size</dt>
                    <dd className="mt-1 text-shell-ink">
                      {(selected.sizeKb / 1024).toFixed(1)} MB
                    </dd>
                  </div>
                </dl>
                <div className="mt-4 flex flex-wrap gap-2">
                  {access.canCreateSource ? (
                    <>
                      <button className="shell-action" type="button">
                        Assign to replay
                      </button>
                      <button className="shell-action" type="button">
                        Export
                      </button>
                    </>
                  ) : (
                    <>
                      <button className="shell-action" disabled type="button">
                        Assign to replay
                      </button>
                      <button className="shell-action" disabled type="button">
                        Export
                      </button>
                    </>
                  )}
                </div>
                {!access.canCreateSource ? (
                  <p className="mt-3 text-xs text-shell-muted">
                    Export and assign actions are available to Admins only in
                    shared mode.
                  </p>
                ) : null}
              </section>
            </div>
          ) : (
            <section className="hidden rounded-md border border-shell-line bg-white px-4 py-6 xl:block">
              <p className="text-sm text-center text-shell-muted">
                Select a recording or sample to preview its details and
                available actions.
              </p>
            </section>
          )}
        </div>
      </section>

      {/* Import dialog stub (mock) */}
      {importDialogOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <section className="w-full max-w-md rounded-md border border-shell-line bg-white px-6 py-6 shadow-lg">
            <h3 className="text-lg font-semibold text-shell-ink">
              Import artifact
            </h3>
            <p className="mt-2 text-sm text-shell-muted">
              Import a recording, sample, or schema package from a file. Full
              import flow is implemented in UI-051.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                className="shell-action"
                type="button"
                onClick={() => setImportDialogOpen(false)}
              >
                Cancel
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
