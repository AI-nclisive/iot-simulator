/**
 * ARIA regression tests for table-pattern (UI-091)
 *
 * Covers:
 * - Column headers have scope="col"
 * - Sortable columns have aria-sort="none" when not active
 * - Active sorted column has aria-sort="ascending" / "descending"
 * - Non-sortable columns have no aria-sort attribute
 * - Sort direction indicator is aria-hidden
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { OperationalTable } from "./table-pattern";

afterEach(cleanup);

type Row = { id: string; name: string; value: number };

const columns = [
  {
    id: "name",
    header: "Name",
    cell: (r: Row) => r.name,
    sortable: true,
    sortValue: (r: Row) => r.name,
  },
  {
    id: "value",
    header: "Value",
    cell: (r: Row) => r.value,
    sortable: true,
    sortValue: (r: Row) => r.value,
  },
  {
    id: "static",
    header: "Static",
    cell: (r: Row) => r.id,
  },
];

const rows: Row[] = [
  { id: "1", name: "Alpha", value: 10 },
  { id: "2", name: "Beta", value: 5 },
];

function renderTable(sortState: { columnId: string; direction: "asc" | "desc" } | null) {
  return render(
    <OperationalTable
      columns={columns}
      rows={rows}
      rowKey={(r) => r.id}
      sortState={sortState}
      onSortChange={vi.fn()}
      hasQueryState={false}
      emptyTitle="No rows"
      emptyMessage="Table is empty"
      noResultsTitle="No results"
      noResultsMessage="No matching rows"
    />,
  );
}

describe("OperationalTable — column header scope", () => {
  it("every th has scope='col'", () => {
    renderTable(null);
    const headers = screen.getAllByRole("columnheader");
    expect(headers.length).toBeGreaterThan(0);
    headers.forEach((th) => {
      expect(th.getAttribute("scope")).toBe("col");
    });
  });
});

describe("OperationalTable — aria-sort", () => {
  it("sortable columns have aria-sort='none' when no sort is active", () => {
    renderTable(null);
    expect(screen.getByRole("columnheader", { name: "Name" }).getAttribute("aria-sort")).toBe("none");
    expect(screen.getByRole("columnheader", { name: "Value" }).getAttribute("aria-sort")).toBe("none");
  });

  it("active column has aria-sort='ascending' when sorted asc", () => {
    renderTable({ columnId: "name", direction: "asc" });
    const nameHeader = screen.getAllByRole("columnheader").find((th) =>
      th.textContent?.includes("Name"),
    )!;
    expect(nameHeader.getAttribute("aria-sort")).toBe("ascending");
    expect(screen.getByRole("columnheader", { name: "Value" }).getAttribute("aria-sort")).toBe("none");
  });

  it("active column has aria-sort='descending' when sorted desc", () => {
    renderTable({ columnId: "value", direction: "desc" });
    const valueHeader = screen.getAllByRole("columnheader").find((th) =>
      th.textContent?.includes("Value"),
    )!;
    expect(valueHeader.getAttribute("aria-sort")).toBe("descending");
  });

  it("non-sortable column has no aria-sort attribute", () => {
    renderTable(null);
    expect(screen.getByRole("columnheader", { name: "Static" }).getAttribute("aria-sort")).toBeNull();
  });
});

describe("OperationalTable — sort direction indicator", () => {
  it("sort indicator span is aria-hidden", () => {
    const { container } = renderTable({ columnId: "name", direction: "asc" });
    const indicators = container.querySelectorAll('[aria-hidden="true"]');
    expect(indicators.length).toBeGreaterThan(0);
  });
});
