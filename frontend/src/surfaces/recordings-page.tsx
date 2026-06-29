import { useState } from "react";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { mockRecordings, type RecordingRow } from "./mock-recordings";
import { RecordingExportDialog } from "./recording-export-dialog";
import { RecordingImportDialog } from "./recording-import-dialog";

type TypeFilter = "all" | "recording" | "sample";
type OriginFilter = "all" | "captured" | "imported" | "synthetic";

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

// ─── Timeline helpers ─────────────────────────────────────────────────────────

/** Parse a duration string like "4h 12m", "8m 05s", "30m" into total minutes */
export function parseDurationMinutes(duration: string): number {
  const hourMatch = duration.match(/(\d+)h/);
  const minMatch = duration.match(/(\d+)m/);
  const hours = hourMatch ? parseInt(hourMatch[1], 10) : 0;
  const mins = minMatch ? parseInt(minMatch[1], 10) : 0;
  return hours * 60 + mins;
}

/** Format a sample rate like "9.8 values/min" or "0.5 values/min" */
function formatSampleRate(
  parameterCount: number,
  durationMinutes: number,
): string {
  if (durationMinutes === 0) return "—";
  const rate = parameterCount / durationMinutes;
  if (rate >= 1) return `${rate.toFixed(1)} values/min`;
  return `${(rate * 60).toFixed(1)} values/hr`;
}

