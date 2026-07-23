/**
 * Tests for DataSourceDetailEventsTab (UI-106)
 *
 * Covers:
 * - Loading state shown while fetch is in flight
 * - Empty state when API returns no events
 * - Events loaded from API are rendered
 * - Type→level/category mapping (SOURCE_START, SOURCE_STALE, SOURCE_ERROR, ERROR)
 * - Category filter shows only matching events
 * - Level filter shows only matching events
 * - Intersection: both filters active narrows events
 * - No-match empty state when filters produce empty result
 * - Live SSE events with matching dataSourceId are prepended
 * - Live SSE events with non-matching dataSourceId are ignored
 * - Duplicate live events (same id) are not added twice
 * - Error state shown when fetch fails
 * - Expand/collapse detail panel
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DataSourceRow } from "./mock-data-sources";
import type { LiveRuntimeEvent, LiveRuntimeResult } from "../shell/use-live-runtime";
import { DataSourceDetailEventsTab } from "./data-source-detail-events-tab";

// ── hoisted mocks ────────────────────────────────────────────────────────────

const { mockApiFetch, mockUseLiveRuntime, mockUseShellStore } = vi.hoisted(() => ({
  mockApiFetch: vi.fn(),
  mockUseLiveRuntime: vi.fn(),
  mockUseShellStore: vi.fn(),
}));

vi.mock("../api/client", () => ({
  apiFetch: mockApiFetch,
}));

vi.mock("../shell/use-live-runtime", () => ({
  useLiveRuntime: mockUseLiveRuntime,
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: { currentProjectId: string }) => unknown) =>
    mockUseShellStore(selector),
}));

// ── helpers ──────────────────────────────────────────────────────────────────

const PROJECT_ID = "proj-1";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

/** Default no-live-events result */
function noLiveEvents(): LiveRuntimeResult {
  return { sources: [], events: [], status: "open" };
}

/** Live result with one matching SSE event */
function withLiveEvent(event: Partial<LiveRuntimeEvent>): LiveRuntimeResult {
  return {
    sources: [],
    events: [
      {
        dataSourceId: "src-test",
        type: "SOURCE_START",
        at: "2026-01-01T10:00:00Z",
        ...event,
      },
    ],
    status: "open",
  };
}

function setupDefaults(liveResult: LiveRuntimeResult = noLiveEvents()) {
  mockUseShellStore.mockImplementation(
    (sel: (s: { currentProjectId: string }) => unknown) =>
      sel({ currentProjectId: PROJECT_ID }),
  );
  mockUseLiveRuntime.mockReturnValue(liveResult);
}

const mockSource: DataSourceRow = {
  id: "src-test",
  name: "Test Source",
  protocol: "OPC UA",
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 10,
  status: "Active",
  health: "Healthy",
};

type DtoOverride = {
  id?: number;
  type?: string;
  at?: string;
  dataSourceId?: string | null;
  payload?: Record<string, unknown>;
};

function makeDto(overrides: DtoOverride = {}) {
  return {
    id: 1,
    type: "SOURCE_START",
    at: "2026-01-01T10:00:00Z",
    dataSourceId: "src-test",
    runId: null,
    payload: {},
    ...overrides,
  };
}

function resolvedApiFetch(events: ReturnType<typeof makeDto>[]) {
  mockApiFetch.mockResolvedValue({ events, nextCursor: null });
}

function rejectedApiFetch(message: string) {
  mockApiFetch.mockRejectedValue(new Error(message));
}

// ── loading state ─────────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — loading state", () => {
  it("shows loading panel while fetch is in flight", () => {
    setupDefaults();
    // Never resolves during this test
    mockApiFetch.mockReturnValue(new Promise(() => {}));
    render(<DataSourceDetailEventsTab source={mockSource} />);
    expect(screen.getByText(/Loading events/i)).toBeTruthy();
  });
});

// ── error state ───────────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — error state", () => {
  it("shows error panel when fetch fails", async () => {
    setupDefaults();
    rejectedApiFetch("Network error");
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      expect(screen.getByText(/Failed to load events/i)).toBeTruthy();
    });
  });
});

// ── empty state ───────────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — empty state", () => {
  it("shows empty state when API returns no events", async () => {
    setupDefaults();
    resolvedApiFetch([]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      expect(screen.getByText(/No runtime events recorded yet/)).toBeTruthy();
    });
  });
});

// ── API fetch ─────────────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — API fetch", () => {
  it("fetches events for the correct source and project", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto()]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      expect(mockApiFetch).toHaveBeenCalledWith(
        `/api/v1/projects/${PROJECT_ID}/runtime-events?source=src-test&limit=100`,
      );
    });
  });

  it("renders events returned by the API", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Source is up" } }),
      makeDto({ id: 2, type: "SOURCE_ERROR", payload: { detail: "Connection refused" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      expect(screen.getByText("Source is up")).toBeTruthy();
      expect(screen.getByText("Connection refused")).toBeTruthy();
    });
  });
});

