import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { type DataSourceRow } from "./mock-data-sources";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { stopActionCopy } from "./source-action-copy";
import {
  OperationalTable,
  TableToolbar,
  type ActiveTableFilter,
  type TableColumn,
  type TableFilterControl,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";

type ConfirmationRequest =
  | {
      action: "stop";
      rowId: string;
    }
  | {
      action: "delete";
      rowId: string;
    }
  | null;

function healthTone(health: DataSourceRow["health"]) {
  if (health === "Error") {
    return "danger";
  }

  if (health === "Warning") {
    return "warning";
  }

  return "accent";
}

function stateMeta(row: DataSourceRow) {
  if (row.status === "Active") {
    return { key: "run", label: "Run", tone: "accent" as const };
  }

  return { key: "off", label: "Off", tone: "neutral" as const };
}

function stateFilterLabel(value: string) {
  if (value === "run") {
    return "Run";
  }

  if (value === "off") {
    return "Off";
  }

  return value;
}

export function DataSourcesListPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const rows = useDataSourcesStore((state) => state.dataSources);
  const isLoading = useDataSourcesStore((state) => state.isLoading);
  const error = useDataSourcesStore((state) => state.error);
  const loadDataSources = useDataSourcesStore((state) => state.loadDataSources);
  const stopDataSource = useDataSourcesStore((state) => state.stopDataSource);
  const duplicateDataSource = useDataSourcesStore((state) => state.duplicateDataSource);
  const deleteDataSource = useDataSourcesStore((state) => state.deleteDataSource);
  const push = useNotificationStore((state) => state.push);

  useEffect(() => {
    if (currentProjectId) {
      loadDataSources(currentProjectId);
    }
  }, [currentProjectId, loadDataSources]);
  const [searchValue, setSearchValue] = useState("");
  const [protocolFilter, setProtocolFilter] = useState("all");
  const [stateFilter, setStateFilter] = useState("all");
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "name",
    direction: "asc",
  });
  const [confirmationRequest, setConfirmationRequest] =
    useState<ConfirmationRequest>(null);
  const access = resolveAccess(accessMode, sharedRole);

  const filteredRows = useMemo(() => {
    return rows.filter((row) => {
      const searchMatches =
        searchValue.trim().length === 0 ||
        [row.name, row.protocol, row.endpoint]
          .join(" ")
          .toLowerCase()
          .includes(searchValue.trim().toLowerCase());

      const protocolMatches = protocolFilter === "all" || row.protocol === protocolFilter;
      const stateMatches = stateFilter === "all" || stateMeta(row).key === stateFilter;

      return searchMatches && protocolMatches && stateMatches;
    });
  }, [protocolFilter, rows, searchValue, stateFilter]);

  const confirmationModel = useMemo(() => {
    if (!confirmationRequest) {
      return null;
    }

    const row = rows.find((item) => item.id === confirmationRequest.rowId);
    if (!row) {
      return null;
    }

    if (confirmationRequest.action === "stop") {
      const copy = stopActionCopy;

      return {
        confirmLabel: copy.confirmLabel,
        impacts: [
          { label: "Endpoint", value: row.endpoint },
          { label: "Runtime impact", value: "The source stops serving values until someone starts it again." },
        ],
        message: copy.message,
        objectLabel: `${row.name} (${row.protocol})`,
        reversibilityLabel:
          "This action is reversible. The source can be started again later.",
        title: copy.title,
        tone: "warning" as const,
      };
    }

    const sharedImpact =
      row.status === "Active"
        ? "This source is currently active. Deleting it removes the active runtime entry from the project."
        : "Deleting this source removes its saved simulator setup from the project.";

    return {
      confirmLabel: "Delete source",
      impacts: [
        { label: "Endpoint", value: row.endpoint },
        { label: "Shared impact", value: sharedImpact },
      ],
      message:
        "Deleting a source removes it from the project and breaks direct access to its saved simulator configuration.",
      objectLabel: `${row.name} (${row.protocol})`,
      reversibilityLabel:
        "This action is not reversible. The source must be created again if removed.",
      title: "Delete this source?",
      tone: "danger" as const,
    };
  }, [confirmationRequest, rows]);

  async function duplicateSource(rowId: string) {
    try {
      await duplicateDataSource(rowId, currentProjectId);
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to duplicate source";
      push({ tone: "error", title });
    }
  }

  async function confirmRequestedAction() {
    if (!confirmationRequest) {
      return;
    }

    try {
      if (confirmationRequest.action === "stop") {
        await stopDataSource(confirmationRequest.rowId, currentProjectId);
      }

      if (confirmationRequest.action === "delete") {
        await deleteDataSource(confirmationRequest.rowId, currentProjectId);
      }
    } catch (err) {
      const title = err instanceof Error ? err.message : "Action failed";
      push({ tone: "error", title });
    }

    setConfirmationRequest(null);
  }

  const filters: TableFilterControl[] = [
    {
      id: "protocol",
      label: "Protocol",
      value: protocolFilter,
      onChange: setProtocolFilter,
      options: [
        { label: "All protocols", value: "all" },
        { label: "OPC UA", value: "OPC UA" },
        { label: "Modbus TCP", value: "Modbus TCP" },
      ],
    },
    {
      id: "state",
      label: "State",
      value: stateFilter,
      onChange: setStateFilter,
      options: [
        { label: "All states", value: "all" },
        { label: "Run", value: "run" },
        { label: "Off", value: "off" },
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
    ...(protocolFilter !== "all"
      ? [
          {
            id: "protocol",
            label: "Protocol",
            value: protocolFilter,
            onClear: () => setProtocolFilter("all"),
          },
        ]
      : []),
    ...(stateFilter !== "all"
      ? [
          {
            id: "state",
            label: "State",
            value: stateFilterLabel(stateFilter),
            onClear: () => setStateFilter("all"),
          },
        ]
      : []),
  ];

  const columns: TableColumn<DataSourceRow>[] = [
    {
      id: "name",
      header: "Source",
      sortable: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm font-medium text-shell-ink">{row.name}</p>
          <p className="mt-1 text-sm text-shell-muted">{row.endpoint}</p>
          <p className="mt-1 text-xs text-shell-muted">
            {row.protocol} · {row.parameterCount.toLocaleString()} parameters
          </p>
        </div>
      ),
    },
    {
      id: "state",
      header: "State",
      sortable: true,
      sortValue: (row) => stateMeta(row).label,
      cell: (row) => (
        <StatusBadge label={stateMeta(row).label} tone={stateMeta(row).tone} />
      ),
      className: "w-[8rem]",
    },
    {
      id: "health",
      header: "Health",
      sortable: true,
      sortValue: (row) => row.health,
      cell: (row) => (
        <div className="min-w-0">
          <StatusBadge label={row.health} tone={healthTone(row.health)} />
          {row.health === "Error" ? (
            <p className="mt-1 text-xs text-shell-muted">See Events tab</p>
          ) : null}
        </div>
      ),
      className: "w-[9rem]",
    },
  ];

  const hasQueryState =
    searchValue.trim().length > 0 || protocolFilter !== "all" || stateFilter !== "all";

  if (isLoading) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Loading data sources for this project."
            state="loading"
            title="Loading sources…"
          />
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            actionLabel="Retry"
            message="Check your connection and try again. If the problem continues, contact your project admin."
            onAction={() => {
              if (currentProjectId) loadDataSources(currentProjectId);
            }}
            state="error"
            title="Data sources could not be loaded."
          />
        </section>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <TableToolbar
          activeFilters={activeFilters}
          filters={filters}
          onClearAll={() => {
            setSearchValue("");
            setProtocolFilter("all");
            setStateFilter("all");
          }}
          onSearchChange={setSearchValue}
          onPrimaryAction={
            access.canCreateSource ? () => navigate("/data-sources/new") : undefined
          }
          primaryActionLabel={access.canCreateSource ? "Create source" : undefined}
          searchPlaceholder="Search by source, protocol, or endpoint"
          searchValue={searchValue}
        />

        <div className="mt-4">
          <OperationalTable
            columns={columns}
            emptyMessage="Add the first data source to start building simulator flows."
            emptyTitle="No data sources exist yet."
            hasQueryState={hasQueryState}
            noResultsMessage="Try a different search term or clear one of the active filters."
            noResultsTitle="No sources match the current filters."
            onSortChange={setSortState}
            rowActions={(row) => {
              const actions: TableRowAction<DataSourceRow>[] = [
                {
                  label: "Open",
                  onClick: () => navigate(`/data-sources/${row.id}`),
                },
              ];

              if (access.canRecordSource) {
                actions.push({
                  label: "Record",
                  onClick: () => navigate(`/data-sources/${row.id}/record`),
                });
              }

              if (access.canConfigureReplay) {
                actions.push({
                  label: "Simulate",
                  onClick: () => navigate(`/data-sources/${row.id}/replay`),
                });
              }

              if (row.status === "Active" && access.canStopSource) {
                actions.push({
                  label: "Stop source",
                  onClick: () =>
                    setConfirmationRequest({ action: "stop", rowId: row.id }),
                });
              }

              if (access.canDuplicateSource) {
                actions.push({
                  label: "Duplicate",
                  onClick: () => duplicateSource(row.id),
                });
              }

              if (access.canDeleteSource) {
                actions.push({
                  label: "Delete",
                  onClick: () =>
                    setConfirmationRequest({ action: "delete", rowId: row.id }),
                  tone: "danger" as const,
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

      {confirmationModel ? (
        <ConfirmationDialog
          confirmLabel={confirmationModel.confirmLabel}
          impacts={confirmationModel.impacts}
          message={confirmationModel.message}
          objectLabel={confirmationModel.objectLabel}
          open={Boolean(confirmationModel)}
          reversibilityLabel={confirmationModel.reversibilityLabel}
          title={confirmationModel.title}
          tone={confirmationModel.tone}
          onClose={() => setConfirmationRequest(null)}
          onConfirm={confirmRequestedAction}
        />
      ) : null}
    </div>
  );
}