/** Derive end timestamp from capturedAt string and duration in minutes */
function deriveEndTimestamp(
  capturedAt: string,
  durationMinutes: number,
): string {
  const date = new Date(capturedAt.replace(" ", "T"));
  if (isNaN(date.getTime())) return "—";
  date.setMinutes(date.getMinutes() + durationMinutes);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// ─── Fitness warning logic ────────────────────────────────────────────────────

type WarningEntry = { level: "warn" | "info"; message: string };

export function computeFitnessWarnings(row: RecordingRow): WarningEntry[] {
  const warnings: WarningEntry[] = [];

  if (row.origin === "synthetic") {
    warnings.push({
      level: "warn",
      message:
        "Synthetic data — not captured from a real source. Replay behavior may differ from real-device patterns.",
    });
  }

  if (row.lastUsedAt === null) {
    warnings.push({
      level: "info",
      message: "Never used in replay. No compatibility history.",
    });
  }

  if (row.sizeKb > 10240) {
    const mb = (row.sizeKb / 1024).toFixed(1);
    warnings.push({
      level: "info",
      message: `Large artifact (${mb} MB) — replay may take longer to initialise.`,
    });
  }

  if (row.parameterCount > 2000) {
    warnings.push({
      level: "info",
      message:
        "High parameter count — verify target source schema matches before assigning.",
    });
  }

  return warnings;
}

// ─── Compatibility hint ───────────────────────────────────────────────────────

function compatibilityHint(protocol: RecordingRow["protocol"]): string {
  return `Compatible with: ${protocol} sources`;
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
  const durationMinutes = parseDurationMinutes(selected.duration);
  const sampleRate = formatSampleRate(selected.parameterCount, durationMinutes);
  const endTimestamp = deriveEndTimestamp(selected.capturedAt, durationMinutes);
  const warnings = computeFitnessWarnings(selected);
  const sizeMb = (selected.sizeKb / 1024).toFixed(1);

  return (
    <div className="space-y-4">
      {/* Header + metadata */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
          Preview
        </p>
        <p className="mt-2 font-medium text-shell-ink">{selected.name}</p>
        <dl className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-shell-muted">
          <div className="flex items-center gap-1">
            <dt>Type</dt>
            <dd><StatusBadge label={selected.type} tone="neutral" /></dd>
          </div>
          <div className="flex items-center gap-1">
            <dt>Origin</dt>
            <dd><StatusBadge
              label={selected.origin}
              tone={selected.origin === "synthetic" ? "warning" : "neutral"}
            /></dd>
          </div>
        </dl>

        <dl className="mt-4 space-y-3 text-sm">
          <div>
            <dt className="text-shell-muted">Source</dt>
            <dd className="mt-1 text-shell-ink">{selected.sourceName}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Protocol</dt>
            <dd className="mt-1 text-shell-ink">{selected.protocol}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Captured by</dt>
            <dd className="mt-1 text-shell-ink">{selected.capturedBy}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Captured at</dt>
            <dd className="mt-1 text-shell-ink">{selected.capturedAt}</dd>
          </div>
          <div>
            <dt className="text-shell-muted">Last used</dt>
            <dd className="mt-1 text-shell-ink">
              {selected.lastUsedAt ?? "Not yet used"}
            </dd>
          </div>
          <div>
            <dt className="text-shell-muted">Size</dt>
            <dd className="mt-1 text-shell-ink">{sizeMb} MB</dd>
          </div>
        </dl>
      </section>

      {/* Timeline summary */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
          Timeline
        </p>
        <div className="mt-3">
          <div className="relative h-3 w-full overflow-hidden rounded-full bg-shell-base">
            <div
              className="absolute left-0 top-0 h-full w-full rounded-full bg-shell-accent"
              aria-hidden="true"
            />
          </div>
          <div className="mt-1 flex justify-between text-xs text-shell-muted">
            <span>{selected.capturedAt}</span>
            <span>{endTimestamp}</span>
          </div>
        </div>
        <dl className="mt-3 grid grid-cols-3 gap-2 text-xs">
          <div>
            <dt className="font-semibold uppercase tracking-wide text-shell-muted">
              Duration
            </dt>
            <dd className="mt-1 text-shell-ink">{selected.duration}</dd>
          </div>
          <div>
            <dt className="font-semibold uppercase tracking-wide text-shell-muted">
              Values recorded
            </dt>
            <dd className="mt-1 text-shell-ink">
              ~{selected.parameterCount.toLocaleString()}
            </dd>
          </div>
          <div>
            <dt className="font-semibold uppercase tracking-wide text-shell-muted">
              Sample rate
            </dt>
            <dd className="mt-1 text-shell-ink">{sampleRate}</dd>
          </div>
        </dl>
      </section>

      {/* Replay readiness */}
      <section className="rounded-md border border-shell-line bg-white px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
          Replay readiness
        </p>
        <p className="mt-2 text-xs text-shell-muted">{compatibilityHint(selected.protocol)}</p>
        {warnings.length > 0 ? (
          <div className="mt-3 space-y-2">
            {warnings.map((w, i) => (
              <FitnessWarning key={i} level={w.level} message={w.message} />
            ))}
          </div>
        ) : (
          <p className="mt-2 text-xs text-shell-muted">No issues detected.</p>
        )}
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

// ─── Page ─────────────────────────────────────────────────────────────────────

export function RecordingsPage() {
  const { accessMode, sharedRole } = useShellStore();
  const access = resolveAccess(accessMode, sharedRole);

  const [typeFilter, setTypeFilter] = useState<TypeFilter>("all");
  const [originFilter, setOriginFilter] = useState<OriginFilter>("all");
  const [sourceFilter, setSourceFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);
  const [exportDialogOpen, setExportDialogOpen] = useState<boolean>(false);
  const [localRecordings, setLocalRecordings] = useState<RecordingRow[]>(mockRecordings);

  const uniqueSources = Array.from(
    new Map(localRecordings.map((r) => [r.sourceId, r.sourceName])).entries(),
  );

  const filtered = localRecordings.filter((row) => {
    if (typeFilter !== "all" && row.type !== typeFilter) return false;
    if (originFilter !== "all" && row.origin !== originFilter) return false;
    if (sourceFilter !== "all" && row.sourceId !== sourceFilter) return false;
    if (searchQuery.trim() !== "") {
      const q = searchQuery.toLowerCase();
      const nameMatch = row.name.toLowerCase().includes(q);
      const tagMatch = row.tags.some((t) => t.toLowerCase().includes(q));
      if (!nameMatch && !tagMatch) return false;
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
            <RecordingPreviewPanel
              selected={selected}
              isAdmin={access.isAdmin}
              onExport={() => setExportDialogOpen(true)}
            />
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
          setLocalRecordings((prev) => [artifact, ...prev]);
          setImportDialogOpen(false);
        }}
      />
    </div>
  );
}
