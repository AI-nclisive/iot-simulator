/**
 * Tests for ActivityPage (UI-024)
 *
 * Covers:
 * - Loading state while fetch is in flight
 * - Error state when fetch fails
 * - Renders activity timeline with actor, action, object, and time
 * - Filter by actor
 * - Filter by action
 * - Filter by object type
 * - Clear filters button removes all active filters
 * - Empty state when no events match filters
 * - Load more button hidden when no nextCursor
 * - Load more button appends events when nextCursor present
 * - Load more error shows warning notification
 * - Load more visible with filters active when nextCursor present
 * - No fetch when projectId is empty
 */

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { type ActivityEventDto } from "../types/activity";
import { ActivityPage } from "./activity-page";

// ── Mock the API module ───────────────────────────────────────────────────────

const mockApiFetch = vi.fn();

vi.mock("../api", () => ({
  apiFetch: (...args: unknown[]) => mockApiFetch(...args),
  ApiError: class ApiError extends Error {
    status: number;
    title: string;
    constructor(status: number, title: string) {
      super(title);
      this.status = status;
      this.title = title;
    }
  },
}));

// ── Shell store mock ──────────────────────────────────────────────────────────

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) =>
    sel({ push: mockNotifyPush }),
}));

function setupStore(projectId = "p-001") {
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ currentProjectId: projectId }),
  );
}

// ── Seed data ─────────────────────────────────────────────────────────────────

const seedEvents: ActivityEventDto[] = [
  {
    id: 1,
    projectId: "p-001",
    actor: "local",
    action: "create",
    objectType: "data_source",
    objectId: "ds-001",
    at: "2026-07-07T10:00:00Z",
    detail: {},
  },
  {
    id: 2,
    projectId: "p-001",
    actor: "local",
    action: "start",
    objectType: "data_source",
    objectId: "ds-001",
    at: "2026-07-07T10:05:00Z",
    detail: {},
  },
  {
    id: 3,
    projectId: "p-001",
    actor: "automation",
    action: "stop",
    objectType: "data_source",
    objectId: "ds-001",
    at: "2026-07-07T10:10:00Z",
    detail: {},
  },
  {
    id: 4,
    projectId: "p-001",
    actor: "local",
    action: "change_role",
    objectType: "user",
    objectId: "u-001",
    at: "2026-07-07T09:00:00Z",
    detail: { role: "admin" },
  },
];

function setupApiFetch(opts: {
  failFetch?: boolean;
  failLoadMore?: boolean;
  events?: ActivityEventDto[];
  nextCursor?: string | null;
  moreEvents?: ActivityEventDto[];
} = {}) {
  let callCount = 0;
  mockApiFetch.mockImplementation((_path: string) => {
    callCount++;
    if (callCount === 1 && opts.failFetch) return Promise.reject(new Error("Network error"));
    if (callCount === 1) {
      return Promise.resolve({
        events: opts.events ?? seedEvents,
        nextCursor: opts.nextCursor ?? null,
      });
    }
    if (opts.failLoadMore) return Promise.reject(new Error("Network error"));
    return Promise.resolve({
      events: opts.moreEvents ?? [],
      nextCursor: null,
    });
  });
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  setupApiFetch();
  setupStore();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <ActivityPage />
    </MemoryRouter>,
  );
}

