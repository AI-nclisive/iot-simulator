import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import {
  OperationalTable,
  type ActiveTableFilter,
  type TableColumn,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import { evidenceArtifacts, type EvidenceArtifact, type EvidenceStatus } from "./mock-evidence";

function statusTone(status: EvidenceStatus): StatusTone {
  if (status === "Export failed") {
    return "danger";
  }

  if (status === "Capturing" || status === "Partial") {
    return "warning";
  }

  if (status === "Ready" || status === "Exported") {
    return "accent";
  }

  return "neutral";
}

function exportTone(exportState: EvidenceArtifact["exportState"]): StatusTone {
  if (exportState === "Export failed") {
    return "danger";
  }

  if (exportState === "Not ready") {
    return "warning";
  }

  if (exportState === "Exported") {
    return "accent";
  }

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
  const access = resolveAccess(accessMode, sharedRole);
  const [searchValue, setSearchValue] = useState("");
  const [projectFilter, setProjectFilter] = useState("all");
  const [sourceFilter, setSourceFilter] = useState("all");
  const [initiatorFilter, setInitiatorFilter] = useState("all");
  const [stateFilter, setStateFilter] = useState("all");
  const [scenarioFilter, setScenarioFilter] = useState("all");
  const [advancedFiltersOpen, setAdvancedFiltersOpen] = useState(false);
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "startedAt",
    direction: "desc",
  });

  const projectOptions = [
    { label: "All projects", value: "all" },
    ...uniqueOptions(evidenceArtifacts.map((artifact) => artifact.projectName)).map(
      (projectName) => ({ label: projectName, value: projectName }),
    ),
  ];
  const sourceOptions = [
    { label: "All sources", value: "all" },
    ...uniqueOptions(evidenceArtifacts.map((artifact) => artifact.sourceName)).map(
      (sourceName) => ({ label: sourceName, value: sourceName }),
    ),
  ];
  const initiatorOptions = [
    { label: "All initiators", value: "all" },
    ...uniqueOptions(evidenceArtifacts.map((artifact) => artifact.initiator)).map(
      (initiator) => ({ label: initiator, value: initiator }),
    ),
  ];
  const stateOptions = [
    { label: "All states", value: "all" },
    { label: "Capturing", value: "Capturing" },
    { label: "Ready", value: "Ready" },
    { label: "Partial", value: "Partial" },
    { label: "Exported", value: "Exported" },
    { label: "Export failed", value: "Export failed" },
  ];
  const workTypeOptions = [
    { label: "All work types", value: "all" },
    { label: "Scenario evidence", value: "scenario" },
    { label: "Source evidence", value: "source" },
  ];

  const filteredRows = useMemo(() => {
    const normalizedSearch = searchValue.trim().toLowerCase();

    return evidenceArtifacts.filter((artifact) => {
      const searchMatches =
        normalizedSearch.length === 0 ||
        [
          artifact.title,
          artifact.projectName,
          artifact.sourceName,
          artifact.scenarioName ?? "",
          artifact.runId,
          artifact.runType,
          artifact.initiator,
          artifact.status,
          artifact.exportState,
        ]
          .join(" ")
          .toLowerCase()
          .includes(normalizedSearch);

      const projectMatches =
        projectFilter === "all" || artifact.projectName === projectFilter;
      const sourceMatches = sourceFilter === "all" || artifact.sourceName === sourceFilter;
      const initiatorMatches =
        initiatorFilter === "all" || artifact.initiator === initiatorFilter;
      const stateMatches = stateFilter === "all" || artifact.status === stateFilter;
      const scenarioMatches =
        scenarioFilter === "all" ||
        (scenarioFilter === "scenario" ? artifact.scenarioName : !artifact.scenarioName);

      return (
        searchMatches &&
        projectMatches &&
        sourceMatches &&
        initiatorMatches &&
        stateMatches &&
        scenarioMatches
      );
    });
  }, [initiatorFilter, projectFilter, scenarioFilter, searchValue, sourceFilter, stateFilter]);

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
    ...(projectFilter !== "all"
      ? [
          {
            id: "project",
            label: "Project",
            value: filterValueLabel(projectFilter),
            onClear: () => setProjectFilter("all"),
          },
        ]
      : []),
    ...(sourceFilter !== "all"
      ? [
          {
            id: "source",
            label: "Source",
            value: filterValueLabel(sourceFilter),
            onClear: () => setSourceFilter("all"),
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

  const columns: TableColumn<EvidenceArtifact>[] = [
    {
      id: "title",
      header: "Evidence",
      sortable: true,
      sortValue: (row) => row.title,
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm font-medium text-shell-ink">{row.title}</p>
          <p className="mt-1 text-sm text-shell-muted">
            {row.runType} · {row.runId}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <StatusBadge label={row.status} tone={statusTone(row.status)} />
            <StatusBadge label={row.exportState} tone={exportTone(row.exportState)} />
          </div>
        </div>
      ),
    },
    {
      id: "origin",
      header: "Origin",
      sortable: true,
      sortValue: (row) => `${row.projectName} ${row.sourceName} ${row.scenarioName ?? ""}`,
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm text-shell-ink">{row.projectName}</p>
          <p className="mt-1 text-sm text-shell-muted">{row.scenarioName ?? row.sourceName}</p>
        </div>
      ),
      className: "min-w-[13rem]",
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
      className: "w-[12rem]",
    },
    {
      id: "scope",
      header: "Scope",
      sortable: true,
      sortValue: (row) => row.valueCount,
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm text-shell-ink">{row.valueCount.toLocaleString()} values</p>
          <p className="mt-1 text-sm text-shell-muted">
            {row.clientCount} client{row.clientCount === 1 ? "" : "s"} · {row.sizeLabel}
          </p>
        </div>
      ),
      className: "w-[11rem]",
    },
  ];

  const hasQueryState =
    searchValue.trim().length > 0 ||
    projectFilter !== "all" ||
    sourceFilter !== "all" ||
    initiatorFilter !== "all" ||
    stateFilter !== "all" ||
    scenarioFilter !== "all";
  const advancedFilterCount = [
    projectFilter,
    sourceFilter,
    initiatorFilter,
  ].filter((value) => value !== "all").length;

  function clearAllFilters() {
    setSearchValue("");
    setProjectFilter("all");
    setSourceFilter("all");
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
              Find captured evidence by project, source, scenario, run, initiator, and state.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={`${evidenceArtifacts.length} artifacts`} />
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
        <div className="space-y-4">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
            <div className="grid min-w-0 flex-1 gap-3 md:grid-cols-[minmax(18rem,1fr)_minmax(10rem,12rem)_minmax(12rem,14rem)]">
              <label className="flex min-w-0 flex-col gap-2 text-sm text-shell-muted">
                Search
                <input
                  className="shell-field"
                  placeholder="Search evidence, source, run, initiator, or project"
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
                label="Project"
                options={projectOptions}
                value={projectFilter}
                onChange={setProjectFilter}
              />
              <FilterSelect
                label="Source"
                options={sourceOptions}
                value={sourceFilter}
                onChange={setSourceFilter}
              />
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
            noResultsMessage="Try a different source, initiator, project, scenario, or state filter."
            noResultsTitle="No evidence matches the current filters."
            onSortChange={setSortState}
            rowActions={(row) => {
              const actions: TableRowAction<EvidenceArtifact>[] = [
                {
                  label: "Open",
                  onClick: () => navigate(`/evidence/${row.id}`),
                },
              ];

              if (access.isAdmin && row.status !== "Capturing") {
                actions.push({
                  label: row.exportState === "Export failed" ? "Recover export" : "Export",
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
      </section>
    </div>
  );
}
