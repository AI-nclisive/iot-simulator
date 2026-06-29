import { useMemo, useState } from "react";
import { useSourceValuesStore } from "../shell/source-values-store";
import {
  OperationalTable,
  TableToolbar,
  type ActiveTableFilter,
  type TableColumn,
  type TableFilterControl,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";
import { StatusBadge } from "../ui/status-badge";
import type { DataSourceRow } from "./mock-data-sources";
import type { SourceValueRow } from "./mock-source-values";

function freshnessTone(freshness: SourceValueRow["freshness"]) {
  return freshness === "Live" ? "accent" : "warning";
}

function currentModeLabel(source: DataSourceRow) {
  return source.status === "Active" ? "Run" : "Off";
}

export function DataSourceDetailValuesTab({
  source,
}: {
  source: DataSourceRow;
}) {
  const allValues = useSourceValuesStore((state) => state.values);
  const togglePinnedValue = useSourceValuesStore((state) => state.togglePinnedValue);
  const [searchValue, setSearchValue] = useState("");
  const [freshnessFilter, setFreshnessFilter] = useState("all");
  const [pinFilter, setPinFilter] = useState("all");
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "path",
    direction: "asc",
  });

  const values = useMemo(
    () => allValues.filter((valueRow) => valueRow.sourceId === source.id),
    [allValues, source.id],
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

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 max-w-3xl">
          <p className="text-sm font-medium text-shell-ink">Current values</p>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            Parameter definitions live in Schema. Values shows current readings
            from those parameters; recordings, samples, and evidence stay in
            their own surfaces.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge label={`Mode: ${currentModeLabel(source)}`} tone="neutral" />
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
        emptyMessage="No runtime values are available for this source yet."
        emptyTitle="This source has no visible runtime rows."
        hasQueryState={hasQueryState}
        noResultsMessage="Try a different search term or clear one of the active filters."
        noResultsTitle="No runtime values match the current filters."
        onSortChange={setSortState}
        rowActions={(row) => {
          const actions: TableRowAction<SourceValueRow>[] = [
            {
              label: row.pinned ? "Unpin" : "Pin",
              onClick: () => togglePinnedValue(row.id),
            },
          ];

          return actions;
        }}
        rowKey={(row) => row.id}
        rows={filteredRows}
        sortState={sortState}
      />
    </div>
  );
}
