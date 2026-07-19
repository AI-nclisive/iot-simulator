/**
 * Tests for RuntimeDashboardPanel (UI-025, rewired to live API by UI-111)
 *
 * Covers:
 * - runStateTone: running → warning, failed → danger, queued/stopped/completed → neutral
 * - runProcessLabel: correct labels for each state
 * - Failed run shows error banner
 * - Empty state when no active runs
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ActiveRunResponse } from "../shell/use-active-runs";

const { mockUseActiveRuns } = vi.hoisted(() => ({
  mockUseActiveRuns: vi.fn(),
}));

vi.mock("../shell/use-active-runs", () => ({
  useActiveRuns: mockUseActiveRuns,
}));

// Panel reads the current project id from the shell store (UI-098).
vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: { currentProjectId: string }) => unknown) =>
    selector({ currentProjectId: "p1" }),
}));

// jsdom has no EventSource; the live runtime hook needs one. A minimal stub that
// never opens is enough — these tests assert the run-process list, not live state.
class StubEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  addEventListener() {}
  close() {}
}
vi.stubGlobal("EventSource", StubEventSource as unknown as typeof EventSource);

import { RuntimeDashboardPanel } from "./runtime-dashboard-panel";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function baseRun(overrides: Partial<ActiveRunResponse> = {}): ActiveRunResponse {
  return {
    id: "run-1",
    label: "Test run",
    initiator: "user",
    startedAt: "2026-01-01T10:00:00Z",
    runState: "running",
    processType: "Replay",
    relatedSourceId: "src-1",
    relatedLabel: "Source A",
    ...overrides,
  };
}

function setActiveRuns(runs: ActiveRunResponse[], overrides: { isLoading?: boolean; error?: string | null } = {}) {
  mockUseActiveRuns.mockReturnValue({
    runs,
    isLoading: overrides.isLoading ?? false,
    error: overrides.error ?? null,
  });
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
    setActiveRuns([]);
    renderPanel();
    expect(screen.getByText(/No active runs/)).toBeTruthy();
  });
});

describe("RuntimeDashboardPanel — run states", () => {
  it("shows Running label for running state", () => {
    setActiveRuns([baseRun({ runState: "running" })]);
    renderPanel();
    expect(screen.getByText("Replay in progress")).toBeTruthy();
  });

  it("shows Failed label for failed state", () => {
    setActiveRuns([baseRun({ runState: "failed" })]);
    renderPanel();
    expect(screen.getByText("Replay failed")).toBeTruthy();
  });

  it("shows Queued label for queued state", () => {
    setActiveRuns([baseRun({ runState: "queued" })]);
    renderPanel();
    expect(screen.getByText("Replay waiting")).toBeTruthy();
  });

  it("shows Completed label for completed state", () => {
    setActiveRuns([baseRun({ runState: "completed" })]);
    renderPanel();
    expect(screen.getByText("Replay completed")).toBeTruthy();
  });

  it("shows Stopped label for stopped state", () => {
    setActiveRuns([baseRun({ runState: "stopped" })]);
    renderPanel();
    expect(screen.getByText("Replay stopped")).toBeTruthy();
  });
});

describe("RuntimeDashboardPanel — failed run", () => {
  it("shows error banner on failed run", () => {
    setActiveRuns([baseRun({ runState: "failed" })]);
    renderPanel();
    expect(screen.getByText(/This run failed/)).toBeTruthy();
  });
});
