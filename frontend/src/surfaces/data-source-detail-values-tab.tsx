import { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api";
import { useLiveValues } from "../shell/use-live-values";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import {
  OperationalTable,
  TableToolbar,
  type ActiveTableFilter,
  type TableColumn,
  type TableFilterControl,
  type TableSortState,
} from "../ui/table-pattern";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import type { DataSourceRow } from "../shell/data-sources-store";
import type { SourceValueRow } from "./mock-source-values";

function freshnessTone(freshness: SourceValueRow["freshness"]) {
  return freshness === "Live" ? "accent" : "warning";
}

/** Map a protocol-neutral schema data type (FLOAT64, INT32, BOOL, …) to the tab's type category. */
function neutralToUiType(dataType: string | null): SourceValueRow["dataType"] {
  if (dataType == null) return "string";
  if (dataType.startsWith("FLOAT")) return "float";
  if (dataType.startsWith("INT") || dataType.startsWith("UINT")) return "int";
  if (dataType === "BOOL") return "bool";
  return "string";
}

function currentModeLabel(source: DataSourceRow) {
  return source.status === "Active" ? "Run" : "Off";
}

export function DataSourceDetailValuesTab({
  source,
}: {
  source: DataSourceRow;
}) {
  const isLive = source.status === "Active";
  const { rows: liveRows, status: liveStatus } = useLiveValues(source.id, isLive);
  const pushNotification = useNotificationStore((state) => state.push);
  const projectId = useShellStore((state) => state.currentProjectId);

  // The live SSE stream carries only nodeId + value (no data type / name / unit), so join
  // against the source schema to show the real type, display name, and unit. Without this the
  // rows fall back to the "string" placeholder in use-live-values (UI-098 TODO).
  const [schemaMeta, setSchemaMeta] = useState<
    Record<string, { dataType: SourceValueRow["dataType"]; name: string; path: string; unit: string | null }>
  >({});

  useEffect(() => {
    if (!projectId || !source.id) return;
    let cancelled = false;
    void (async () => {
      try {
        const schema = await apiFetch<{
          nodes: {
            nodeId: string;
            name: string;
            path: string;
            kind: string;
            dataType: string | null;
            unit: string | null;
          }[];
        }>(`/api/v1/projects/${projectId}/data-sources/${source.id}/schema`);
        if (cancelled) return;
        const map: Record<
          string,
          { dataType: SourceValueRow["dataType"]; name: string; path: string; unit: string | null }
        > = {};
        for (const n of schema.nodes) {
          if (n.kind === "VARIABLE") {
            map[n.nodeId] = {
              dataType: neutralToUiType(n.dataType),
              name: n.name,
              path: n.path,
              unit: n.unit,
            };
          }
        }
        setSchemaMeta(map);
      } catch {
        if (!cancelled) setSchemaMeta({});
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId, source.id]);

  useEffect(() => {
    if (!isLive) return;
    if (liveStatus === "reconnecting") {
      pushNotification({
        tone: "reconnecting",
        title: "Reconnecting to live values…",
        message: `Lost the live stream for ${source.name}. Retrying automatically.`,
      });
    } else if (liveStatus === "stale") {
      pushNotification({
        tone: "stale",
        title: "Live values may be out of date.",
        message: `No updates received from ${source.name} recently.`,
      });
    }
  }, [isLive, liveStatus, pushNotification, source.name]);

  const [searchValue, setSearchValue] = useState("");
  const [freshnessFilter, setFreshnessFilter] = useState("all");
  const [pinFilter, setPinFilter] = useState("all");
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "path",
    direction: "asc",
  });

  // Enrich each live row with its schema metadata (join on nodeId, which the row id encodes
  // as `${sourceId}:${nodeId}`). Falls back to the raw stream values when no schema is loaded.
  const values = useMemo(
    () =>
      liveRows.map((row) => {
        const nodeId = row.id.slice(row.sourceId.length + 1);
        const meta = schemaMeta[nodeId];
        if (!meta) return row;
        return {
          ...row,
          path: meta.path || meta.name || row.path,
          dataType: meta.dataType,
          currentValue: meta.unit ? `${row.currentValue} ${meta.unit}` : row.currentValue,
        };
      }),
    [liveRows, schemaMeta],
  );

  const filteredRows = useMemo(() => {
    return values.filter((row) => {
      const normalizedSearch = searchValue.trim().toLowerCase();
      const searchMatches =
        normalizedSearch.length === 0 ||
        [row.path, row.dataType, row.currentValue, row.updatedAt]
          .join(" ")
          .toLowerCase()
          .includes(normalizedSearch);

      const freshnessMatches =
        freshnessFilter === "all" || row.freshness === freshnessFilter;
      const pinMatches =
        pinFilter === "all" || (pinFilter === "pinned" ? row.pinned : !row.pinned);

      return searchMatches && freshnessMatches && pinMatches;
    });
  }, [freshnessFilter, pinFilter, searchValue, values]);

  const filters: TableFilterControl[] = [
    {
      id: "freshness",
      label: "State",
      value: freshnessFilter,
      onChange: setFreshnessFilter,
      options: [
        { label: "All rows", value: "all" },
        { label: "Live", value: "Live" },
        { label: "No updates", value: "No updates" },
      ],
    },
    {
      id: "pin",
      label: "Pinned",
      value: pinFilter,
      onChange: setPinFilter,
      options: [
        { label: "All rows", value: "all" },
        { label: "Pinned only", value: "pinned" },
        { label: "Unpinned only", value: "unpinned" },
      ],
    },
  ];

  const activeFilters: ActiveTableFilter[] = [
    ...(searchValue.trim()
      ? [
          {
            id: "search",
            label: "Search",
            value: searchValue.trim(),
            onClear: () => setSearchValue(""),
          },
        ]
      : []),
    ...(freshnessFilter !== "all"
      ? [
          {
            id: "freshness",
            label: "State",
            value: freshnessFilter,
            onClear: () => setFreshnessFilter("all"),
          },
        ]
      : []),
    ...(pinFilter !== "all"
      ? [
          {
            id: "pin",
            label: "Pinned",
            value: pinFilter === "pinned" ? "Pinned only" : "Unpinned only",
            onClear: () => setPinFilter("all"),
          },
        ]
      : []),
  ];

  const columns: TableColumn<SourceValueRow>[] = [
    {
      id: "path",
      header: "Parameter",
      sortable: true,
      sortValue: (row) => row.path,
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm font-medium text-shell-ink">{row.path}</p>
          {row.pinned ? (
            <div className="mt-2">
              <StatusBadge label="Pinned" tone="accent" />
            </div>
          ) : null}
        </div>
      ),
    },
    {
      id: "type",
      header: "Type",
      sortable: true,
      sortValue: (row) => row.dataType,
      cell: (row) => <span className="text-sm text-shell-ink">{row.dataType}</span>,
      className: "w-[7rem]",
    },
    {
      id: "value",
      header: "Current value",
      sortable: true,
      sortValue: (row) => row.currentValue,
      cell: (row) => <span className="text-sm text-shell-ink">{row.currentValue}</span>,
      className: "w-[10rem]",
    },
    {
      id: "updatedAt",
      header: "Updated",
      sortable: true,
      sortValue: (row) => row.updatedAt,
      cell: (row) => <span className="text-sm text-shell-ink">{row.updatedAt}</span>,
      className: "w-[8rem]",
    },
    {
      id: "freshness",
      header: "State",
      sortable: true,
      sortValue: (row) => row.freshness,
      cell: (row) => (
        <StatusBadge label={row.freshness} tone={freshnessTone(row.freshness)} />
      ),
      className: "w-[8rem]",
    },
  ];

  const hasQueryState =
    searchValue.trim().length > 0 ||
    freshnessFilter !== "all" ||
    pinFilter !== "all";

  if (!isLive) {
    return (
      <div className="space-y-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <p className="text-sm font-medium text-shell-ink">Current values</p>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Parameter definitions live in Schema. Values shows live readings while
              the source is running.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label="Off" tone="neutral" />
            <StatusBadge
              label={`${source.parameterCount.toLocaleString()} parameters`}
              tone="neutral"
            />
          </div>
        </div>
        <SharedStatePanel
          message="Start the source from the Overview tab or the source list to see live parameter readings here."
          state="empty"
          title="Source is not running."
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 max-w-3xl">
          <p className="text-sm font-medium text-shell-ink">Current values</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Parameter definitions live in Schema. Values shows live readings
            from those parameters while the source is running.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge label="Run" tone="accent" />
          <StatusBadge
            label={`${source.parameterCount.toLocaleString()} parameters`}
            tone="neutral"
          />
        </div>
      </div>

      <TableToolbar
        activeFilters={activeFilters}
        filters={filters}
        onClearAll={() => {
          setSearchValue("");
          setFreshnessFilter("all");
          setPinFilter("all");
        }}
        onSearchChange={setSearchValue}
        searchPlaceholder="Search by parameter, type, value, or timestamp"
        searchValue={searchValue}
      />

      <OperationalTable
        columns={columns}
        emptyMessage="No live values have arrived yet. The source is running — values appear as parameters publish updates."
        emptyTitle="Waiting for live values."
        hasQueryState={hasQueryState}
        noResultsMessage="Try a different search term or clear one of the active filters."
        noResultsTitle="No runtime values match the current filters."
        onSortChange={setSortState}
        rowActions={() => []}
        rowKey={(row) => row.id}
        rows={filteredRows}
        sortState={sortState}
      />
    </div>
  );
}
