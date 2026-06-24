import { useMemo, type ReactNode } from "react";
import { SharedStatePanel } from "./shared-state-panel";

type TableSortDirection = "asc" | "desc";

type TableSortState = {
  columnId: string;
  direction: TableSortDirection;
} | null;

type TableFilterOption = {
  label: string;
  value: string;
};

type TableFilterControl = {
  id: string;
  label: string;
  value: string;
  options: TableFilterOption[];
  onChange: (value: string) => void;
};

type ActiveTableFilter = {
  id: string;
  label: string;
  value: string;
  onClear: () => void;
};

type TableColumn<T> = {
  id: string;
  header: string;
  cell: (row: T) => ReactNode;
  sortable?: boolean;
  sortValue?: (row: T) => string | number;
  className?: string;
};

type TableRowAction<T> = {
  label: string;
  onClick?: (row: T) => void;
  tone?: "default" | "danger";
};

type OperationalTableProps<T> = {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  rowActions?: (row: T) => TableRowAction<T>[];
  sortState: TableSortState;
  onSortChange: (nextState: TableSortState) => void;
  hasQueryState: boolean;
  emptyTitle: string;
  emptyMessage: string;
  noResultsTitle: string;
  noResultsMessage: string;
};

type TableToolbarProps = {
  searchValue: string;
  searchPlaceholder: string;
  onSearchChange: (value: string) => void;
  filters: TableFilterControl[];
  activeFilters: ActiveTableFilter[];
  onClearAll: () => void;
  resultLabel?: string;
  primaryActionLabel?: string;
  onPrimaryAction?: () => void;
};

function compareValues(left: string | number, right: string | number) {
  if (typeof left === "number" && typeof right === "number") {
    return left - right;
  }

  return String(left).localeCompare(String(right));
}

function sortLabel(direction: TableSortDirection) {
  return direction === "asc" ? "asc" : "desc";
}

function nextSortState(columnId: string, sortState: TableSortState): TableSortState {
  if (!sortState || sortState.columnId !== columnId) {
    return { columnId, direction: "asc" };
  }

  return {
    columnId,
    direction: sortState.direction === "asc" ? "desc" : "asc",
  };
}

export function TableToolbar({
  searchValue,
  searchPlaceholder,
  onSearchChange,
  filters,
  activeFilters,
  onClearAll,
  resultLabel,
  primaryActionLabel,
  onPrimaryAction,
}: TableToolbarProps) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div className="flex min-w-0 flex-1 flex-col gap-3 lg:flex-row">
          <label className="flex min-w-0 flex-1 flex-col gap-2 text-sm text-shell-muted">
            Search
            <input
              className="shell-field"
              placeholder={searchPlaceholder}
              type="search"
              value={searchValue}
              onChange={(event) => onSearchChange(event.target.value)}
            />
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            {filters.map((filter) => (
              <label key={filter.id} className="flex flex-col gap-2 text-sm text-shell-muted">
                {filter.label}
                <select
                  className="shell-field"
                  value={filter.value}
                  onChange={(event) => filter.onChange(event.target.value)}
                >
                  {filter.options.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
        </div>

        <div className="flex shrink-0 flex-wrap items-end justify-end gap-2 xl:pt-7">
          {resultLabel ? <span className="text-sm text-shell-muted">{resultLabel}</span> : null}
          {primaryActionLabel ? (
            <button className="shell-action" type="button" onClick={onPrimaryAction}>
              {primaryActionLabel}
            </button>
          ) : null}
        </div>
      </div>

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
          <button className="shell-text-action" type="button" onClick={onClearAll}>
            Clear all
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function OperationalTable<T>({
  columns,
  rows,
  rowKey,
  rowActions,
  sortState,
  onSortChange,
  hasQueryState,
  emptyTitle,
  emptyMessage,
  noResultsTitle,
  noResultsMessage,
}: OperationalTableProps<T>) {
  const sortedRows = useMemo(() => {
    if (!sortState) {
      return rows;
    }

    const column = columns.find((item) => item.id === sortState.columnId);
    if (!column?.sortValue) {
      return rows;
    }

    const sorted = [...rows].sort((left, right) => {
      const comparison = compareValues(column.sortValue!(left), column.sortValue!(right));
      return sortState.direction === "asc" ? comparison : -comparison;
    });

    return sorted;
  }, [columns, rows, sortState]);

  if (sortedRows.length === 0) {
    return (
      <SharedStatePanel
        message={hasQueryState ? noResultsMessage : emptyMessage}
        state="empty"
        title={hasQueryState ? noResultsTitle : emptyTitle}
      />
    );
  }

  return (
    <div className="overflow-hidden rounded-md border border-shell-line bg-white">
      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse">
          <thead className="bg-shell-base/65">
            <tr>
              {columns.map((column) => (
                <th key={column.id} className={`px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted ${column.className ?? ""}`}>
                  {column.sortable ? (
                    <button
                      className="inline-flex items-center gap-2 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted"
                      type="button"
                      onClick={() => onSortChange(nextSortState(column.id, sortState))}
                    >
                      <span>{column.header}</span>
                      {sortState?.columnId === column.id ? (
                        <span>{sortLabel(sortState.direction)}</span>
                      ) : null}
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              ))}
              {rowActions ? (
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Actions
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>
            {sortedRows.map((row) => {
              const actions = rowActions?.(row) ?? [];

              return (
                <tr key={rowKey(row)} className="border-t border-shell-line">
                  {columns.map((column) => (
                    <td key={column.id} className={`px-4 py-4 align-top text-sm text-shell-ink ${column.className ?? ""}`}>
                      {column.cell(row)}
                    </td>
                  ))}
                  {rowActions ? (
                    <td className="px-4 py-4 align-top">
                      <div className="flex flex-wrap justify-end gap-2">
                        {actions.map((action) => (
                          <button
                            key={action.label}
                            className={action.tone === "danger" ? "shell-text-action-danger" : "shell-text-action"}
                            type="button"
                            onClick={() => action.onClick?.(row)}
                          >
                            {action.label}
                          </button>
                        ))}
                      </div>
                    </td>
                  ) : null}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export type {
  ActiveTableFilter,
  TableColumn,
  TableFilterControl,
  TableFilterOption,
  TableRowAction,
  TableSortDirection,
  TableSortState,
};
