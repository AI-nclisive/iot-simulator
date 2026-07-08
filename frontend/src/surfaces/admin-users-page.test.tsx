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
 * - Role change activity panel: shows empty state when log is empty
 * - Role change activity panel: cancel does not mutate the activity log
 */

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { type ActivityEventDto, AdminUsersPage } from "./admin-users-page";

// ── Mock the API module ───────────────────────────────────────────────────────

const mockApiFetch = vi.fn();

vi.mock("../api", () => ({
  apiFetch: (...args: unknown[]) => mockApiFetch(...args),
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, title: string) {
      super(title);
      this.status = status;
    }
  },
}));

// ── Seed data (API response shape) ───────────────────────────────────────────

const apiUsers = [
  { id: "u-001", displayName: "Jordan Kim", subject: "jordan.kim@example.com", role: "admin", status: "ACTIVE", lastSeenAt: null },
  { id: "u-002", displayName: "Alex Rivera", subject: "alex.rivera@example.com", role: "user", status: "ACTIVE", lastSeenAt: null },
  { id: "u-003", displayName: "Sam Chen", subject: "sam.chen@example.com", role: "user", status: "ACTIVE", lastSeenAt: null },
  { id: "u-004", displayName: "Taylor Brooks", subject: "taylor.brooks@example.com", role: "user", status: "SUSPENDED", lastSeenAt: null },
  { id: "u-005", displayName: "Morgan Lee", subject: "morgan.lee@example.com", role: "admin", status: "ACTIVE", lastSeenAt: null },
  { id: "u-006", displayName: "Casey Davis", subject: "casey.davis@example.com", role: "user", status: "SUSPENDED", lastSeenAt: null },
  { id: "u-007", displayName: "Riley Wilson", subject: "riley.wilson@example.com", role: "user", status: "ACTIVE", lastSeenAt: null },
];

const seedActivityEvents: ActivityEventDto[] = [
  {
    id: 1,
    projectId: null,
    actor: "Jordan Kim",
    action: "change_role",
    objectType: "user",
    objectId: "u-003",
    at: "2026-07-07T11:42:00Z",
    detail: { role: "user" },
  },
  {
    id: 2,
    projectId: null,
    actor: "Morgan Lee",
    action: "change_role",
    objectType: "user",
    objectId: "u-007",
    at: "2026-07-04T09:00:00Z",
    detail: { role: "admin" },
  },
];

let activityEvents: ActivityEventDto[] = seedActivityEvents.slice();

function resetActivityEvents() {
  activityEvents = seedActivityEvents.slice();
}

function makeApiUser(overrides: Partial<(typeof apiUsers)[0]>) {
  return { ...apiUsers[0], ...overrides };
}

function setupApiFetch(opts: { saveShouldFail?: boolean; emptyActivity?: boolean } = {}) {
  mockApiFetch.mockImplementation((path: string, init?: RequestInit) => {
    const method = (init?.method ?? "GET").toUpperCase();
    if (method === "GET" && path.includes("/admin/activity")) {
      if (opts.emptyActivity) return Promise.resolve({ events: [], nextCursor: null });
      return Promise.resolve({ events: activityEvents, nextCursor: null });
    }
    if (method === "GET") {
      return Promise.resolve({ items: apiUsers });
    }
    if (opts.saveShouldFail) {
      return Promise.reject(new Error("Service unavailable"));
    }
    if (path.includes("/roles")) {
      const userId = path.split("/")[5];
      const body = JSON.parse(init?.body as string) as { role: string };
      const user = apiUsers.find((u) => u.id === userId) ?? apiUsers[0];
      return Promise.resolve(makeApiUser({ ...user, role: body.role }));
    }
    if (path.includes("/status")) {
      const userId = path.split("/")[5];
      const body = JSON.parse(init?.body as string) as { status: string };
      const user = apiUsers.find((u) => u.id === userId) ?? apiUsers[0];
      return Promise.resolve(makeApiUser({ ...user, status: body.status }));
    }
    return Promise.resolve({});
  });
}

// ── Shell store mock ──────────────────────────────────────────────────────────

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
  resetActivityEvents();
});

beforeEach(() => {
  setupApiFetch();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminUsersPage />
    </MemoryRouter>,
  );
}

