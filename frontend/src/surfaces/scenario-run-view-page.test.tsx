/**
 * Tests for ScenarioRunViewPage (UI-129)
 *
 * Covers:
 * - Renders run summary and step timeline from store steps (not from mock)
 * - SSE events update step status
 * - Stop run → confirmation → success toast + Stop button hides (local UI state)
 * - Not-found panel for unknown scenario
 * - No active run panel when liveRuns is empty for this scenario
 * - Evidence collecting state while running
 */

import { cleanup, render, screen, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) => sel({ push: mockNotifyPush }),
}));

const mockCurrentProjectId = "proj-01";
vi.mock("../shell/shell-store", () => ({
  useShellStore: (sel: (s: object) => unknown) =>
    sel({ currentProjectId: mockCurrentProjectId }),
}));

import { ScenarioRunViewPage } from "./scenario-run-view-page";
import { useScenariosStore } from "../shell/scenarios-store";
import type { ScenarioRow } from "../shell/scenarios-store";
import type { LiveRunState } from "../shell/scenarios-store";
import type { ScenarioStep } from "./scenario-steps";

// ── EventSource mock ──────────────────────────────────────────────────────────

type ESListener = (e: MessageEvent) => void;

class MockEventSource {
  url: string;
  listeners: Record<string, ESListener[]> = {};
  closed = false;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(event: string, handler: ESListener) {
    if (!this.listeners[event]) this.listeners[event] = [];
    this.listeners[event].push(handler);
  }

  close() {
    this.closed = true;
  }

  emit(event: string, data: unknown) {
    const handlers = this.listeners[event] ?? [];
    const msg = { data: JSON.stringify(data) } as MessageEvent;
    for (const h of handlers) h(msg);
  }

  static instances: MockEventSource[] = [];
  static reset() {
    MockEventSource.instances = [];
  }
  static latest(): MockEventSource | undefined {
    return MockEventSource.instances[MockEventSource.instances.length - 1];
  }
}

