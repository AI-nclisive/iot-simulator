import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { type DataSourceRow } from "./mock-data-sources";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { StatusBadge } from "../ui/status-badge";
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

function statusTone(status: DataSourceRow["status"]) {
  return status === "Active" ? "accent" : "neutral";
}

export function DataSourcesListPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const rows = useDataSourcesStore((state) => state.dataSources);
  const startDataSource = useDataSourcesStore((state) => state.startDataSource);
  const stopDataSource = useDataSourcesStore((state) => state.stopDataSource);
  const duplicateDataSource = useDataSourcesStore((state) => state.duplicateDataSource);
  const deleteDataSource = useDataSourcesStore((state) => state.deleteDataSource);
  const [searchValue, setSearchValue] = useState("");
  const [protocolFilter, setProtocolFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
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
        [row.name, row.protocol, row.endpoint, row.lastOperator]
          .join(" ")
          .toLowerCase()
          .includes(searchValue.trim().toLowerCase());

      const protocolMatches = protocolFilter === "all" || row.protocol === protocolFilter;
      const statusMatches = statusFilter === "all" || row.status === statusFilter;

      return searchMatches && protocolMatches && statusMatches;
    });
  }, [protocolFilter, rows, searchValue, statusFilter]);

  const confirmationModel = useMemo(() => {
    if (!confirmationRequest) {
      return null;
    }

    const row = rows.find((item) => item.id === confirmationRequest.rowId);
    if (!row) {
      return null;
    }

    if (confirmationRequest.action === "stop") {
      const runtimeImpact =
        row.process === "Recording"
          ? "Recording stops immediately and the current capture ends on this source."
          : row.process === "Replay"
            ? "Replay stops immediately for this source."
            : "The source stops serving simulated values until someone starts it again.";

      return {
        confirmLabel: "Stop source",
        impacts: [
          { label: "Endpoint", value: row.endpoint },
          { label: "Runtime impact", value: runtimeImpact },
          {
            label: "Connected clients",
            value:
              row.clients > 0
                ? `${row.clients} connected client${row.clients === 1 ? "" : "s"} may notice the interruption.`
                : "No connected clients are currently shown for this source.",
          },
        ],
        message:
          "Stopping a source interrupts its current runtime behavior for everyone using this project.",
        objectLabel: `${row.name} (${row.protocol})`,
        reversibilityLabel:
          "This action is reversible. The source can be started again later.",
        title: "Stop this source?",
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
        {
          label: "Connected clients",
          value:
            row.clients > 0
              ? `${row.clients} connected client${row.clients === 1 ? "" : "s"} lose this source when it is removed.`
              : "No connected clients are currently shown for this source.",
        },
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

  function startSource(rowId: string) {
    startDataSource(rowId);
  }

  function duplicateSource(rowId: string) {
    duplicateDataSource(rowId);
  }

  function confirmRequestedAction() {
    if (!confirmationRequest) {
      return;
    }

    if (confirmationRequest.action === "stop") {
      stopDataSource(confirmationRequest.rowId);
    }

    if (confirmationRequest.action === "delete") {
      deleteDataSource(confirmationRequest.rowId);
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
      id: "status",
      label: "Status",
      value: statusFilter,
      onChange: setStatusFilter,
      options: [
        { label: "All statuses", value: "all" },
        { label: "Active", value: "Active" },
        { label: "Stopped", value: "Stopped" },
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
    ...(statusFilter !== "all"
      ? [
          {
            id: "status",
            label: "Status",
            value: statusFilter,
            onClear: () => setStatusFilter("all"),
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
            {row.parameterCount.toLocaleString()} parameters
          </p>
        </div>
      ),
    },
    {
      id: "protocol",
      header: "Protocol",
      sortable: true,
      sortValue: (row) => row.protocol,
      cell: (row) => <span className="text-sm text-shell-ink">{row.protocol}</span>,
      className: "w-[10rem]",
    },
    {
      id: "parameters",
      header: "Parameters",
      sortable: true,
      sortValue: (row) => row.parameterCount,
      cell: (row) => (
        <span className="text-sm text-shell-ink">
          {row.parameterCount.toLocaleString()}
        </span>
      ),
      className: "w-[8rem]",
    },
    {
      id: "status",
      header: "Status",
      sortable: true,
      sortValue: (row) => row.status,
      cell: (row) => <StatusBadge label={row.status} tone={statusTone(row.status)} />,
      className: "w-[9rem]",
    },
    {
      id: "process",
      header: "Process",
      sortable: true,
      sortValue: (row) => row.process ?? "",
      cell: (row) =>
        row.process ? <StatusBadge label={row.process} tone={row.process === "Recording" ? "warning" : "accent"} /> : <span className="text-sm text-shell-muted">-</span>,
      className: "w-[10rem]",
    },
    {
      id: "clients",
      header: "Clients",
      sortable: true,
      sortValue: (row) => row.clients,
      cell: (row) => <span className="text-sm text-shell-ink">{row.clients}</span>,
      className: "w-[7rem]",
    },
    {
      id: "health",
      header: "Health",
      sortable: true,
      sortValue: (row) => row.health,
      cell: (row) => <StatusBadge label={row.health} tone={healthTone(row.health)} />,
      className: "w-[9rem]",
    },
    {
      id: "operator",
      header: "Last operator",
      sortable: true,
      sortValue: (row) => row.lastOperator,
      cell: (row) => <span className="text-sm text-shell-ink">{row.lastOperator}</span>,
      className: "w-[10rem]",
    },
  ];

  const hasQueryState =
    searchValue.trim().length > 0 || protocolFilter !== "all" || statusFilter !== "all";

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <TableToolbar
          activeFilters={activeFilters}
          filters={filters}
          onClearAll={() => {
            setSearchValue("");
            setProtocolFilter("all");
            setStatusFilter("all");
          }}
          onSearchChange={setSearchValue}
          onPrimaryAction={
            access.canCreateSource ? () => navigate("/data-sources/new") : undefined
          }
          primaryActionLabel={access.canCreateSource ? "Create source" : undefined}
          searchPlaceholder="Search by source, protocol, endpoint, or operator"
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

              if (row.status === "Active" && access.canStopSource) {
                actions.push({
                  label: "Stop",
                  onClick: () =>
                    setConfirmationRequest({ action: "stop", rowId: row.id }),
                });
              }

              if (row.status === "Stopped" && access.canStartStoppedSource) {
                actions.push({
                  label: "Start",
                  onClick: () => startSource(row.id),
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