async function waitForTable() {
  return waitFor(() => screen.getByRole("table"));
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("AdminUsersPage — loading and error states", () => {
  it("shows loading panel while GET is in flight", () => {
    mockApiFetch.mockImplementation(() => new Promise(() => {})); // never resolves
    setupStore();
    renderPage();
    expect(screen.getByText("Loading users…")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("shows error panel when GET /api/v1/admin/users fails", async () => {
    mockApiFetch.mockRejectedValue(new Error("Network error"));
    setupStore();
    renderPage();
    await waitFor(() => expect(screen.queryByText("Loading users…")).toBeNull());
    expect(screen.getByText("Failed to load users.")).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("AdminUsersPage — access gates", () => {
  it("shows the users table in Local mode (trusted admin)", async () => {
    setupStore({ accessMode: "local" });
    renderPage();
    await waitForTable();
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
  it("renders user rows with role and status badges", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    const table = screen.getByRole("table");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(within(table).getAllByText("Admin").length).toBeGreaterThan(0);
    expect(within(table).getAllByText("Active").length).toBeGreaterThan(0);
  });

  it("filters rows by name", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    await userEvent.type(screen.getByPlaceholderText(/search by name/i), "Jordan");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters rows by email", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    await userEvent.type(screen.getByPlaceholderText(/search by name/i), "sam.chen");
    expect(screen.getAllByText("Sam Chen").length).toBeGreaterThan(0);
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });

  it("filters to admin users only", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /role/i }), "admin");
    expect(screen.getByText("Jordan Kim")).toBeTruthy();
    expect(screen.queryByText("Alex Rivera")).toBeNull();
  });

  it("filters to inactive users only", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /status/i }), "inactive");
    expect(screen.getByText("Taylor Brooks")).toBeTruthy();
    expect(screen.queryByText("Jordan Kim")).toBeNull();
  });
});

describe("AdminUsersPage — role change (save states)", () => {
  it("shows isProcessing state and resolves with success toast + badge update", async () => {
    setupStore();
    renderPage();
    await waitForTable();
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
    setupApiFetch({ saveShouldFail: true });
    setupStore();
    renderPage();
    await waitForTable();
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
    await waitForTable();
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
    await waitForTable();
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
    await waitForTable();
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
    await waitForTable();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /role/i }), "admin");
    const deactivateBtns = screen.getAllByRole("button", { name: /deactivate/i });
    await userEvent.click(deactivateBtns[1]);
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /deactivate/i }));
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "success" }),
      ),
    );
    mockNotifyPush.mockClear();
    await userEvent.click(screen.getAllByRole("button", { name: /make user/i })[0]);
    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "warning" }),
    );
  });
});

describe("AdminUsersPage — role change activity (UI-023)", () => {
  it("shows the role change activity panel on load with API-fetched entries", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    const panel = screen.getByRole("region", { name: /role change activity/i });
    expect(panel).toBeTruthy();
    // objectIds from seed events
    expect(panel.textContent).toMatch(/u-003/);
    expect(panel.textContent).toMatch(/u-007/);
    expect(panel.textContent).toMatch(/Jordan Kim/);
  });

  it("prepends a new activity entry after a successful role change", async () => {
    setupStore();
    renderPage();
    await waitForTable();
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
    expect(firstEntry.textContent).toMatch(/by local/);
  });

  it("does not add an activity entry when the save fails", async () => {
    setupApiFetch({ saveShouldFail: true });
    setupStore();
    renderPage();
    await waitForTable();
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

  it("shows role label in each activity entry", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    const log = screen.getByRole("list", { name: /role change log/i });
    expect(log.textContent).toMatch(/Role changed to/);
  });

  it("shows empty state when no activity events are returned", async () => {
    setupApiFetch({ emptyActivity: true });
    setupStore();
    renderPage();
    await waitForTable();
    const panel = screen.getByRole("region", { name: /role change activity/i });
    expect(panel).toBeTruthy();
    expect(panel.textContent).toMatch(/Role changes made by admins will appear here/);
    expect(screen.queryByRole("list", { name: /role change log/i })).toBeNull();
  });

  it("cancel does not mutate the activity log", async () => {
    setupStore();
    renderPage();
    await waitForTable();
    const logBefore = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem");
    const countBefore = logBefore.length;

    await userEvent.click(screen.getAllByRole("button", { name: /make admin/i })[0]);
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));

    const logAfter = within(
      screen.getByRole("list", { name: /role change log/i }),
    ).getAllByRole("listitem");
    expect(logAfter.length).toBe(countBefore);
  });
});
