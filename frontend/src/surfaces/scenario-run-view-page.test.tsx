/**
 * Tests for ScenarioRunViewPage (UI-065)
 *
 * Covers:
 * - Renders run summary, current step, timeline, sources, faults, clients,
 *   events, and evidence state
 * - Stop run → confirmation → success toast + Stop button hides (local UI state)
 * - Not-found panel for unknown scenario
 * - Open-in-builder navigation
 */

import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) => sel({ push: mockNotifyPush }),
}));

import { ScenarioRunViewPage } from "./scenario-run-view-page";
import { useScenariosStore } from "../shell/scenarios-store";
import type { ScenarioRow } from "../shell/scenarios-store";
import type { ScenarioStep } from "./scenario-steps";

function makeRow(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: overrides.description ?? "A test scenario.",
    stepCount: overrides.stepCount ?? 3,
    runState: overrides.runState ?? "Not running",
    lastRun: overrides.lastRun ?? { at: null, outcome: null },
    owner: overrides.owner ?? "Olena Ohii",
    lockedBy: overrides.lockedBy ?? null,
    updatedAt: overrides.updatedAt ?? "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

const testSteps: Record<string, ScenarioStep[]> = {
  "scn-01": [
    { id: "st-1", type: "start", label: "Line A telemetry", config: { sourceId: "src-01" }, configured: true },
    { id: "st-2", type: "replay", label: "Calibration recording", config: { sourceId: "src-01", recordingId: "rec-12" }, configured: true },
    { id: "st-3", type: "wait", label: "Hold 30s", config: { seconds: 30 }, configured: true },
  ],
};

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: [
      makeRow({ id: "scn-01", name: "Morning ramp-up", runState: "Running", stepCount: 3 }),
    ],
    steps: Object.fromEntries(
      Object.entries(testSteps).map(([k, v]) => [k, v.map((x) => ({ ...x }))]),
    ),
    versions: { "scn-01": 1 },
    isLoading: false,
    error: null,
  });
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
        <Route path="/data-sources/:sourceId" element={<div>Source detail</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ScenarioRunViewPage", () => {
  it("renders all required run sections", () => {
    renderRun("scn-01");
    expect(screen.getByRole("heading", { name: "Morning ramp-up" })).toBeTruthy();
    expect(screen.getByLabelText("Current step")).toBeTruthy();
    expect(screen.getByLabelText("Step timeline")).toBeTruthy();
    expect(screen.getByLabelText("Sources involved")).toBeTruthy();
    expect(screen.getByLabelText("Faults")).toBeTruthy();
    expect(screen.getByLabelText("Clients")).toBeTruthy();
    expect(screen.getByLabelText("Evidence")).toBeTruthy();
    expect(screen.getByLabelText("Events")).toBeTruthy();
  });

  it("shows the run initiator and current step", () => {
    renderRun("scn-01");
    expect(screen.getByText(/Started by Olena Ohii/)).toBeTruthy();
    const current = screen.getByLabelText("Current step");
    expect(within(current).getByText(/Replay calibration/)).toBeTruthy();
  });

  it("stops the run through the confirmation dialog", async () => {
    const user = userEvent.setup();
    renderRun("scn-01");
    await user.click(screen.getByRole("button", { name: "Stop run" }));
    expect(screen.getByText(/Stop "Morning ramp-up"\?/)).toBeTruthy();
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Stop run" }));

    // stopScenario is a local-UI no-op until IS-141+UI-129;
    // the page manages stopped state locally via stoppedLocally flag.
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

  it("navigates to the correct data-source route when a source is clicked", async () => {
    const user = userEvent.setup();
    renderRun("scn-01");
    const sources = screen.getByLabelText("Sources involved");
    await user.click(within(sources).getByRole("button", { name: "Line A telemetry" }));
    expect(screen.getByText("Source detail")).toBeTruthy();
  });

  it("shows a not-found panel for an unknown scenario", () => {
    renderRun("scn-nope");
    expect(screen.getByText("Scenario run not found.")).toBeTruthy();
  });

  it("shows evidence collecting state", () => {
    renderRun("scn-01");
    const evidence = screen.getByLabelText("Evidence");
    expect(within(evidence).getByText(/Collecting/)).toBeTruthy();
  });
});
