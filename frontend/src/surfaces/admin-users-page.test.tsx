/**
 * Tests for AdminUsersPage (UI-081)
 *
 * Covers:
 * - Local mode: informational panel shown, no table
 * - Shared User: locked panel shown, no table
 * - Shared Admin: table visible with users
 * - Filters: role and status filter narrow the list
 * - Search: filters by name and email
 * - Row action: Make Admin opens confirmation dialog
 * - Row action: Deactivate opens confirmation dialog
 * - Row action: Activate applies immediately without dialog
 * - Confirming role change updates the row badge
 * - Cancelling dialog leaves data unchanged
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminUsersPage } from "./admin-users-page";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

function setupStore(opts: {
  accessMode?: "local" | "shared";
  sharedRole?: "admin" | "user";
} = {}) {
  const { accessMode = "shared", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole }),
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminUsersPage />
    </MemoryRouter>,
  );
}

describe("AdminUsersPage — access gates", () => {
  it("shows the users table in Local mode (trusted admin)", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    expect(screen.getByRole("table")).toBeTruthy();
  });

  it("shows locked panel for shared User role", () => {
    setupStore({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    expect(screen.getByText("Admin access is required.")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("AdminUsersPage — table (shared Admin)", () => {
  it("renders the users table with user rows", () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    expect(screen.getByRole("table")).toBeTruthy();
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.getByText("Alex Rivera")).toBeTruthy();
  });

  it("shows Role and Status badges", () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const adminBadges = screen.getAllByText("Admin");
    expect(adminBadges.length).toBeGreaterThan(0);
    const activeBadges = screen.getAllByText("Active");
    expect(activeBadges.length).toBeGreaterThan(0);
  });
});

describe("AdminUsersPage — search", () => {
  it("filters rows by name", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const input = screen.getByPlaceholderText(/search by name/i);
    await userEvent.type(input, "Jordan");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters rows by email", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const input = screen.getByPlaceholderText(/search by name/i);
    await userEvent.type(input, "sam.chen");
    expect(screen.getByText("Sam Chen")).toBeTruthy();
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });
});

describe("AdminUsersPage — role/status filters", () => {
  it("filters to admin users only", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const roleSelect = screen.getByRole("combobox", { name: /role/i });
    await userEvent.selectOptions(roleSelect, "admin");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters to inactive users only", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const statusSelect = screen.getByRole("combobox", { name: /status/i });
    await userEvent.selectOptions(statusSelect, "inactive");
    expect(screen.getByText("Taylor Brooks")).toBeTruthy();
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });
});

describe("AdminUsersPage — row actions", () => {
  it("opens role-change confirmation dialog when clicking Make Admin", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const makeAdminBtn = screen.getAllByRole("button", { name: /make admin/i })[0];
    await userEvent.click(makeAdminBtn);
    expect(screen.getByText(/Change role for/i)).toBeTruthy();
  });

  it("opens deactivate confirmation dialog", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const deactivateBtns = screen.getAllByRole("button", { name: /deactivate/i });
    await userEvent.click(deactivateBtns[0]);
    expect(screen.getByText(/no longer be able to access/i)).toBeTruthy();
  });

  it("confirming role change updates the badge", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const adminCountBefore = screen.getAllByText("Admin").length;
    const makeAdminBtn = screen.getAllByRole("button", { name: /make admin/i })[0];
    await userEvent.click(makeAdminBtn);
    await userEvent.click(screen.getByRole("button", { name: /change role/i }));
    await waitFor(() =>
      expect(screen.getAllByText("Admin").length).toBe(adminCountBefore + 1),
    );
  });

  it("cancelling role-change dialog leaves data unchanged", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const makeAdminBtn = screen.getAllByRole("button", { name: /make admin/i })[0];
    await userEvent.click(makeAdminBtn);
    const cancelBtn = screen.getByRole("button", { name: /cancel/i });
    await userEvent.click(cancelBtn);
    expect(screen.queryByText(/Change role for/i)).toBeNull();
    expect(screen.getByText("Alex Rivera")).toBeTruthy();
  });

  it("activate applies immediately without dialog", async () => {
    setupStore({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const statusSelect = screen.getByRole("combobox", { name: /status/i });
    await userEvent.selectOptions(statusSelect, "inactive");
    const activateBtn = screen.getAllByRole("button", { name: /^activate$/i })[0];
    await userEvent.click(activateBtn);
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
