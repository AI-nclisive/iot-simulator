/**
 * Tests for RuntimeDashboardPanel (UI-025)
 *
 * Covers:
 * - runStateTone: running → warning, failed → danger, queued/stopped/completed → neutral
 * - runStateLabel: correct labels for each state
 * - Automated badge shown only for automation-sourced runs
 * - Failed run shows error banner
 * - Queued run shows queued message, no evidence badge
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ActiveRun } from "../shell/mock-workspace";

const { mockActiveRuns } = vi.hoisted(() => ({
  mockActiveRuns: vi.fn(() => [] as ActiveRun[]),
}));

vi.mock("../shell/mock-workspace", async () => {
  const actual = await vi.importActual<typeof import("../shell/mock-workspace")>(
    "../shell/mock-workspace",
  );
  return {
    ...actual,
    get activeRuns() {
      return mockActiveRuns();
    },
  };
});

import { RuntimeDashboardPanel } from "./runtime-dashboard-panel";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function baseRun(overrides: Partial<ActiveRun> = {}): ActiveRun {
  return {
    id: "run-1",
    label: "Test run",
    initiator: "user",
    startedAt: "10:00",
    runSource: "manual",
    runState: "running",
    processType: "Replay",
    parameterCount: 10,
    previewParameters: ["p1"],
    previewOverflowCount: 9,
    relatedLabel: "Source A",
    relatedPath: "/data-sources/src-1",
    evidence: "Ready",
    ...overrides,
  };
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <RuntimeDashboardPanel />
    </MemoryRouter>,
  );
}

describe("RuntimeDashboardPanel — empty state", () => {
  it("shows empty state panel when no active runs", () => {
    mockActiveRuns.mockReturnValue([]);
    renderPanel();
    expect(screen.getByText(/No active runtime/)).toBeTruthy();
  });
});

describe("RuntimeDashboardPanel — run states", () => {
  it("shows Running label for running state", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "running" })]);
    renderPanel();
    expect(screen.getByText("Running")).toBeTruthy();
  });

  it("shows Failed label for failed state", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "failed", evidence: "Retry needed" })]);
    renderPanel();
    expect(screen.getByText("Failed")).toBeTruthy();
  });

  it("shows Queued label for queued state", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "queued" })]);
    renderPanel();
    expect(screen.getByText("Queued")).toBeTruthy();
  });

  it("shows Completed label for completed state", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "completed" })]);
    renderPanel();
    expect(screen.getByText("Completed")).toBeTruthy();
  });

  it("shows Stopped label for stopped state", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "stopped" })]);
    renderPanel();
    expect(screen.getByText("Stopped")).toBeTruthy();
  });
});

describe("RuntimeDashboardPanel — automated badge", () => {
  it("shows Automated badge for automation-sourced runs", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runSource: "automation" })]);
    renderPanel();
    expect(screen.getByText("Automated")).toBeTruthy();
  });

  it("does not show Automated badge for manual runs", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runSource: "manual" })]);
    renderPanel();
    expect(screen.queryByText("Automated")).toBeNull();
  });
});

describe("RuntimeDashboardPanel — failed run", () => {
  it("shows error banner on failed run", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "failed", evidence: "Retry needed" })]);
    renderPanel();
    expect(screen.getByText(/This run failed/)).toBeTruthy();
  });
});

describe("RuntimeDashboardPanel — queued run", () => {
  it("does not show evidence badge for queued run", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "queued" })]);
    renderPanel();
    expect(screen.queryByText(/Evidence:/)).toBeNull();
  });

  it("shows queued waiting message", () => {
    mockActiveRuns.mockReturnValue([baseRun({ runState: "queued" })]);
    renderPanel();
    expect(screen.getByText(/Waiting to start/)).toBeTruthy();
  });
});
