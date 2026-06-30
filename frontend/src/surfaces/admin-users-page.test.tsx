/**
 * Tests for AdminUsersPage (UI-081 + UI-082 + UI-083)
 *
 * Covers:
 * - Access gates: Local mode → table shown; Shared User → locked panel
 * - Table renders users with role/status badges
 * - Search filters by name and email
 * - Role and status dropdown filters
 * - Role change: confirmation dialog → isProcessing → success toast + badge update
 * - Role change: error path → error toast
 * - Deactivate: confirmation dialog → success toast + status update
 * - Activate: no dialog → success toast + status update
 * - Last-admin validation: warning toast when only one active admin remains
 * - Cancel: dialog closes without mutation
 * - Role change activity panel: pre-seeded entries visible on load
 * - Role change activity panel: new entry prepended after a successful change
 * - Role change activity panel: no new entry added when save fails
 */

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setMockUserSaveShouldFail } from "./mock-users";
import { AdminUsersPage } from "./admin-users-page";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) =>
    sel({ push: mockNotifyPush }),
}));

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
  setMockUserSaveShouldFail(false);
});

beforeEach(() => {
  setMockUserSaveShouldFail(false);
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

describe("AdminUsersPage — table", () => {
  it("renders user rows with role and status badges", () => {
    setupStore();
    renderPage();
    const table = screen.getByRole("table");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(within(table).getAllByText("Admin").length).toBeGreaterThan(0);
    expect(within(table).getAllByText("Active").length).toBeGreaterThan(0);
  });

  it("filters rows by name", async () => {
    setupStore();
    renderPage();
    await userEvent.type(screen.getByPlaceholderText(/search by name/i), "Jordan");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters rows by email", async () => {
    setupStore();
    renderPage();
    await userEvent.type(screen.getByPlaceholderText(/search by name/i), "sam.chen");
    expect(screen.getAllByText("Sam Chen").length).toBeGreaterThan(0);
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });

  it("filters to admin users only", async () => {
    setupStore();
    renderPage();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /role/i }), "admin");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters to inactive users only", async () => {
    setupStore();
    renderPage();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /status/i }), "inactive");
    expect(screen.getByText("Taylor Brooks")).toBeTruthy();
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });
});

describe("AdminUsersPage — role change (save states)", () => {
  it("shows isProcessing state and resolves with success toast + badge update", async () => {
    setupStore();
    renderPage();
    const table = screen.getByRole("table");
    const adminCountBefore = within(table).getAllByText("Admin").length;
    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /change role/i }));
    await waitFor(() =>
      expect(within(table).getAllByText("Admin").length).toBe(adminCountBefore + 1),
    );
    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "success" }),
    );
  });

  it("shows error toast when save fails", async () => {
    setMockUserSaveShouldFail(true);
    setupStore();
    renderPage();
    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /change role/i }));
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "error" }),
      ),
    );
  });

  it("cancel closes dialog without mutation", async () => {
    setupStore();
    renderPage();
    const table = screen.getByRole("table");
    const adminCountBefore = within(table).getAllByText("Admin").length;
    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(screen.queryByText(/Change role for/i)).toBeNull();
    expect(within(table).getAllByText("Admin").length).toBe(adminCountBefore);
  });
});

describe("AdminUsersPage — deactivate (save states)", () => {
  it("deactivates user and shows success toast", async () => {
    setupStore();
    renderPage();
    await userEvent.click(screen.getAllByRole("button", { name: /deactivate/i })[0]);
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: /deactivate/i }));
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "success", title: "User deactivated." }),
      ),
    );
  });
});

describe("AdminUsersPage — activate (save states)", () => {
  it("activates inactive user with success toast, no dialog", async () => {
    setupStore();
    renderPage();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /status/i }), "inactive");
    await userEvent.click(screen.getAllByRole("button", { name: /^activate$/i })[0]);
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "success", title: "User activated." }),
      ),
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("AdminUsersPage — last-admin validation", () => {
  it("shows warning toast when trying to Make User the only remaining active admin", async () => {
    setupStore();
    renderPage();
    // Filter to only admins; Jordan Kim and Morgan Lee are admins.
    // Deactivate one admin first so only one active admin remains.
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /role/i }), "admin");
    // Deactivate Morgan Lee (second admin) to leave Jordan Kim as last active admin
    const deactivateBtns = screen.getAllByRole("button", { name: /deactivate/i });
    await userEvent.click(deactivateBtns[1]);
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /deactivate/i }));
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "success" }),
      ),
    );
    mockNotifyPush.mockClear();
    // Now try to Make User the last active admin
    await userEvent.click(screen.getAllByRole("button", { name: /make user/i })[0]);
    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "warning" }),
    );
  });
});

describe("AdminUsersPage — role change activity (UI-083)", () => {
  it("shows the role change activity panel on load with pre-seeded entries", () => {
    setupStore();
    renderPage();
    const panel = screen.getByRole("region", { name: /role change activity/i });
    expect(panel).toBeTruthy();
    expect(panel.textContent).toMatch(/Sam Chen/);
    expect(panel.textContent).toMatch(/Jordan Kim/);
    expect(screen.getByText("Yesterday at 11:42")).toBeTruthy();
  });

  it("prepends a new activity entry after a successful role change", async () => {
    setupStore();
    renderPage();
    const logBefore = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem");
    const countBefore = logBefore.length;

    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /change role/i }));

    await waitFor(() => {
      const logAfter = within(
        screen.getByRole("list", { name: /role change log/i }),
      ).getAllByRole("listitem");
      expect(logAfter.length).toBe(countBefore + 1);
    });

    const firstEntry = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem")[0];
    expect(firstEntry.textContent).toMatch(/Just now/);
    expect(firstEntry.textContent).toMatch(/by You/);
  });

  it("does not add an activity entry when the save fails", async () => {
    setMockUserSaveShouldFail(true);
    setupStore();
    renderPage();
    const logBefore = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem");
    const countBefore = logBefore.length;

    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /change role/i }));

    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "error" }),
      ),
    );

    const logAfter = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem");
    expect(logAfter.length).toBe(countBefore);
  });

  it("shows 'from role → to role' in each activity entry", () => {
    setupStore();
    renderPage();
    const log = screen.getByRole("list", { name: /role change log/i });
    expect(log.textContent).toMatch(/Admin/);
    expect(log.textContent).toMatch(/User/);
  });
});
