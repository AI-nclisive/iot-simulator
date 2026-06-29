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
import userEvent from "@testing-library/user-event";
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

// ---------------------------------------------------------------------------
// Pagination tests
// ---------------------------------------------------------------------------

function makeRows(count: number): Row[] {
  return Array.from({ length: count }, (_, i) => ({
    id: String(i + 1),
    name: `Row ${i + 1}`,
    value: i + 1,
  }));
}

function renderPaged(rowData: Row[], pageSize?: number) {
  return render(
    <OperationalTable
      columns={columns}
      rows={rowData}
      rowKey={(r) => r.id}
      sortState={null}
      onSortChange={vi.fn()}
      hasQueryState={false}
      emptyTitle="No rows"
      emptyMessage="Table is empty"
      noResultsTitle="No results"
      noResultsMessage="No matching rows"
      pageSize={pageSize}
    />,
  );
}

describe("OperationalTable — pagination not shown for small sets", () => {
  it("no pagination controls when rows fit on one page", () => {
    renderPaged(makeRows(3), 5);
    expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Previous" })).toBeNull();
  });
});

describe("OperationalTable — pagination controls appear for large sets", () => {
  it("shows Previous and Next buttons when rows exceed pageSize", () => {
    renderPaged(makeRows(10), 3);
    expect(screen.getByRole("button", { name: "Previous" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Next" })).toBeTruthy();
  });

  it("Previous is disabled on the first page", () => {
    renderPaged(makeRows(10), 3);
    expect((screen.getByRole("button", { name: "Previous" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("shows only the first page of rows initially", () => {
    renderPaged(makeRows(10), 3);
    expect(screen.getByText("Row 1")).toBeTruthy();
    expect(screen.getByText("Row 3")).toBeTruthy();
    expect(screen.queryByText("Row 4")).toBeNull();
  });

  it("clicking Next shows the next page", async () => {
    renderPaged(makeRows(10), 3);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Row 4")).toBeTruthy();
    expect(screen.queryByText("Row 1")).toBeNull();
  });

  it("Next is disabled on the last page", async () => {
    renderPaged(makeRows(6), 3);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Previous" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("clicking Previous returns to the previous page", async () => {
    renderPaged(makeRows(10), 3);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Previous" }));
    expect(screen.getByText("Row 1")).toBeTruthy();
    expect(screen.queryByText("Row 4")).toBeNull();
  });

  it("shows correct row range label", () => {
    renderPaged(makeRows(10), 3);
    expect(screen.getByText("1–3 of 10")).toBeTruthy();
  });
});
