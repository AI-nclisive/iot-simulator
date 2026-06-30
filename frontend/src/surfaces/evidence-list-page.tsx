import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch } from "../api/client";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import {
  OperationalTable,
  type ActiveTableFilter,
  type TableColumn,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import {
  canExportEvidenceItem,
  filterEvidenceItems,
} from "./evidence-list-filter";
import {
  evidenceExportStateLabel,
  evidenceKindLabel,
  evidenceStatusLabel,
  evidenceTitle,
  mapEvidenceDto,
  type EvidenceExportStateLabel,
  type EvidenceItem,
  type EvidenceListResponse,
  type EvidenceStatusLabel,
} from "./evidence-types";

function statusTone(status: EvidenceStatusLabel): StatusTone {
  if (status === "Export failed") return "danger";
  if (status === "In progress" || status === "Incomplete") return "warning";
  if (status === "Ready" || status === "Exported") return "accent";
  return "neutral";
}

function exportTone(exportState: EvidenceExportStateLabel): StatusTone {
  if (exportState === "Export failed") return "danger";
  if (exportState === "Not ready") return "warning";
  if (exportState === "Exported") return "accent";
  return "neutral";
}

function uniqueOptions(values: string[]) {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right));
}

function filterValueLabel(value: string) {
  return value === "all" ? "All" : value;
}

function FilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { label: string; value: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="flex min-w-[10rem] flex-col gap-2 text-sm text-shell-muted">
      {label}
      <select
        className="shell-field"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function EvidenceListPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  const [items, setItems] = useState<EvidenceItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [searchValue, setSearchValue] = useState("");
  const [initiatorFilter, setInitiatorFilter] = useState("all");
  const [stateFilter, setStateFilter] = useState("all");
  const [scenarioFilter, setScenarioFilter] = useState("all");
  const [advancedFiltersOpen, setAdvancedFiltersOpen] = useState(false);
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "startedAt",
    direction: "desc",
  });

  useEffect(() => {
    if (!currentProjectId) {
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setFetchError(null);

    apiFetch<EvidenceListResponse>(
      `/api/v1/projects/${currentProjectId}/evidence?limit=50`,
    )
      .then((res) => {
        if (cancelled) return;
        setItems(res.items.map(mapEvidenceDto));
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setFetchError(err instanceof Error ? err.message : "Failed to load evidence");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [currentProjectId]);

  const initiatorOptions = [
    { label: "All initiators", value: "all" },
    ...uniqueOptions(items.map((item) => item.initiator)).map((initiator) => ({
      label: initiator,
      value: initiator,
    })),
  ];
  const stateOptions = [
    { label: "All states", value: "all" },
    { label: "In progress", value: "In progress" },
    { label: "Ready", value: "Ready" },
    { label: "Incomplete", value: "Incomplete" },
    { label: "Export failed", value: "Export failed" },
  ];
  const workTypeOptions = [
    { label: "All work types", value: "all" },
    { label: "Scenario evidence", value: "scenario" },
    { label: "Source evidence", value: "source" },
  ];

  const filteredRows = useMemo(() => {
    return filterEvidenceItems(items, {
      initiatorFilter,
      scenarioFilter,
      searchValue,
      stateFilter,
    });
  }, [initiatorFilter, items, scenarioFilter, searchValue, stateFilter]);

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
    ...(initiatorFilter !== "all"
      ? [
          {
            id: "initiator",
            label: "Initiator",
            value: filterValueLabel(initiatorFilter),
            onClear: () => setInitiatorFilter("all"),
          },
        ]
      : []),
    ...(stateFilter !== "all"
      ? [
          {
            id: "state",
            label: "State",
            value: filterValueLabel(stateFilter),
            onClear: () => setStateFilter("all"),
          },
        ]
      : []),
    ...(scenarioFilter !== "all"
      ? [
          {
            id: "workType",
            label: "Work type",
            value: scenarioFilter === "scenario" ? "Scenario evidence" : "Source evidence",
            onClear: () => setScenarioFilter("all"),
          },
        ]
      : []),
  ];

  const columns: TableColumn<EvidenceItem>[] = [
    {
      id: "title",
      header: "Evidence",
      sortable: true,
      sortValue: (row) => row.startedAt,
      cell: (row) => {
        const statusLbl = evidenceStatusLabel(row.status);
        const exportStateLbl = evidenceExportStateLabel(row.exported, row.status);
        const title = evidenceTitle(row.kind, row.runId);
        return (
          <div className="min-w-0">
            <p className="text-sm font-medium text-shell-ink">{title}</p>
            <p className="mt-1 text-sm text-shell-muted">
              {evidenceKindLabel(row.kind)} · {row.runId}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <StatusBadge label={statusLbl} tone={statusTone(statusLbl)} />
              {exportStateLbl !== "Not exported" && exportStateLbl !== statusLbl ? (
                <StatusBadge label={exportStateLbl} tone={exportTone(exportStateLbl)} />
              ) : null}
            </div>
          </div>
        );
      },
    },
    {
      id: "initiator",
      header: "Initiator",
      sortable: true,
      sortValue: (row) => row.initiator,
      cell: (row) => <span className="text-sm text-shell-ink">{row.initiator}</span>,
      className: "w-[9rem]",
    },
    {
      id: "startedAt",
      header: "Started",
      sortable: true,
      sortValue: (row) => row.startedAt,
      cell: (row) => <span className="text-sm text-shell-ink">{row.startedAt}</span>,
      className: "w-[14rem]",
    },
    {
      id: "sources",
      header: "Sources",
      sortable: false,
      cell: (row) => (
        <span className="text-sm text-shell-ink">
          {row.sourceIds.length > 0
            ? row.sourceIds.length === 1
              ? row.sourceIds[0]
              : `${row.sourceIds.length} sources`
            : "—"}
        </span>
      ),
      className: "w-[11rem]",
    },
  ];

  const hasQueryState =
    searchValue.trim().length > 0 ||
    initiatorFilter !== "all" ||
    stateFilter !== "all" ||
    scenarioFilter !== "all";
  const advancedFilterCount = [initiatorFilter].filter((value) => value !== "all").length;

  function clearAllFilters() {
    setSearchValue("");
    setInitiatorFilter("all");
    setStateFilter("all");
    setScenarioFilter("all");
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <h2 className="text-2xl font-semibold text-shell-ink">Evidence</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              Find captured evidence by run, initiator, and state.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {!isLoading && !fetchError ? (
              <StatusBadge label={`${items.length} artifacts`} />
            ) : null}
            <StatusBadge label={access.isAdmin ? "Export allowed" : "Inspect only"} />
          </div>
        </div>

        {access.isSharedUser ? (
          <div className="mt-5 rounded-md border border-shell-line bg-white px-4 py-4">
            <p className="text-sm font-medium text-shell-ink">Shared user access</p>
            <p className="mt-2 text-sm leading-6 text-shell-muted">
              You can inspect evidence and follow origin links. Export actions are reserved
              for Admin users in shared mode.
            </p>
          </div>
        ) : null}
      </section>

      <section className="shell-panel px-5 py-5">
        {isLoading ? (
          <SharedStatePanel
            message="Loading evidence artifacts from the API."
            state="loading"
            title="Loading evidence…"
          />
        ) : fetchError ? (
          <SharedStatePanel
            message={fetchError}
            state="error"
            title="Failed to load evidence."
          />
        ) : (
          <>
            <div className="space-y-4">
              <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                <div className="grid min-w-0 flex-1 gap-3 md:grid-cols-[minmax(18rem,1fr)_minmax(10rem,12rem)_minmax(12rem,14rem)]">
                  <label className="flex min-w-0 flex-col gap-2 text-sm text-shell-muted">
                    Search
                    <input
                      className="shell-field"
                      placeholder="Search run, initiator, source, or state"
                      type="search"
                      value={searchValue}
                      onChange={(event) => setSearchValue(event.target.value)}
                    />
                  </label>

                  <FilterSelect
                    label="State"
                    options={stateOptions}
                    value={stateFilter}
                    onChange={setStateFilter}
                  />
                  <FilterSelect
                    label="Work type"
                    options={workTypeOptions}
                    value={scenarioFilter}
                    onChange={setScenarioFilter}
                  />
                </div>

                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <span className="text-sm text-shell-muted">{filteredRows.length} shown</span>
                  <button
                    className="shell-action"
                    type="button"
                    onClick={() => setAdvancedFiltersOpen((isOpen) => !isOpen)}
                  >
                    {advancedFiltersOpen ? "Hide filters" : "More filters"}
                    {advancedFilterCount > 0 ? ` (${advancedFilterCount})` : ""}
                  </button>
                </div>
              </div>

              {advancedFiltersOpen ? (
                <div className="grid gap-3 rounded-md border border-shell-line bg-white px-4 py-4 md:grid-cols-3">
                  <FilterSelect
                    label="Initiator"
                    options={initiatorOptions}
                    value={initiatorFilter}
                    onChange={setInitiatorFilter}
                  />
                </div>
              ) : null}

              {activeFilters.length > 0 ? (
                <div className="flex flex-wrap items-center gap-2">
                  {activeFilters.map((filter) => (
                    <button
                      key={filter.id}
                      className="shell-filter-chip"
                      type="button"
                      onClick={filter.onClear}
                    >
                      {filter.label}: {filter.value}
                    </button>
                  ))}
                  <button className="shell-text-action" type="button" onClick={clearAllFilters}>
                    Clear all
                  </button>
                </div>
              ) : null}
            </div>

            <div className="mt-5">
              <OperationalTable
                columns={columns}
                emptyMessage="Evidence appears here after recordings, replays, or scenarios produce an artifact."
                emptyTitle="No evidence has been captured yet."
                hasQueryState={hasQueryState}
                noResultsMessage="Try a different initiator, state, or work type filter."
                noResultsTitle="No evidence matches the current filters."
                onSortChange={setSortState}
                rowActions={(row) => {
                  const actions: TableRowAction<EvidenceItem>[] = [
                    {
                      label: "Open",
                      onClick: () => navigate(`/evidence/${row.id}`),
                    },
                  ];

                  if (access.isAdmin && canExportEvidenceItem(row)) {
                    const exportStateLbl = evidenceExportStateLabel(row.exported, row.status);
                    actions.push({
                      label: exportStateLbl === "Export failed" ? "Recover export" : "Export",
                      onClick: () => navigate(`/evidence/${row.id}?export=1`),
                    });
                  }

                  return actions;
                }}
                rowKey={(row) => row.id}
                rows={filteredRows}
                sortState={sortState}
              />
            </div>
          </>
        )}
      </section>
    </div>
  );
}