async function waitForTimeline() {
  return waitFor(() => screen.getByRole("list", { name: /activity timeline/i }));
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ActivityPage — loading and error states", () => {
  it("shows loading panel while fetch is in flight", () => {
    mockApiFetch.mockImplementation(() => new Promise(() => {}));
    renderPage();
    expect(screen.getByText("Loading activity…")).toBeTruthy();
    expect(screen.queryByRole("list", { name: /activity timeline/i })).toBeNull();
  });

  it("shows error panel when fetch fails", async () => {
    setupApiFetch({ failFetch: true });
    renderPage();
    await waitFor(() => expect(screen.queryByText("Loading activity…")).toBeNull());
    expect(screen.getByText("Failed to load activity.")).toBeTruthy();
    expect(screen.queryByRole("list", { name: /activity timeline/i })).toBeNull();
  });

  it("does not fetch when projectId is empty", () => {
    setupStore("");
    renderPage();
    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});

describe("ActivityPage — timeline content", () => {
  it("renders events with actor, action label, objectId, and time", async () => {
    renderPage();
    await waitForTimeline();
    const list = screen.getByRole("list", { name: /activity timeline/i });
    const items = within(list).getAllByRole("listitem");
    expect(items.length).toBe(seedEvents.length);
    expect(list.textContent).toMatch(/Created data_source/i);
    expect(list.textContent).toMatch(/Started data_source/i);
    expect(list.textContent).toMatch(/ds-001/);
    expect(list.textContent).toMatch(/by local/);
    expect(list.textContent).toMatch(/by automation/);
  });

  it("renders change_role event with role detail", async () => {
    renderPage();
    await waitForTimeline();
    expect(screen.getByText(/Changed role to admin/i)).toBeTruthy();
  });
});

describe("ActivityPage — filters", () => {
  it("filters by actor", async () => {
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /actor/i }), "automation");
    const list = screen.getByRole("list", { name: /activity timeline/i });
    expect(within(list).getAllByRole("listitem").length).toBe(1);
    expect(list.textContent).toMatch(/automation/);
    expect(list.textContent).not.toMatch(/by local/);
  });

  it("filters by action", async () => {
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /action/i }), "stop");
    const list = screen.getByRole("list", { name: /activity timeline/i });
    expect(within(list).getAllByRole("listitem").length).toBe(1);
  });

  it("filters by object type", async () => {
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /object/i }), "user");
    const list = screen.getByRole("list", { name: /activity timeline/i });
    expect(within(list).getAllByRole("listitem").length).toBe(1);
    expect(list.textContent).toMatch(/u-001/);
  });

  it("shows empty state when no events match filters", async () => {
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /actor/i }), "automation");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /object/i }), "user");
    expect(screen.queryByRole("list", { name: /activity timeline/i })).toBeNull();
    expect(screen.getByText(/No activity events match/i)).toBeTruthy();
  });

  it("clear filters button resets all filters and shows all events", async () => {
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /actor/i }), "automation");
    const clearBtn = screen.getByRole("button", { name: /clear filters/i });
    await userEvent.click(clearBtn);
    expect(screen.queryByRole("button", { name: /clear filters/i })).toBeNull();
    const list = screen.getByRole("list", { name: /activity timeline/i });
    expect(within(list).getAllByRole("listitem").length).toBe(seedEvents.length);
  });
});

describe("ActivityPage — pagination", () => {
  it("does not show load more button when nextCursor is null", async () => {
    renderPage();
    await waitForTimeline();
    expect(screen.queryByRole("button", { name: /load more/i })).toBeNull();
  });

  it("shows load more button when nextCursor is present", async () => {
    setupApiFetch({ nextCursor: "abc123" });
    renderPage();
    await waitForTimeline();
    expect(screen.getByRole("button", { name: /load more/i })).toBeTruthy();
  });

  it("load more button remains visible when filters are active and nextCursor is set", async () => {
    setupApiFetch({ nextCursor: "abc123" });
    renderPage();
    await waitForTimeline();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: /actor/i }), "local");
    expect(screen.getByRole("button", { name: /load more/i })).toBeTruthy();
  });

  it("appends events when load more is clicked", async () => {
    const moreEvents: ActivityEventDto[] = [
      {
        id: 99,
        projectId: "p-001",
        actor: "local",
        action: "delete",
        objectType: "scenario",
        objectId: "scn-099",
        at: "2026-07-06T08:00:00Z",
        detail: {},
      },
    ];
    setupApiFetch({ nextCursor: "cursor1", moreEvents });
    renderPage();
    await waitForTimeline();
    const countBefore = within(
      screen.getByRole("list", { name: /activity timeline/i }),
    ).getAllByRole("listitem").length;
    await userEvent.click(screen.getByRole("button", { name: /load more/i }));
    await waitFor(() => {
      const countAfter = within(
        screen.getByRole("list", { name: /activity timeline/i }),
      ).getAllByRole("listitem").length;
      expect(countAfter).toBe(countBefore + moreEvents.length);
    });
    expect(screen.queryByRole("button", { name: /load more/i })).toBeNull();
  });

  it("shows warning notification when load more fails", async () => {
    setupApiFetch({ nextCursor: "cursor1", failLoadMore: true });
    renderPage();
    await waitForTimeline();
    await userEvent.click(screen.getByRole("button", { name: /load more/i }));
    await waitFor(() =>
      expect(mockNotifyPush).toHaveBeenCalledWith(
        expect.objectContaining({ tone: "warning" }),
      ),
    );
    expect(screen.getByRole("button", { name: /load more/i })).toBeTruthy();
  });
});