// ── type→level/category mapping ───────────────────────────────────────────────

describe("DataSourceDetailEventsTab — type→level mapping", () => {
  it("SOURCE_START maps to info level and connection category", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_START", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("info")).toBeTruthy());
    // Expand to see category
    const button = screen.getByRole("button", { name: /Source started/i });
    await userEvent.click(button);
    expect(screen.getByText("connection")).toBeTruthy();
  });

  it("SOURCE_STALE maps to warning level and connection category", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_STALE", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("warning")).toBeTruthy());
    const button = screen.getByRole("button", { name: /Source went stale/i });
    await userEvent.click(button);
    expect(screen.getByText("connection")).toBeTruthy();
  });

  it("SOURCE_ERROR maps to error level and runtime category", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_ERROR", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("error")).toBeTruthy());
    const button = screen.getByRole("button", { name: /Source error/i });
    await userEvent.click(button);
    expect(screen.getByText("runtime")).toBeTruthy();
  });

  it("ERROR maps to error level and runtime category", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "ERROR", payload: { detail: "Crash" } })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getAllByText("error").length).toBeGreaterThan(0));
    const button = screen.getByRole("button", { name: /Crash/i });
    await userEvent.click(button);
    expect(screen.getByText("runtime")).toBeTruthy();
  });

  it("RUN_COMPLETED maps to info level and runtime category (UI-502)", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "RUN_COMPLETED", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("info")).toBeTruthy());
    const button = screen.getByRole("button", { name: /Run completed/i });
    await userEvent.click(button);
    expect(screen.getByText("runtime")).toBeTruthy();
  });

  it("RUN_STOPPED maps to info level and runtime category (UI-502)", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "RUN_STOPPED", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("info")).toBeTruthy());
    const button = screen.getByRole("button", { name: /Run stopped/i });
    await userEvent.click(button);
    expect(screen.getByText("runtime")).toBeTruthy();
  });

  it("RUN_FAILED maps to error level and runtime category (UI-502)", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "RUN_FAILED", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getAllByText("error").length).toBeGreaterThan(0));
    const button = screen.getByRole("button", { name: /Run failed/i });
    await userEvent.click(button);
    expect(screen.getByText("runtime")).toBeTruthy();
  });

  it("uses payload.detail as message when present", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Custom message" } })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Custom message")).toBeTruthy());
  });

  it("falls back to humanized type when payload.detail is absent", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_RECOVERED", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Source recovered")).toBeTruthy());
  });
});

// ── filters ───────────────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — category filter", () => {
  it("filters events by category", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Connection event" } }),
      makeDto({ id: 2, type: "SOURCE_ERROR", payload: { detail: "Runtime event" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Connection event")).toBeTruthy());
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "connection");
    expect(screen.getByText("Connection event")).toBeTruthy();
    expect(screen.queryByText("Runtime event")).toBeNull();
  });
});

describe("DataSourceDetailEventsTab — level filter", () => {
  it("filters events by level", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Info event" } }),
      makeDto({ id: 2, type: "SOURCE_ERROR", payload: { detail: "Error event" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Info event")).toBeTruthy());
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[1], "error");
    expect(screen.getByText("Error event")).toBeTruthy();
    expect(screen.queryByText("Info event")).toBeNull();
  });
});

describe("DataSourceDetailEventsTab — combined filters", () => {
  it("narrows results when both filters are active", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Connection info" } }),
      makeDto({ id: 2, type: "SOURCE_STALE", payload: { detail: "Connection warning" } }),
      makeDto({ id: 3, type: "SOURCE_ERROR", payload: { detail: "Runtime error" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Connection info")).toBeTruthy());
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "connection");
    await userEvent.selectOptions(selects[1], "warning");
    expect(screen.getByText("Connection warning")).toBeTruthy();
    expect(screen.queryByText("Connection info")).toBeNull();
    expect(screen.queryByText("Runtime error")).toBeNull();
  });

  it("shows no-match empty state when filters produce empty result", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "Started" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Started")).toBeTruthy());
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "recording");
    expect(screen.getByText(/No matching events/)).toBeTruthy();
  });
});

