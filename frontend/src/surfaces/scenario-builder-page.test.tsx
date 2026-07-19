/**
 * Tests for ScenarioBuilderPage (UI-061 builder shell / UI-127 API wiring)
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

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

const mockNotifyPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) => sel({ push: mockNotifyPush }),
}));

vi.mock("../api", () => ({
  apiFetch: vi.fn().mockResolvedValue({ runId: "run-1", status: "RUNNING" }),
  ApiError: class ApiError extends Error {
    constructor(
      public readonly status: number,
      public readonly title: string,
      public readonly detail: string | undefined,
      public readonly type: string | undefined,
    ) {
      super(title);
      this.name = "ApiError";
    }
  },
}));

// Default: lease is held by the current user so existing tests are unaffected.
// Individual tests override this via mockUseEditLease.
const { mockUseEditLease } = vi.hoisted(() => ({
  mockUseEditLease: vi.fn().mockReturnValue({ leaseState: "held", lockedByHolder: null }),
}));
vi.mock("../shell/use-edit-lease", () => ({ useEditLease: mockUseEditLease }));

import { ScenarioBuilderPage } from "./scenario-builder-page";
import { useScenariosStore } from "../shell/scenarios-store";
import type { ScenarioRow } from "../shell/scenarios-store";
import type { ScenarioStep } from "./scenario-steps";

function makeRow(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: overrides.description ?? "A test scenario.",
    stepCount: overrides.stepCount ?? 0,
    runState: overrides.runState ?? "Not running",
    lastRun: overrides.lastRun ?? { at: null, outcome: null },
    owner: overrides.owner ?? "Test User",
    lockedBy: overrides.lockedBy ?? null,
    updatedAt: overrides.updatedAt ?? "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

const testScenarios: ScenarioRow[] = [
  makeRow({ id: "scn-01", name: "Morning ramp-up", stepCount: 3 }),
  makeRow({
    id: "scn-02",
    name: "Packaging fault drill",
    stepCount: 2,
    runState: "Running",
    lockedBy: "Anna Kosol",
  }),
  makeRow({ id: "scn-04", name: "Empty template", stepCount: 0 }),
];

const testSteps: Record<string, ScenarioStep[]> = {
  "scn-01": [
    { id: "st-1", type: "start", label: "Line A telemetry", config: { sourceId: "src-01" }, configured: true },
    { id: "st-2", type: "replay", label: "Calibration recording", config: { sourceId: "src-01", recordingId: "rec-12" }, configured: true },
    { id: "st-3", type: "wait", label: "Hold 30s", config: { seconds: 30 }, configured: true },
  ],
  "scn-02": [
    { id: "st-1", type: "start", label: "Packaging cell stream", config: { sourceId: "src-02" }, configured: true },
    { id: "st-2", type: "fault", label: "Quality fault", config: {}, configured: false },
  ],
  "scn-04": [],
};

function setRole(opts: { accessMode?: "local" | "shared"; sharedRole?: "admin" | "user" } = {}) {
  const { accessMode = "local", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole, currentProjectId: "proj-1" }),
  );
}

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: testScenarios.map((s) => ({ ...s })),
    steps: Object.fromEntries(
      Object.entries(testSteps).map(([k, v]) => [k, v.map((s) => ({ ...s }))]),
    ),
    versions: { "scn-01": 1, "scn-02": 1, "scn-04": 1 },
    isLoading: false,
    error: null,
  });
  mockNotifyPush.mockClear();
  mockNavigate.mockClear();
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

  it("enables Run when ready and shows a success toast on click", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-01");
    const run = screen.getByRole("button", { name: "Run" }) as HTMLButtonElement;
    expect(run.disabled).toBe(false);
    await user.click(run);
    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "success", title: expect.stringContaining("Started") }),
    );
  });

  it("navigates to /scenarios/:id/run after clicking Run (UI-455)", async () => {
    const user = userEvent.setup();
    renderBuilder("scn-01");
    await user.click(screen.getByRole("button", { name: "Run" }));
    expect(mockNavigate).toHaveBeenCalledWith("/scenarios/scn-01/run");
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
    expect(screen.getAllByText(/needs configuration/).length).toBeGreaterThan(0);
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
    // EditLockBanner renders "Anna Kosol is currently editing."
    expect(screen.getByText(/Anna Kosol/)).toBeTruthy();
    expect(screen.getByText(/read-only/)).toBeTruthy();
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
          runState: "Not running" as const,
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

  it("clicking a validation issue focuses the offending step", async () => {
    // Build a scenario with a lifecycle issue: stop a source never started.
    useScenariosStore.setState((s) => ({
      scenarios: [
        ...s.scenarios,
        {
          id: "scn-lc",
          name: "Lifecycle",
          description: "bad stop",
          stepCount: 1,
          runState: "Not running" as const,
          lastRun: { at: null, outcome: null },
          owner: "You",
          lockedBy: null,
          updatedAt: "2026-07-01T00:00:00Z",
        },
      ],
      steps: {
        ...s.steps,
        "scn-lc": [
          { id: "bad", type: "stop" as const, label: "Stop A", config: { sourceId: "src-01" }, configured: true },
        ],
      },
    }));
    const user = userEvent.setup();
    renderBuilder("scn-lc");
    // The issue appears in the summary as a button; clicking selects the step.
    const issueButton = screen.getByRole("button", { name: /not running/ });
    await user.click(issueButton);
    // The step list marks it with an Issue badge (configured but semantically bad).
    expect(screen.getByText("Issue")).toBeTruthy();
  });

  it("shows EditLockBanner and disables editing when useEditLease returns locked-by-other", () => {
    // Override the default lease mock for this test only.
    mockUseEditLease.mockReturnValueOnce({
      leaseState: "locked-by-other",
      lockedByHolder: "carol",
    });
    setRole({ accessMode: "local", sharedRole: "admin" });
    // Use scn-01 which has lockedBy:null in the store — lock comes from the API lease only.
    renderBuilder("scn-01");
    // EditLockBanner renders "carol is currently editing."
    expect(screen.getByText(/carol/)).toBeTruthy();
    expect(screen.getByText(/read-only/)).toBeTruthy();
    // Save button should be absent because canEdit === false
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
  });
});