vi.stubGlobal("EventSource", MockEventSource);

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeRow(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: overrides.description ?? "A test scenario.",
    stepCount: overrides.stepCount ?? 3,
    runState: overrides.runState ?? "Running",
    lastRun: overrides.lastRun ?? { at: null, outcome: null },
    owner: overrides.owner ?? "Olena Ohii",
    lockedBy: overrides.lockedBy ?? null,
    updatedAt: overrides.updatedAt ?? "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

const testSteps: ScenarioStep[] = [
  { id: "st-1", type: "start", label: "Line A telemetry", config: { sourceId: "src-01" }, configured: true },
  { id: "st-2", type: "replay", label: "Calibration recording", config: { sourceId: "src-01", recordingId: "rec-12" }, configured: true },
  { id: "st-3", type: "wait", label: "Hold 30s", config: { seconds: 30 }, configured: true },
];

const defaultLiveRun: LiveRunState = {
  runId: "run-abc",
  evidenceId: "ev-001",
  state: "running",
  stepOrdinals: {},
};

function setStoreRunning(overrides?: Partial<typeof defaultLiveRun>) {
  useScenariosStore.setState({
    scenarios: [
      makeRow({ id: "scn-01", name: "Morning ramp-up", runState: "Running", stepCount: 3 }),
    ],
    steps: { "scn-01": testSteps.map((x) => ({ ...x })) },
    versions: { "scn-01": 1 },
    liveRuns: { "scn-01": { ...defaultLiveRun, ...overrides } },
    isLoading: false,
    error: null,
  });
}

beforeEach(() => {
  MockEventSource.reset();
  setStoreRunning();
  mockNotifyPush.mockClear();
});

afterEach(cleanup);

function renderRun(scenarioId: string) {
  return render(
    <MemoryRouter initialEntries={[`/scenarios/${scenarioId}/run`]}>
      <Routes>
        <Route path="/scenarios/:scenarioId/run" element={<ScenarioRunViewPage />} />
        <Route path="/scenarios/:scenarioId" element={<div>Builder</div>} />
        <Route path="/scenarios" element={<div>Scenarios list</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ScenarioRunViewPage", () => {
  it("renders the scenario name and run ID in the header", () => {
    renderRun("scn-01");
    expect(screen.getByRole("heading", { name: "Morning ramp-up" })).toBeTruthy();
    expect(screen.getByText(/Run ID: run-abc/)).toBeTruthy();
  });

  it("renders step timeline from store steps", () => {
    renderRun("scn-01");
    const timeline = screen.getByLabelText("Step timeline");
    expect(within(timeline).getByText("Line A telemetry")).toBeTruthy();
    expect(within(timeline).getByText("Calibration recording")).toBeTruthy();
    expect(within(timeline).getByText("Hold 30s")).toBeTruthy();
  });

  it("shows required sections", () => {
    renderRun("scn-01");
    expect(screen.getByLabelText("Current step")).toBeTruthy();
    expect(screen.getByLabelText("Step timeline")).toBeTruthy();
    expect(screen.getByLabelText("Sources involved")).toBeTruthy();
    expect(screen.getByLabelText("Evidence")).toBeTruthy();
    expect(screen.getByLabelText("Events")).toBeTruthy();
  });

  it("SSE step-started marks step as active", async () => {
    renderRun("scn-01");
    const es = MockEventSource.latest()!;
    act(() => {
      es.emit("step-started", { ordinal: 1, type: "REPLAY" });
    });
    const timeline = screen.getByLabelText("Step timeline");
    // Step at index 1 (Calibration recording) should now show "active"
    const items = within(timeline).getAllByRole("listitem");
    expect(within(items[1]).getByText("active")).toBeTruthy();
  });

  it("SSE step-completed marks step as done", async () => {
    renderRun("scn-01");
    const es = MockEventSource.latest()!;
    act(() => {
      es.emit("step-started", { ordinal: 0, type: "START" });
    });
    act(() => {
      es.emit("step-completed", { ordinal: 0, type: "START" });
    });
    const timeline = screen.getByLabelText("Step timeline");
    const items = within(timeline).getAllByRole("listitem");
    expect(within(items[0]).getByText("done")).toBeTruthy();
  });

  it("SSE run-finished updates display status to completed", async () => {
    renderRun("scn-01");
    const es = MockEventSource.latest()!;
    act(() => {
      es.emit("run-finished", { state: "COMPLETED" });
    });
    // Status badge should show "completed"
    expect(screen.getByText("completed")).toBeTruthy();
    // Stop button should disappear
    expect(screen.queryByRole("button", { name: "Stop run" })).toBeNull();
  });

  it("stops the run through the confirmation dialog", async () => {
    const user = userEvent.setup();
    renderRun("scn-01");
    await user.click(screen.getByRole("button", { name: "Stop run" }));
    expect(screen.getByText(/Stop "Morning ramp-up"\?/)).toBeTruthy();
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Stop run" }));

    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining("Stopped") }),
    );
    // Stop button disappears once stopped (local UI state).
    expect(screen.queryByRole("button", { name: "Stop run" })).toBeNull();
  });

  it("navigates to the builder via Open in builder", async () => {
    const user = userEvent.setup();
    renderRun("scn-01");
    await user.click(screen.getByRole("button", { name: "Open in builder" }));
    expect(screen.getByText("Builder")).toBeTruthy();
  });

  it("shows a not-found panel for an unknown scenario", () => {
    renderRun("scn-nope");
    expect(screen.getByText("Scenario run not found.")).toBeTruthy();
  });

  it("shows 'No active run' panel when scenario exists but has no liveRun", () => {
    useScenariosStore.setState({
      scenarios: [
        makeRow({ id: "scn-01", name: "Morning ramp-up", runState: "Not running", stepCount: 3 }),
      ],
      steps: { "scn-01": testSteps.map((x) => ({ ...x })) },
      versions: { "scn-01": 1 },
      liveRuns: {},
      isLoading: false,
      error: null,
    });
    renderRun("scn-01");
    expect(screen.getByText("No active run.")).toBeTruthy();
  });

  it("shows evidence collecting state while running", () => {
    renderRun("scn-01");
    const evidence = screen.getByLabelText("Evidence");
    expect(within(evidence).getByText(/Collecting/)).toBeTruthy();
  });

  it("shows evidence available when run is completed and evidenceId exists", () => {
    setStoreRunning({ evidenceId: "ev-001" });
    renderRun("scn-01");
    const es = MockEventSource.latest()!;
    act(() => {
      es.emit("run-finished", { state: "COMPLETED" });
    });
    const evidence = screen.getByLabelText("Evidence");
    expect(within(evidence).getByText("Available")).toBeTruthy();
  });
});