// ── live SSE events ───────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — live SSE events", () => {
  it("prepends a live event matching the source", async () => {
    setupDefaults(
      withLiveEvent({ dataSourceId: "src-test", type: "SOURCE_START", at: "2026-01-01T11:00:00Z" }),
    );
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_STOP", payload: { detail: "Old stop" } })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      expect(screen.getByText("Source started")).toBeTruthy();
      expect(screen.getByText("Old stop")).toBeTruthy();
    });
  });

  it("ignores live events from a different source", async () => {
    setupDefaults(
      withLiveEvent({ dataSourceId: "OTHER-src", type: "SOURCE_START", at: "2026-01-01T11:00:00Z" }),
    );
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_STOP", payload: { detail: "My event" } })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("My event")).toBeTruthy());
    // "Source started" should NOT appear — it was for another source
    expect(screen.queryByText("Source started")).toBeNull();
  });

  it("does not add duplicate live events", async () => {
    const liveEvent: LiveRuntimeEvent = {
      dataSourceId: "src-test",
      type: "SOURCE_START",
      at: "2026-01-01T11:00:00Z",
    };
    // Simulate the same event being in liveEvents twice (re-render with same ref)
    mockUseShellStore.mockImplementation(
      (sel: (s: { currentProjectId: string }) => unknown) =>
        sel({ currentProjectId: PROJECT_ID }),
    );
    const liveResult: LiveRuntimeResult = { sources: [], events: [liveEvent], status: "open" };
    mockUseLiveRuntime.mockReturnValue(liveResult);
    resolvedApiFetch([]);

    const { rerender } = render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.queryByText(/Loading/i)).toBeNull());

    // Re-render with the exact same live events — should not duplicate
    rerender(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => {
      const items = screen.queryAllByText("Source started");
      expect(items.length).toBe(1);
    });
  });

  it("live SOURCE_STALE maps to warning level", async () => {
    setupDefaults(
      withLiveEvent({ dataSourceId: "src-test", type: "SOURCE_STALE", at: "2026-01-01T11:00:00Z" }),
    );
    resolvedApiFetch([]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("warning")).toBeTruthy());
  });

  it("live SOURCE_ERROR maps to error level", async () => {
    setupDefaults(
      withLiveEvent({ dataSourceId: "src-test", type: "SOURCE_ERROR", at: "2026-01-01T11:00:00Z" }),
    );
    resolvedApiFetch([]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("error")).toBeTruthy());
  });

  it("live event uses detail as message when provided", async () => {
    setupDefaults(
      withLiveEvent({
        dataSourceId: "src-test",
        type: "SOURCE_ERROR",
        at: "2026-01-01T11:00:00Z",
        detail: "Live detail message",
      }),
    );
    resolvedApiFetch([]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("Live detail message")).toBeTruthy());
  });
});

// ── expand/collapse ───────────────────────────────────────────────────────────

describe("DataSourceDetailEventsTab — expand/collapse", () => {
  it("detail panel is hidden by default", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_START", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.queryByText(/Loading/i)).toBeNull());
    expect(screen.queryByText("Timestamp")).toBeNull();
    expect(screen.queryByText("Event ID")).toBeNull();
  });

  it("clicking a row expands the detail panel", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_START", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => screen.getByRole("button", { name: /Source started/i }));
    const button = screen.getByRole("button", { name: /Source started/i });
    expect(button.getAttribute("aria-expanded")).toBe("false");
    await userEvent.click(button);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    expect(screen.getByText("Timestamp")).toBeTruthy();
    expect(screen.getByText("Event ID")).toBeTruthy();
  });

  it("clicking the expanded row collapses the detail panel", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 1, type: "SOURCE_START", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => screen.getByRole("button", { name: /Source started/i }));
    const button = screen.getByRole("button", { name: /Source started/i });
    await userEvent.click(button);
    expect(screen.getByText("Timestamp")).toBeTruthy();
    await userEvent.click(button);
    expect(screen.queryByText("Timestamp")).toBeNull();
    expect(button.getAttribute("aria-expanded")).toBe("false");
  });

  it("expanding a second row collapses the first", async () => {
    setupDefaults();
    resolvedApiFetch([
      makeDto({ id: 1, type: "SOURCE_START", payload: { detail: "First event" } }),
      makeDto({ id: 2, type: "SOURCE_STOP", payload: { detail: "Second event" } }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => expect(screen.getByText("First event")).toBeTruthy());
    const buttons = screen.getAllByRole("button");
    await userEvent.click(buttons[0]);
    expect(buttons[0].getAttribute("aria-expanded")).toBe("true");
    await userEvent.click(buttons[1]);
    expect(buttons[1].getAttribute("aria-expanded")).toBe("true");
    expect(buttons[0].getAttribute("aria-expanded")).toBe("false");
  });

  it("detail panel is linked to button via aria-controls", async () => {
    setupDefaults();
    resolvedApiFetch([makeDto({ id: 42, type: "SOURCE_START", payload: {} })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    await waitFor(() => screen.getByRole("button", { name: /Source started/i }));
    const button = screen.getByRole("button", { name: /Source started/i });
    await userEvent.click(button);
    const controlsId = button.getAttribute("aria-controls");
    expect(controlsId).toBe("event-detail-42");
    expect(document.getElementById(controlsId!)).not.toBeNull();
  });
});
