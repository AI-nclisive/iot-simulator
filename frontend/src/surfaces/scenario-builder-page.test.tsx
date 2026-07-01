/**
 * Tests for ScenarioBuilderPage (UI-061 builder shell)
 *
 * Covers:
 * - Renders step list + details panel for a scenario
 * - Selecting a step shows its details
 * - Validation summary: invalid (unconfigured step) vs ready
 * - Run disabled while invalid, enabled when ready
 * - Admin can add a step; reorder; remove via confirmation
 * - Shared user sees inspect-only (no add/remove/save), can still Run a ready one
 * - Locked-by-other scenario is read-only with a notice
 * - Unknown scenario id → not-found panel
 */

import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));
vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) => sel({ push: mockNotifyPush }),
}));

import { ScenarioBuilderPage } from "./scenario-builder-page";
import { useScenariosStore } from "../shell/scenarios-store";
import { scenarioRows, stepsByScenario } from "./mock-scenarios";

function setRole(opts: { accessMode?: "local" | "shared"; sharedRole?: "admin" | "user" } = {}) {
  const { accessMode = "local", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole }),
  );
}

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: scenarioRows.map((s) => ({ ...s })),
    steps: Object.fromEntries(
      Object.entries(stepsByScenario).map(([k, v]) => [k, v.map((s) => ({ ...s }))]),
    ),
  });
  mockNotifyPush.mockClear();
  setRole();
});

afterEach(cleanup);

function renderBuilder(scenarioId: string) {
  return render(
    <MemoryRouter initialEntries={[`/scenarios/${scenarioId}`]}>
      <Routes>
        <Route path="/scenarios/:scenarioId" element={<ScenarioBuilderPage />} />
        <Route path="/scenarios" element={<div>Scenarios list</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ScenarioBuilderPage", () => {
  it("renders the step list and details panel", () => {
    renderBuilder("scn-01");
    expect(screen.getByRole("heading", { name: "Morning ramp-up" })).toBeTruthy();
    expect(screen.getByLabelText("Step list")).toBeTruthy();
    expect(screen.getByLabelText("Step details")).toBeTruthy();
    // scn-01 has 3 steps, all configured → ready
    expect(screen.getByText("Ready to run")).toBeTruthy();
  });

  it("shows a validation issue and Invalid status for an unconfigured step", () => {
    // Clear scn-02's lock so it isn't read-only/Locked; we want the Invalid path.
    useScenariosStore.setState((s) => ({
      scenarios: s.scenarios.map((x) => (x.id === "scn-02" ? { ...x, lockedBy: null } : x)),
    }));
    renderBuilder("scn-02"); // has a fault step with configured:false
    expect(screen.getByText("Invalid")).toBeTruthy();
    expect(screen.getByText(/needs configuration/)).toBeTruthy();
  });

  it("disables Run while invalid", () => {
    useScenariosStore.setState((s) => ({
      scenarios: s.scenarios.map((x) => (x.id === "scn-02" ? { ...x, lockedBy: null } : x)),
    }));
    renderBuilder("scn-02");
    const run = screen.getByRole("button", { name: "Run" }) as HTMLButtonElement;
    expect(run.disabled).toBe(true);
  });

  it("enables Run when ready and runs the scenario", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-01");
    const run = screen.getByRole("button", { name: "Run" }) as HTMLButtonElement;
    expect(run.disabled).toBe(false);
    await user.click(run);
    expect(useScenariosStore.getState().scenarios.find((s) => s.id === "scn-01")?.runState).toBe(
      "Running",
    );
  });

  it("admin can add a step", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-04"); // empty
    expect(screen.getByText(/No steps yet/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "+ Wait" }));
    expect(useScenariosStore.getState().steps["scn-04"]).toHaveLength(1);
  });

  it("a freshly-added wait step is NOT auto-configured (needs its duration)", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-04"); // empty
    await user.click(screen.getByRole("button", { name: "+ Wait" }));
    const added = useScenariosStore.getState().steps["scn-04"][0];
    // Regression: addStep must not hard-code wait/marker as configured now that
    // they have required fields (seconds / note).
    expect(added.configured).toBe(false);
    // And the scenario must not claim it is runnable.
    expect(screen.queryByText("Ready to run")).toBeNull();
    expect(screen.getByText(/needs configuration/)).toBeTruthy();
  });

  it("admin can remove a step through the confirmation dialog", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-01");
    const list = screen.getByLabelText("Step list");
    const firstRemove = within(list).getAllByRole("button", { name: "Remove step" })[0];
    await user.click(firstRemove);
    expect(screen.getByText("Remove this step?")).toBeTruthy();
    // The dialog's confirm button lives in the dialog, not the step list.
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Remove step" }));
    expect(useScenariosStore.getState().steps["scn-01"]).toHaveLength(2);
  });

  it("reorders a step down", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-01");
    const before = useScenariosStore.getState().steps["scn-01"].map((s) => s.id);
    const list = screen.getByLabelText("Step list");
    await user.click(within(list).getAllByRole("button", { name: "Move step down" })[0]);
    const after = useScenariosStore.getState().steps["scn-01"].map((s) => s.id);
    expect(after[0]).toBe(before[1]);
    expect(after[1]).toBe(before[0]);
  });

  it("shared user sees inspect-only (no add/remove/save) but can run a ready scenario", () => {
    setRole({ accessMode: "shared", sharedRole: "user" });
    renderBuilder("scn-01");
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
    expect(screen.queryByText("Add step")).toBeNull();
    expect(screen.queryAllByRole("button", { name: "Remove step" })).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Run" })).toBeTruthy();
  });

  it("treats a scenario locked by another editor as read-only", () => {
    // scn-02 is locked by Anna Kosol.
    setRole({ accessMode: "shared", sharedRole: "admin" });
    renderBuilder("scn-02");
    expect(screen.getByText("Locked")).toBeTruthy();
    expect(screen.getByText(/being edited by Anna Kosol/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
  });

  it("treats a wait/marker-only scenario as runnable but warns it produces no data", () => {
    // Build a scenario whose only step is a configured wait — no actionable step.
    useScenariosStore.setState((s) => ({
      scenarios: [
        ...s.scenarios,
        {
          id: "scn-wait",
          name: "Wait only",
          description: "Only waits.",
          stepCount: 1,
          runState: "Idle" as const,
          lastRun: { at: null, outcome: null },
          owner: "You",
          lockedBy: null,
          updatedAt: "2026-06-30T00:00:00Z",
        },
      ],
      steps: {
        ...s.steps,
        "scn-wait": [
          { id: "w1", type: "wait" as const, label: "Hold", config: { seconds: 5 }, configured: true },
        ],
      },
    }));
    renderBuilder("scn-wait");
    // Runnable (warning does not block), but the no-data warning is shown.
    expect(screen.getByText("Ready to run")).toBeTruthy();
    const run = screen.getByRole("button", { name: "Run" }) as HTMLButtonElement;
    expect(run.disabled).toBe(false);
    expect(screen.getByText(/will not produce data/)).toBeTruthy();
  });

  it("shows a not-found panel for an unknown scenario", () => {
    renderBuilder("scn-does-not-exist");
    expect(screen.getByText("Scenario not found.")).toBeTruthy();
  });
});
