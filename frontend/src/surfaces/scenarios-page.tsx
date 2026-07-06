/**
 * scenarios-page.tsx — Scenarios landing surface (UI-060).
 *
 * A landing page for saved scenarios: list with run-state, last-run summary,
 * owner/editor context, and role-aware actions (create/open/duplicate/run/stop).
 * Gives the Scenarios area a clear entry point instead of dropping the user
 * straight into the builder. Runs on the scenarios store (live API from UI-127).
 *
 * Roles (UI-006): User can view + run/stop; Admin can additionally create,
 * open-to-edit, and duplicate. A scenario locked by someone else cannot be
 * run/stopped from here (UI-005 edit-lock convention).
 */

import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useNotificationStore } from "../shell/notification-store";
import { useScenariosStore } from "../shell/scenarios-store";
import type { ScenarioRow, ScenarioRunState } from "../shell/scenarios-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import {
  OperationalTable,
  TableToolbar,
  type ActiveTableFilter,
  type TableColumn,
  type TableFilterControl,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";

function runStateTone(state: ScenarioRunState): StatusTone {
  if (state === "Running") return "accent";
  if (state === "Failed") return "danger";
  if (state === "Stopped") return "warning";
  return "neutral";
}

function formatTimestamp(iso: string | null): string {
  if (!iso) return "Never";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

function lastRunSummary(row: ScenarioRow): string {
  if (!row.lastRun.at) return "Never run";
  const when = formatTimestamp(row.lastRun.at);
  return row.lastRun.outcome ? `${row.lastRun.outcome} · ${when}` : `In progress · ${when}`;
}

export function ScenariosPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  const scenarios = useScenariosStore((state) => state.scenarios);
  const isLoading = useScenariosStore((state) => state.isLoading);
  const error = useScenariosStore((state) => state.error);
  const loadScenarios = useScenariosStore((state) => state.loadScenarios);
  const runScenario = useScenariosStore((state) => state.runScenario);
  const stopScenario = useScenariosStore((state) => state.stopScenario);
  const liveRuns = useScenariosStore((state) => state.liveRuns);
  const duplicateScenario = useScenariosStore((state) => state.duplicateScenario);
  const createScenario = useScenariosStore((state) => state.createScenario);
  const pushNotification = useNotificationStore((state) => state.push);

  const [searchValue, setSearchValue] = useState("");
  const [stateFilter, setStateFilter] = useState("all");
  const [confirmStop, setConfirmStop] = useState<string | null>(null);
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "name",
    direction: "asc",
  });

  // Load on mount when scenarios are empty and not already loading or in error state
  useEffect(() => {
    if (currentProjectId && !isLoading && !error && scenarios.length === 0) {
      void loadScenarios(currentProjectId);
    }
  }, [currentProjectId, isLoading, error, scenarios.length, loadScenarios]);

  const filtered = useMemo(() => {
    const query = searchValue.trim().toLowerCase();
    return scenarios.filter((row) => {
      if (stateFilter !== "all" && row.runState !== stateFilter) return false;
      if (!query) return true;
      return (
        row.name.toLowerCase().includes(query) ||
        row.description.toLowerCase().includes(query) ||
        row.owner.toLowerCase().includes(query)
      );
    });
  }, [scenarios, searchValue, stateFilter]);

  const hasQueryState = searchValue.trim().length > 0 || stateFilter !== "all";

  const filters: TableFilterControl[] = [
    {
      id: "state",
      label: "Run state",
      value: stateFilter,
      onChange: setStateFilter,
      options: [
        { label: "All states", value: "all" },
        { label: "Not running", value: "Not running" },
        { label: "Running", value: "Running" },
        { label: "Stopped", value: "Stopped" },
        { label: "Failed", value: "Failed" },
      ],
    },
  ];

  const activeFilters: ActiveTableFilter[] = [];
  if (stateFilter !== "all") {
    activeFilters.push({
      id: "state",
      label: `Run state: ${stateFilter}`,
      value: stateFilter,
      onClear: () => setStateFilter("all"),
    });
  }

  const columns: TableColumn<ScenarioRow>[] = [
    {
      id: "name",
      header: "Scenario",
      sortable: true,
      sortValue: (row) => row.name.toLowerCase(),
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-medium text-shell-ink">{row.name}</p>
          <p className="truncate text-sm text-shell-muted">{row.description}</p>
        </div>
      ),
    },
    {
      id: "state",
      header: "State",
      sortable: true,
      sortValue: (row) => row.runState,
      cell: (row) => <StatusBadge label={row.runState} tone={runStateTone(row.runState)} />,
    },
    {
      id: "steps",
      header: "Steps",
      sortable: true,
      sortValue: (row) => row.stepCount,
      cell: (row) => <span className="text-sm text-shell-ink">{row.stepCount}</span>,
    },
    {
      id: "lastRun",
      header: "Last run",
      sortable: true,
      sortValue: (row) => row.lastRun.at ?? "",
      cell: (row) => <span className="text-sm text-shell-muted">{lastRunSummary(row)}</span>,
    },
    {
      id: "owner",
      header: "Owner",
      sortable: true,
      sortValue: (row) => row.owner.toLowerCase(),
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-sm text-shell-ink">{row.owner}</p>
          {row.lockedBy ? (
            <p className="text-xs text-shell-warning">Editing: {row.lockedBy}</p>
          ) : null}
        </div>
      ),
    },
  ];

  function handleOpen(row: ScenarioRow) {
    navigate(`/scenarios/${row.id}`);
  }

  async function handleRun(row: ScenarioRow) {
    const runId = await runScenario(currentProjectId, row.id);
    if (!runId) return;
    pushNotification({ tone: "success", title: `Started "${row.name}".` });
    navigate(`/scenarios/${row.id}/run`);
  }

  function handleStopConfirmed(row: ScenarioRow) {
    const runId = liveRuns[row.id]?.runId;
    if (!runId) return;
    void stopScenario(currentProjectId, row.id, runId);
    pushNotification({ tone: "success", title: `Stopped "${row.name}".` });
  }

  async function handleDuplicate(row: ScenarioRow) {
    const newId = await duplicateScenario(currentProjectId, row.id);
    if (newId) {
      pushNotification({ tone: "success", title: `Duplicated "${row.name}".` });
      navigate(`/scenarios/${newId}`);
    }
  }

  function rowActions(row: ScenarioRow): TableRowAction<ScenarioRow>[] {
    const actions: TableRowAction<ScenarioRow>[] = [];
    const lockedByOther = row.lockedBy !== null;

    actions.push({ label: "Open", onClick: () => handleOpen(row) });

    if (row.runState === "Running") {
      actions.push({ label: "View run", onClick: () => navigate(`/scenarios/${row.id}/run`) });
      if (access.canStopScenario && !lockedByOther) {
        actions.push({ label: "Stop", onClick: () => setConfirmStop(row.id) });
      }
    } else if (access.canRunScenario && !lockedByOther) {
      actions.push({ label: "Run", onClick: () => { void handleRun(row); } });
    }

    if (access.canDuplicateScenario) {
      actions.push({ label: "Duplicate", onClick: () => { void handleDuplicate(row); } });
    }

    return actions;
  }

  const confirmTarget = confirmStop
    ? scenarios.find((s) => s.id === confirmStop) ?? null
    : null;

  // Loading state
  if (isLoading && scenarios.length === 0) {
    return (
      <div className="flex h-full items-center justify-center px-4 py-6">
        <p className="text-sm text-shell-muted">Loading scenarios…</p>
      </div>
    );
  }

  // Error state
  if (error && scenarios.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 px-4 py-6">
        <p className="text-sm text-shell-danger">{error}</p>
        <button
          className="shell-action"
          type="button"
          onClick={() => { if (currentProjectId) void loadScenarios(currentProjectId); }}
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-4 px-4 py-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-shell-ink">Scenarios</h1>
          <p className="mt-1 text-sm text-shell-muted">
            Saved scenarios with run state, last run, and owner. Open one to edit, or
            run it directly.
          </p>
        </div>
        {access.canCreateScenario ? (
          <button
            className="shell-action"
            type="button"
            onClick={async () => {
              const id = await createScenario(currentProjectId);
              if (id) navigate(`/scenarios/${id}`);
            }}
          >
            New scenario
          </button>
        ) : null}
      </header>

      {error ? (
        <div className="rounded-md border border-shell-danger/30 bg-shell-danger/5 px-4 py-3 text-sm text-shell-ink">
          {error}
        </div>
      ) : null}

      <TableToolbar
        searchValue={searchValue}
        onSearchChange={setSearchValue}
        searchPlaceholder="Search scenarios"
        filters={filters}
        activeFilters={activeFilters}
        onClearAll={() => {
          setSearchValue("");
          setStateFilter("all");
        }}
      />

      <OperationalTable
        columns={columns}
        rows={filtered}
        rowKey={(row) => row.id}
        rowActions={rowActions}
        sortState={sortState}
        onSortChange={setSortState}
        hasQueryState={hasQueryState}
        emptyTitle="No scenarios yet."
        emptyMessage={
          access.canCreateScenario
            ? "A scenario is an ordered sequence of steps — start a source, replay a recording, inject synthetic data, or trigger a fault — that runs as a single automated flow. Use New scenario to create one."
            : "Scenarios let you automate source control flows. An admin can create scenarios that you can inspect and run."
        }
        noResultsTitle="No matching scenarios."
        noResultsMessage="Adjust the search or run-state filter."
      />

      {confirmTarget ? (
        <ConfirmationDialog
          open
          title={`Stop "${confirmTarget.name}"?`}
          message="The scenario run will stop. Any in-progress steps are interrupted."
          confirmLabel="Stop scenario"
          tone="warning"
          reversibilityLabel="You can start the scenario again afterwards."
          onConfirm={() => {
            handleStopConfirmed(confirmTarget);
            setConfirmStop(null);
          }}
          onClose={() => setConfirmStop(null)}
        />
      ) : null}
    </div>
  );
}
