import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiFetch, ApiError } from "../api";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type TabId = "schema" | "values";

type SchemaNode = {
  nodeId: string;
  parentId: string | null;
  path: string;
  name: string;
  kind: "FOLDER" | "VARIABLE";
  dataType: string | null;
};

type ValueEntry = {
  parameterId: string;
  parameterPath: string | null;
  timestamp: string;
  value: string | null;
  quality: string;
};

type Quality = "GOOD" | "UNCERTAIN" | "BAD";
const ALL_QUALITIES: Quality[] = ["GOOD", "UNCERTAIN", "BAD"];

type ValueFilters = {
  qualities: Set<Quality>;
  search: string;
  from: string;
  to: string;
};

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

export function buildValuesQs(filters: ValueFilters, cursor?: string): string {
  const params = new URLSearchParams();
  if (cursor) params.set("cursor", cursor);
  if (filters.qualities.size < ALL_QUALITIES.length) {
    params.set("quality", [...filters.qualities].join(","));
  }
  if (filters.search.trim()) params.set("search", filters.search.trim());
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  const qs = params.toString();
  return qs ? `?${qs}` : "";
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

  // Schema tab state
  const [schemaNodes, setSchemaNodes] = useState<SchemaNode[] | null>(null);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  // Values tab state
  const [values, setValues] = useState<ValueEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [valuesTotal, setValuesTotal] = useState<number | null>(null);
  const [valuesLoading, setValuesLoading] = useState(false);
  const [valuesError, setValuesError] = useState<string | null>(null);
  const [valuesLoaded, setValuesLoaded] = useState(false);

  // Filter state — displayed values (UI bound)
  const [filters, setFilters] = useState<ValueFilters>({
    qualities: new Set<Quality>(ALL_QUALITIES),
    search: "",
    from: "",
    to: "",
  });
  // Committed search: updated after debounce fires
  const [committedSearch, setCommittedSearch] = useState("");
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Ref that always holds the latest effective filters so loadValues can read
  // them without being re-created on every filter change.
  const filtersRef = useRef<ValueFilters>({
    qualities: new Set<Quality>(ALL_QUALITIES),
    search: "",
    from: "",
    to: "",
  });

  // Generation counter: incremented on every new filter-triggered loadValues call.
  // loadValues captures its generation at call time and bails if stale after the
  // fetch resolves (prevents out-of-order responses overwriting correct data).
  const loadGenRef = useRef(0);

  // When filters change while the initial load is still in progress, record that a
  // refresh is needed. The post-load effect checks this flag and re-fetches instead
  // of just initializing the prevFiltersKey.
  const pendingFilterRefreshRef = useRef(false);

  // Keep filtersRef in sync whenever filters or committedSearch change
  useEffect(() => {
    filtersRef.current = { ...filters, search: committedSearch };
  });

  useEffect(() => {
    if (currentProjectId && recordingId) {
      void loadRecordingById(currentProjectId, recordingId);
    }
  }, [currentProjectId, recordingId, loadRecordingById]);

  // Cleanup debounce timer on unmount
  useEffect(() => {
    return () => {
      if (searchDebounceRef.current !== null) {
        clearTimeout(searchDebounceRef.current);
      }
    };
  }, []);

  const recording = artifacts.find((a) => a.id === recordingId) ?? null;

  const loadSchema = useCallback(async () => {
    if (!currentProjectId || !recordingId) return;
    setSchemaLoading(true);
    try {
      const data = await apiFetch<{ nodes: SchemaNode[] }>(
        `/api/v1/projects/${currentProjectId}/recordings/${recordingId}/schema`,
      );
      setSchemaNodes(data.nodes ?? []);
    } catch (err) {
      setSchemaError(err instanceof ApiError ? err.title : "Failed to load schema.");
    } finally {
      setSchemaLoading(false);
    }
  }, [currentProjectId, recordingId]);

  useEffect(() => {
    if (activeTab === "schema" && schemaNodes === null && !schemaLoading && !schemaError) {
      void loadSchema();
    }
  }, [activeTab, schemaNodes, schemaLoading, schemaError, loadSchema]);

  const loadValues = useCallback(
    async (cursor?: string, gen?: number) => {
      if (!currentProjectId || !recordingId) return;
      const activeFilters = filtersRef.current;
      // Capture the generation for this call. If no gen was provided, bump the counter
      // (used for initial load and "Load more" clicks which don't race).
      const callGen = gen ?? ++loadGenRef.current;
      setValuesLoading(true);
      setValuesError(null);
      try {
        const qs = buildValuesQs(activeFilters, cursor);
        const page = await apiFetch<{ items: ValueEntry[]; nextCursor: string | null; total: number }>(
          `/api/v1/projects/${currentProjectId}/recordings/${recordingId}/values${qs}`,
        );
        // Bail if a newer request has already been dispatched
        if (callGen !== loadGenRef.current) return;
        setValues((prev) => (cursor ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor ?? null);
        setValuesTotal(page.total);
        setValuesLoaded(true);
      } catch (err) {
        if (callGen !== loadGenRef.current) return;
        setValuesError(err instanceof ApiError ? err.title : "Failed to load values");
      } finally {
        if (callGen === loadGenRef.current) setValuesLoading(false);
      }
    },
    [currentProjectId, recordingId],
  );

  // Initial values load when switching to the Values tab
  useEffect(() => {
    if (activeTab === "values" && !valuesLoaded && recording && recording.valueCount > 0) {
      void loadValues();
    }
  }, [activeTab, valuesLoaded, recording, loadValues]);

  // Serialized filter key (uses committedSearch, not live search, for the search field)
  const effectiveSearch = committedSearch;
  const filtersKey = `${[...filters.qualities].sort().join(",")}|${effectiveSearch}|${filters.from}|${filters.to}`;
  const prevFiltersKeyRef = useRef<string | null>(null);

  // When filters change after the initial load — reset and re-fetch.
  // Also handles the case where filters changed while the initial load was in-flight
  // (pendingFilterRefreshRef was set by the filter-change handlers below).
  useEffect(() => {
    if (!valuesLoaded) {
      // Record that a filter change arrived before the initial load finished.
      // The effect will re-run once valuesLoaded becomes true and will re-fetch.
      // prevFiltersKeyRef.current === null means the effect hasn't initialized yet;
      // any change while loading means the default-filter initial fetch will be stale.
      const defaultFiltersKey = `${ALL_QUALITIES.slice().sort().join(",")}|||`;
      if (filtersKey !== defaultFiltersKey) {
        pendingFilterRefreshRef.current = true;
      }
      return;
    }

    const isFirstRun = prevFiltersKeyRef.current === null;
    const keyChanged = prevFiltersKeyRef.current !== filtersKey;
    const hadPendingRefresh = pendingFilterRefreshRef.current;
    prevFiltersKeyRef.current = filtersKey;
    pendingFilterRefreshRef.current = false;

    // Skip if nothing changed AND there's no pending refresh from pre-load filter change
    if (!keyChanged && !hadPendingRefresh) return;

    // Empty quality selection → show empty state, no API call
    if (filters.qualities.size === 0) {
      if (!isFirstRun || hadPendingRefresh) {
        setValues([]);
        setNextCursor(null);
        setValuesTotal(0);
      }
      return;
    }

    // On first run without a pending refresh: the initial fetch already used the default
    // filters (all qualities), so no re-fetch is needed.
    if (isFirstRun && !hadPendingRefresh) return;

    const gen = ++loadGenRef.current;
    setValues([]);
    setNextCursor(null);
    setValuesTotal(null);
    void loadValues(undefined, gen);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtersKey, valuesLoaded]);

  function tabClass(tab: TabId) {
    return `border-b-2 px-4 py-2 text-sm font-medium transition ${
      activeTab === tab
        ? "border-shell-accent text-shell-ink"
        : "border-transparent text-shell-muted hover:text-shell-ink"
    }`;
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
    if (schemaNodes === null && !schemaError) {
      return <SharedStatePanel message="Loading schema…" state="loading" title="" />;
    }
    if (schemaError) {
      return <SharedStatePanel message={schemaError} state="error" title="Failed to load schema." />;
    }
    if (schemaNodes === null || schemaNodes.length === 0) {
      return (
        <SharedStatePanel
          message="No schema data was captured."
          state="empty"
          title="No schema captured."
        />
      );
    }

    const rootNodes = schemaNodes.filter((n) => n.parentId === null);

    function renderNode(node: SchemaNode, depth: number): React.ReactNode {
      const isFolder = node.kind === "FOLDER";
      const isCollapsed = collapsed.has(node.nodeId);
      const children = schemaNodes!.filter((n) => n.parentId === node.nodeId);
      return (
        <div key={node.nodeId} style={{ paddingLeft: `${depth * 16}px` }}>
          <div className="flex items-center gap-1 py-0.5">
            {isFolder && (
              <button
                type="button"
                onClick={() =>
                  setCollapsed((prev) => {
                    const next = new Set(prev);
                    if (next.has(node.nodeId)) next.delete(node.nodeId);
                    else next.add(node.nodeId);
                    return next;
                  })
                }
                className="text-xs text-shell-muted"
              >
                {isCollapsed ? "▶" : "▼"}
              </button>
            )}
            <span
              className={`text-sm ${isFolder ? "font-medium text-shell-ink" : "text-shell-ink"}`}
            >
              {node.name}
            </span>
            {node.dataType && (
              <span className="ml-2 text-xs text-shell-muted">{node.dataType}</span>
            )}
          </div>
          {isFolder && !isCollapsed && children.map((child) => renderNode(child, depth + 1))}
        </div>
      );
    }

    return (
      <div className="rounded-md border border-shell-line bg-white p-4">
        {rootNodes.map((node) => renderNode(node, 0))}
      </div>
    );
  }

  function renderFilterPanel() {
    return (
      <div className="flex flex-wrap items-end gap-4 rounded-md border border-shell-line bg-shell-base/40 px-4 py-3 mb-4">
        {/* Quality filter */}
        <fieldset className="flex flex-col gap-1">
          <legend className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted mb-1">
            Quality
          </legend>
          <div className="flex gap-3">
            {ALL_QUALITIES.map((q) => (
              <label key={q} className="flex items-center gap-1.5 cursor-pointer select-none text-sm text-shell-ink">
                <input
                  type="checkbox"
                  checked={filters.qualities.has(q)}
                  aria-label={q}
                  onChange={(e) => {
                    setFilters((prev) => {
                      const next = new Set(prev.qualities);
                      if (e.target.checked) next.add(q);
                      else next.delete(q);
                      return { ...prev, qualities: next };
                    });
                  }}
                />
                {q}
              </label>
            ))}
          </div>
        </fieldset>

        {/* Search filter */}
        <div className="flex flex-col gap-1 min-w-[180px]">
          <label
            htmlFor="value-search"
            className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted"
          >
            Search
          </label>
          <input
            id="value-search"
            type="text"
            placeholder="Filter by parameter…"
            value={filters.search}
            className="shell-input text-sm"
            onChange={(e) => {
              const val = e.target.value;
              setFilters((prev) => ({ ...prev, search: val }));
              if (searchDebounceRef.current !== null) clearTimeout(searchDebounceRef.current);
              searchDebounceRef.current = setTimeout(() => {
                setCommittedSearch(val);
              }, 300);
            }}
          />
        </div>

        {/* Time range */}
        <div className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            From
          </span>
          <input
            type="datetime-local"
            aria-label="From"
            value={filters.from}
            className="shell-input text-sm"
            onChange={(e) => {
              setFilters((prev) => ({ ...prev, from: e.target.value }));
            }}
          />
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            To
          </span>
          <input
            type="datetime-local"
            aria-label="To"
            value={filters.to}
            className="shell-input text-sm"
            onChange={(e) => {
              setFilters((prev) => ({ ...prev, to: e.target.value }));
            }}
          />
        </div>
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

    // Empty quality selection → show empty state without making an API call
    if (filters.qualities.size === 0) {
      return (
        <>
          {renderFilterPanel()}
          <SharedStatePanel
            message="Select at least one quality filter to see values."
            state="empty"
            title="No quality selected."
          />
        </>
      );
    }

    if (valuesError && values.length === 0) {
      return (
        <>
          {renderFilterPanel()}
          <SharedStatePanel message={valuesError} state="error" title="Failed to load values." />
        </>
      );
    }

    if (valuesLoading && values.length === 0) {
      return (
        <>
          {renderFilterPanel()}
          <SharedStatePanel message="Loading values…" state="loading" title="" />
        </>
      );
    }

    return (
      <div className="space-y-4">
        {renderFilterPanel()}

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

        {nextCursor ? (
          <div className="flex flex-col items-center gap-2">
            <button
              className="shell-action"
              disabled={valuesLoading}
              type="button"
              onClick={() => {
                setValuesError(null);
                void loadValues(nextCursor);
              }}
            >
              {valuesLoading ? "Loading…" : "Load more"}
            </button>
            {valuesError ? <p className="text-xs text-red-600">{valuesError}</p> : null}
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
              {recording.name ? (
                <>
                  {recording.name}
                  <span className="ml-2 font-mono text-base text-shell-muted font-normal">
                    {recording.id.slice(0, 8)}
                  </span>
                </>
              ) : (
                <>
                  Recording{" "}
                  <span className="font-mono text-lg text-shell-muted">{recording.id.slice(0, 8)}</span>
                </>
              )}
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
          {activeTab === "schema" ? renderSchemaTab() : renderValuesTab()}
        </div>
      </section>
    </div>
  );
}
