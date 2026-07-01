/**
 * Tests for ScenarioRunViewPage (UI-065)
 *
 * Covers:
 * - Renders run summary, current step, timeline, sources, faults, clients,
 *   events, and evidence state
 * - Stop run → confirmation → stopScenario called + success toast + status flips
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
import { scenarioRows, stepsByScenario } from "./mock-scenarios";

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: scenarioRows.map((s) => ({ ...s })),
    steps: Object.fromEntries(
      Object.entries(stepsByScenario).map(([k, v]) => [k, v.map((x) => ({ ...x }))]),
    ),
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

    expect(useScenariosStore.getState().scenarios.find((s) => s.id === "scn-01")?.runState).toBe(
      "Stopped",
    );
    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining("Stopped") }),
    );
    // Stop button disappears once stopped.
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
