/**
 * Tests for DataSourceDetailEventsTab (UI-046)
 *
 * Covers:
 * - Empty state when source has no events
 * - All events shown with no filters active
 * - Category filter shows only matching events
 * - Level filter shows only matching events
 * - Intersection: both filters active narrows events
 * - No-match empty state when filters produce empty result
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RuntimeEvent } from "./mock-source-events";
import type { DataSourceRow } from "./mock-data-sources";
import { DataSourceDetailEventsTab } from "./data-source-detail-events-tab";

const { mockGetEvents } = vi.hoisted(() => ({
  mockGetEvents: vi.fn(() => [] as RuntimeEvent[]),
}));

vi.mock("./mock-source-events", async () => {
  const actual = await vi.importActual<typeof import("./mock-source-events")>("./mock-source-events");
  return { ...actual, getEventsForSource: mockGetEvents };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockSource: DataSourceRow = {
  id: "src-test",
  name: "Test Source",
  protocol: "OPC UA",
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 10,
  status: "Active",
  health: "Healthy",
};

function makeEvent(overrides: Partial<RuntimeEvent> = {}): RuntimeEvent {
  return {
    id: "ev-1",
    level: "info",
    timestamp: "10:00:00",
    message: "Test event",
    category: "connection",
    ...overrides,
  };
}

describe("DataSourceDetailEventsTab — no events", () => {
  it("shows empty state when source has no events", () => {
    mockGetEvents.mockReturnValue([]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    expect(screen.getByText(/No runtime events recorded yet/)).toBeTruthy();
  });
});

describe("DataSourceDetailEventsTab — event list", () => {
  it("shows all events with no filters applied", () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", message: "Connection opened", category: "connection", level: "info" }),
      makeEvent({ id: "ev-2", message: "Warning triggered", category: "runtime", level: "warning" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    expect(screen.getByText("Connection opened")).toBeTruthy();
    expect(screen.getByText("Warning triggered")).toBeTruthy();
  });

  it("shows count of visible vs total events", () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1" }),
      makeEvent({ id: "ev-2" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    expect(screen.getByText(/2 of 2 events/)).toBeTruthy();
  });
});

describe("DataSourceDetailEventsTab — category filter", () => {
  it("filters events by category", async () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", message: "Connection event", category: "connection" }),
      makeEvent({ id: "ev-2", message: "Runtime event", category: "runtime" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "connection");
    expect(screen.getByText("Connection event")).toBeTruthy();
    expect(screen.queryByText("Runtime event")).toBeNull();
  });
});

describe("DataSourceDetailEventsTab — level filter", () => {
  it("filters events by level", async () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", message: "Info event", level: "info" }),
      makeEvent({ id: "ev-2", message: "Error event", level: "error" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[1], "error");
    expect(screen.getByText("Error event")).toBeTruthy();
    expect(screen.queryByText("Info event")).toBeNull();
  });
});

describe("DataSourceDetailEventsTab — combined filters", () => {
  it("narrows results when both filters are active", async () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", message: "Connection info", category: "connection", level: "info" }),
      makeEvent({ id: "ev-2", message: "Connection error", category: "connection", level: "error" }),
      makeEvent({ id: "ev-3", message: "Runtime error", category: "runtime", level: "error" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "connection");
    await userEvent.selectOptions(selects[1], "error");
    expect(screen.getByText("Connection error")).toBeTruthy();
    expect(screen.queryByText("Connection info")).toBeNull();
    expect(screen.queryByText("Runtime error")).toBeNull();
  });

  it("shows no-match empty state when filters produce empty result", async () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", category: "connection", level: "info" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const selects = screen.getAllByRole("combobox");
    await userEvent.selectOptions(selects[0], "recording");
    expect(screen.getByText(/No matching events/)).toBeTruthy();
  });
});

describe("DataSourceDetailEventsTab — expand/collapse", () => {
  it("detail panel is hidden by default", () => {
    mockGetEvents.mockReturnValue([makeEvent({ id: "ev-1", message: "Test event" })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    expect(screen.queryByText("Timestamp")).toBeNull();
    expect(screen.queryByText("Event ID")).toBeNull();
  });

  it("clicking a row expands the detail panel", async () => {
    mockGetEvents.mockReturnValue([makeEvent({ id: "ev-1", message: "Test event", timestamp: "10:00:00" })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const button = screen.getByRole("button", { name: /Test event/i });
    expect(button.getAttribute("aria-expanded")).toBe("false");
    await userEvent.click(button);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    expect(screen.getByText("Timestamp")).toBeTruthy();
    expect(screen.getByText("Event ID")).toBeTruthy();
  });

  it("clicking the expanded row collapses the detail panel", async () => {
    mockGetEvents.mockReturnValue([makeEvent({ id: "ev-1", message: "Test event" })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const button = screen.getByRole("button", { name: /Test event/i });
    await userEvent.click(button);
    expect(screen.getByText("Timestamp")).toBeTruthy();
    await userEvent.click(button);
    expect(screen.queryByText("Timestamp")).toBeNull();
    expect(button.getAttribute("aria-expanded")).toBe("false");
  });

  it("expanding a second row collapses the first", async () => {
    mockGetEvents.mockReturnValue([
      makeEvent({ id: "ev-1", message: "First event" }),
      makeEvent({ id: "ev-2", message: "Second event" }),
    ]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const buttons = screen.getAllByRole("button");
    await userEvent.click(buttons[0]);
    expect(buttons[0].getAttribute("aria-expanded")).toBe("true");
    await userEvent.click(buttons[1]);
    expect(buttons[1].getAttribute("aria-expanded")).toBe("true");
    expect(buttons[0].getAttribute("aria-expanded")).toBe("false");
  });

  it("detail panel is linked to button via aria-controls", async () => {
    mockGetEvents.mockReturnValue([makeEvent({ id: "ev-42", message: "Test event" })]);
    render(<DataSourceDetailEventsTab source={mockSource} />);
    const button = screen.getByRole("button", { name: /Test event/i });
    await userEvent.click(button);
    const controlsId = button.getAttribute("aria-controls");
    expect(controlsId).toBe("event-detail-ev-42");
    expect(document.getElementById(controlsId!)).not.toBeNull();
  });
});
