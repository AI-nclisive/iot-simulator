import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type TabId = "schema" | "values";

type ValueEntry = {
  parameterId: string;
  parameterPath: string | null;
  timestamp: string;
  value: string | null;
  quality: string;
};

type SchemaNode = {
  nodeId: string;
  parentId: string | null;
  path: string;
  name: string | null;
  kind: string;
  dataType: string | null;
  valueRank: string | null;
  access: string | null;
  unit: string | null;
  description: string | null;
};

type Quality = "GOOD" | "UNCERTAIN" | "BAD";
const ALL_QUALITIES: Quality[] = ["GOOD", "UNCERTAIN", "BAD"];

type ValueFilters = {
  search: string;
  qualities: Set<Quality>;
  from: string;
  to: string;
};

const DEFAULT_FILTERS: ValueFilters = {
  search: "",
  qualities: new Set(ALL_QUALITIES),
  from: "",
  to: "",
};

function filtersToQs(filters: ValueFilters, cursor?: string): string {
  const params = new URLSearchParams();
  if (cursor) params.set("cursor", cursor);
  if (filters.search.trim()) params.set("search", filters.search.trim());
  if (filters.qualities.size < ALL_QUALITIES.length) {
    params.set("quality", [...filters.qualities].join(","));
  }
  if (filters.from) params.set("from", new Date(filters.from).toISOString());
  if (filters.to) params.set("to", new Date(filters.to).toISOString());
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

function activeFilterCount(filters: ValueFilters): number {
  let n = 0;
  if (filters.search.trim()) n++;
  if (filters.qualities.size < ALL_QUALITIES.length) n++;
  if (filters.from) n++;
  if (filters.to) n++;
  return n;
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

export function RecordingDetailPage() {
  const { recordingId } = useParams<{ recordingId: string }>();
  const navigate = useNavigate();
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const artifacts = useArtifactsStore((s) => s.artifacts);
  const isLoading = useArtifactsStore((s) => s.isLoading);
  const error = useArtifactsStore((s) => s.error);
  const loadRecordingById = useArtifactsStore((s) => s.loadRecordingById);

  const [activeTab, setActiveTab] = useState<TabId>("schema");

  // ── Values tab ──────────────────────────────────────────────────────────────
  const [values, setValues] = useState<ValueEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [valuesTotal, setValuesTotal] = useState<number | null>(null);
  const [valuesLoading, setValuesLoading] = useState(false);
  const [valuesError, setValuesError] = useState<string | null>(null);
  const [valuesLoaded, setValuesLoaded] = useState(false);

  const [filters, setFilters] = useState<ValueFilters>(DEFAULT_FILTERS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const searchDebounce = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── Schema tab ───────────────────────────────────────────────────────────────
  const [schemaNodes, setSchemaNodes] = useState<SchemaNode[] | null>(null);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (currentProjectId && recordingId) {
      void loadRecordingById(currentProjectId, recordingId);
    }
  }, [currentProjectId, recordingId, loadRecordingById]);

  useEffect(() => () => {
    if (searchDebounce.current) clearTimeout(searchDebounce.current);
  }, []);

  const recording = artifacts.find((a) => a.id === recordingId) ?? null;

  const loadValues = useCallback(
    async (cursor?: string, activeFilters?: ValueFilters) => {
      if (!currentProjectId || !recordingId) return;
      const f = activeFilters ?? filters;
      if (f.qualities.size === 0) {
        setValues([]);
        setNextCursor(null);
        setValuesTotal(0);
        setValuesLoaded(true);
        setValuesLoading(false);
        return;
      }
      setValuesLoading(true);
      if (!cursor) setValuesError(null);
      try {
        const qs = filtersToQs(f, cursor);
        const page = await apiFetch<{ items: ValueEntry[]; nextCursor: string | null; total: number }>(
          `/api/v1/projects/${currentProjectId}/recordings/${recordingId}/values${qs}`,
        );
        setValues((prev) => (cursor ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor ?? null);
        setValuesTotal(page.total);
        setValuesLoaded(true);
      } catch (err) {
        setValuesError(err instanceof ApiError ? err.title : "Failed to load values");
      } finally {
        setValuesLoading(false);
      }
    },
    [currentProjectId, recordingId, filters],
  );

  useEffect(() => {
    if (activeTab === "values" && !valuesLoaded && recording && recording.valueCount > 0) {
      void loadValues();
    }
  }, [activeTab, valuesLoaded, recording, loadValues]);

  const loadSchema = useCallback(async () => {
    if (!currentProjectId || !recordingId) return;
    setSchemaLoading(true);
    setSchemaError(null);
    try {
      const resp = await apiFetch<{ nodes: SchemaNode[] }>(
        `/api/v1/projects/${currentProjectId}/recordings/${recordingId}/schema`,
      );
      setSchemaNodes(resp.nodes);
    } catch (err) {
      setSchemaError(err instanceof ApiError ? err.title : "Failed to load schema");
    } finally {
      setSchemaLoading(false);
    }
  }, [currentProjectId, recordingId]);

  useEffect(() => {
    if (activeTab === "schema" && schemaNodes === null && !schemaLoading && !schemaError) {
      void loadSchema();
    }
  }, [activeTab, schemaNodes, schemaLoading, schemaError, loadSchema]);

  function applyFilters(next: ValueFilters) {
    setFilters(next);
    setValues([]);
    setNextCursor(null);
    setValuesTotal(null);
    setValuesLoaded(false);
    void loadValues(undefined, next);
  }

  function handleSearchChange(v: string) {
    const next = { ...filters, search: v };
    setFilters(next);
    if (searchDebounce.current) clearTimeout(searchDebounce.current);
    searchDebounce.current = setTimeout(() => {
      setValues([]);
      setNextCursor(null);
      setValuesTotal(null);
      setValuesLoaded(false);
      void loadValues(undefined, next);
    }, 300);
  }

  function toggleQuality(q: Quality) {
    const next = new Set(filters.qualities);
    if (next.has(q)) {
      next.delete(q);
    } else {
      next.add(q);
    }
    applyFilters({ ...filters, qualities: next });
  }

  function tabClass(tab: TabId) {
    return `border-b-2 px-4 py-2 text-sm font-medium transition ${
      activeTab === tab
        ? "border-shell-accent text-shell-ink"
        : "border-transparent text-shell-muted hover:text-shell-ink"
    }`;
  }

  function toggleCollapse(nodeId: string) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  }

  if (isLoading) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel message="Loading recording details." state="loading" title="Loading…" />
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel message={error} state="error" title="Could not load recording." />
        </section>
      </div>
    );
  }

  if (!recording) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="The recording could not be found. It may have been deleted."
            state="empty"
            title="Recording not found."
          />
        </section>
      </div>
    );
  }

  function renderSchemaTab() {
    if (schemaLoading) {
      return <SharedStatePanel message="Loading schema…" state="loading" title="" />;
    }
    if (schemaError) {
      return <SharedStatePanel message={schemaError} state="error" title="Failed to load schema." />;
    }
    if (schemaNodes === null) {
      return <SharedStatePanel message="Loading schema…" state="loading" title="" />;
    }
    if (schemaNodes.length === 0) {
      return (
        <SharedStatePanel
          message="No schema nodes were captured for this recording."
          state="empty"
          title="No schema captured."
        />
      );
    }

    const collapsedSet = collapsed;
    const hiddenIds = new Set<string>();
    for (const node of schemaNodes) {
      if (node.parentId && (hiddenIds.has(node.parentId) || collapsedSet.has(node.parentId))) {
        hiddenIds.add(node.nodeId);
      }
    }

    return (
      <div className="overflow-x-auto rounded-md border border-shell-line">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-shell-line bg-shell-base/55 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              <th className="px-4 py-3">Path</th>
              <th className="px-4 py-3">Kind</th>
              <th className="px-4 py-3">Data type</th>
              <th className="px-4 py-3">Unit</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-shell-line bg-white">
            {schemaNodes
              .filter((n) => !hiddenIds.has(n.nodeId))
              .map((n) => {
                const isFolder = n.kind === "FOLDER";
                const depth = n.path.split("/").length - 2;
                const isCollapsed = collapsedSet.has(n.nodeId);
                return (
                  <tr key={n.nodeId}>
                    <td className="px-4 py-2 font-mono text-xs text-shell-ink">
                      <span style={{ paddingLeft: `${depth * 16}px` }} className="flex items-center gap-1">
                        {isFolder ? (
                          <button
                            type="button"
                            className="text-shell-muted hover:text-shell-ink w-4 text-left"
                            onClick={() => toggleCollapse(n.nodeId)}
                          >
                            {isCollapsed ? "▶" : "▼"}
                          </button>
                        ) : (
                          <span className="w-4 text-shell-muted">·</span>
                        )}
                        {n.name ?? n.path.split("/").pop()}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs text-shell-muted">{n.kind}</td>
                    <td className="px-4 py-2 text-xs text-shell-muted">{n.dataType ?? "—"}</td>
                    <td className="px-4 py-2 text-xs text-shell-muted">{n.unit ?? "—"}</td>
                  </tr>
                );
              })}
          </tbody>
        </table>
      </div>
    );
  }

  function renderValuesTab() {
    if (recording!.valueCount === 0) {
      return (
        <SharedStatePanel
          message="No data values were captured in this recording. This recording contains schema only."
          state="empty"
          title="No values captured."
        />
      );
    }

    const filterCount = activeFilterCount(filters);

    return (
      <div className="space-y-4">
        {/* Filter bar */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={`shell-action flex items-center gap-1.5 ${filtersOpen ? "ring-1 ring-shell-accent" : ""}`}
            onClick={() => setFiltersOpen((o) => !o)}
          >
            Filters
            {filterCount > 0 ? (
              <span className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-shell-accent text-[10px] font-bold text-white">
                {filterCount}
              </span>
            ) : null}
          </button>
          {filterCount > 0 ? (
            <button
              type="button"
              className="text-xs text-shell-muted hover:text-shell-ink"
              onClick={() => applyFilters(DEFAULT_FILTERS)}
            >
              Clear all
            </button>
          ) : null}
          {valuesTotal !== null ? (
            <span className="ml-auto text-xs text-shell-muted">
              {valuesTotal.toLocaleString()} {filterCount > 0 ? "matching" : "total"}
            </span>
          ) : null}
        </div>

        {filtersOpen ? (
          <div className="rounded-md border border-shell-line bg-shell-base/55 p-4">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="lg:col-span-2">
                <label className="block text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted mb-1">
                  Search parameter
                </label>
                <input
                  type="text"
                  className="w-full rounded border border-shell-line bg-white px-3 py-1.5 text-sm text-shell-ink placeholder:text-shell-muted focus:outline-none focus:ring-1 focus:ring-shell-accent"
                  placeholder="Filter by path or node ID…"
                  value={filters.search}
                  onChange={(e) => handleSearchChange(e.target.value)}
                />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted mb-1">Quality</p>
                <div className="flex flex-col gap-1">
                  {ALL_QUALITIES.map((q) => (
                    <label key={q} className="flex items-center gap-2 text-sm text-shell-ink cursor-pointer">
                      <input
                        type="checkbox"
                        checked={filters.qualities.has(q)}
                        onChange={() => toggleQuality(q)}
                      />
                      {q}
                    </label>
                  ))}
                </div>
              </div>
              <div className="flex flex-col gap-2">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted mb-1">
                    From
                  </label>
                  <input
                    type="datetime-local"
                    className="w-full rounded border border-shell-line bg-white px-2 py-1.5 text-sm text-shell-ink focus:outline-none focus:ring-1 focus:ring-shell-accent"
                    value={filters.from}
                    onChange={(e) => applyFilters({ ...filters, from: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted mb-1">
                    To
                  </label>
                  <input
                    type="datetime-local"
                    className="w-full rounded border border-shell-line bg-white px-2 py-1.5 text-sm text-shell-ink focus:outline-none focus:ring-1 focus:ring-shell-accent"
                    value={filters.to}
                    onChange={(e) => applyFilters({ ...filters, to: e.target.value })}
                  />
                </div>
              </div>
            </div>
          </div>
        ) : null}

        {valuesError && values.length === 0 ? (
          <SharedStatePanel message={valuesError} state="error" title="Failed to load values." />
        ) : null}

        {valuesLoading && values.length === 0 ? (
          <SharedStatePanel message="Loading values…" state="loading" title="" />
        ) : null}

        {values.length > 0 ? (
          <div className="overflow-x-auto rounded-md border border-shell-line">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-shell-line bg-shell-base/55 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  <th className="px-4 py-3">Timestamp</th>
                  <th className="px-4 py-3">Parameter</th>
                  <th className="px-4 py-3">Value</th>
                  <th className="px-4 py-3">Quality</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-shell-line bg-white">
                {values.map((v, i) => (
                  <tr key={`${v.parameterId}-${v.timestamp}-${i}`}>
                    <td className="px-4 py-2 font-mono text-xs text-shell-muted whitespace-nowrap">
                      {formatDate(v.timestamp)}
                    </td>
                    <td className="px-4 py-2 font-mono text-xs text-shell-ink break-all">
                      {v.parameterPath ?? v.parameterId}
                    </td>
                    <td className="px-4 py-2 text-shell-ink">{v.value ?? "—"}</td>
                    <td className="px-4 py-2">
                      <span
                        className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${
                          v.quality === "GOOD"
                            ? "bg-green-50 text-green-700"
                            : "bg-amber-50 text-amber-700"
                        }`}
                      >
                        {v.quality}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {nextCursor ? (
          <div className="flex flex-col items-center gap-2">
            <button
              className="shell-action"
              disabled={valuesLoading}
              type="button"
              onClick={() => { setValuesError(null); void loadValues(nextCursor); }}
            >
              {valuesLoading ? "Loading…" : "Load more"}
            </button>
            {valuesError ? (
              <p className="text-xs text-red-600">{valuesError}</p>
            ) : null}
          </div>
        ) : null}

        {!nextCursor && values.length > 0 ? (
          <p className="text-center text-xs text-shell-muted">
            All {(valuesTotal ?? recording!.valueCount).toLocaleString()} values loaded.
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <button
              className="text-sm text-shell-muted hover:text-shell-ink transition"
              type="button"
              onClick={() => navigate("/recordings")}
            >
              ← Recordings
            </button>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">
              Recording{" "}
              <span className="font-mono text-lg text-shell-muted">{recording.id.slice(0, 8)}</span>
            </h2>
          </div>
          <StatusBadge
            label={recording.origin === "captured" ? "Recorded" : "Imported"}
            tone="neutral"
          />
        </div>
      </section>

      <section className="shell-panel px-5 py-5">
        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 text-sm">
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Data source
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.sourceId || "—"}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Captured at
            </dt>
            <dd className="mt-2 text-shell-ink">{formatDate(recording.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Captured by
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.createdBy || "—"}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Values recorded
            </dt>
            <dd className="mt-2 text-shell-ink">{recording.valueCount.toLocaleString()}</dd>
          </div>
        </dl>
      </section>

      <section className="shell-panel flex-1 px-5 py-0">
        <div className="flex gap-1 border-b border-shell-line">
          <button className={tabClass("schema")} type="button" onClick={() => setActiveTab("schema")}>
            Schema
          </button>
          <button className={tabClass("values")} type="button" onClick={() => setActiveTab("values")}>
            Values
          </button>
        </div>

        <div className="py-5">
          {activeTab === "schema" ? renderSchemaTab() : null}
          {activeTab === "values" ? renderValuesTab() : null}
        </div>
      </section>
    </div>
  );
}
