/**
 * Tests for ScenariosPage (UI-060 / UI-127)
 *
 * Covers:
 * - Renders the scenario list with name, run state, last-run summary, owner
 * - Search filters by name/description/owner
 * - Run-state filter narrows the list
 * - Role-aware actions: admin sees New scenario + Run/Stop/Duplicate;
 *   shared user sees neither create nor duplicate
 * - Run action → success toast (run/stop are fire-and-forget until UI-129)
 * - Stop action → confirmation dialog → toast
 * - Scenario locked by another editor hides Run/Stop
 * - Empty/no-results states
 */

import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));
vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

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

import { apiFetch } from "../api";
import { ScenariosPage } from "./scenarios-page";
import { useScenariosStore } from "../shell/scenarios-store";
import type { ScenarioRow } from "../shell/scenarios-store";

function makeRow(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: overrides.description ?? "A test scenario.",
    stepCount: overrides.stepCount ?? 3,
    runState: overrides.runState ?? "Not running",
    lastRun: overrides.lastRun ?? { at: "2026-06-29T07:14:00Z", outcome: "Completed" },
    owner: overrides.owner ?? "Olena Ohii",
    lockedBy: overrides.lockedBy ?? null,
    updatedAt: overrides.updatedAt ?? "2026-06-29T07:20:00Z",
    ...overrides,
  };
}

const testScenarios: ScenarioRow[] = [
  makeRow({ id: "scn-01", name: "Morning ramp-up", runState: "Not running" }),
  makeRow({
    id: "scn-02",
    name: "Packaging fault drill",
    description: "Injects a quality fault mid-replay to exercise alerting.",
    runState: "Running",
    lastRun: { at: "2026-06-30T09:02:00Z", outcome: null },
    owner: "Anna Kosol",
    lockedBy: "Anna Kosol",
  }),
  makeRow({ id: "scn-03", name: "Overnight soak", runState: "Failed", owner: "Olena Ohii" }),
  makeRow({
    id: "scn-04",
    name: "Empty template",
    description: "Starting point for a new packaging-line scenario.",
    runState: "Not running",
    lastRun: { at: null, outcome: null },
    owner: "Nikolai Pak",
    stepCount: 0,
  }),
];

function setRole(opts: { accessMode?: "local" | "shared"; sharedRole?: "admin" | "user" } = {}) {
  const { accessMode = "local", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole, currentProjectId: "proj-1" }),
  );
}

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: testScenarios.map((s) => ({ ...s })),
    steps: {},
    versions: {},
    isLoading: false,
    error: null,
  });
  mockNotifyPush.mockClear();
  setRole();
});

afterEach(cleanup);

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/scenarios"]}>
      <Routes>
        <Route path="/scenarios" element={<ScenariosPage />} />
        <Route path="/scenarios/:scenarioId/run" element={<div>Run view</div>} />
        <Route path="/scenarios/:scenarioId" element={<div>Builder</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ScenariosPage", () => {
  it("renders the scenario list with key columns", () => {
    renderPage();
    expect(screen.getByText("Morning ramp-up")).toBeTruthy();
    expect(screen.getByText("Packaging fault drill")).toBeTruthy();
    // last-run summary for the never-run template
    expect(screen.getByText(/Never run/)).toBeTruthy();
  });

  it("filters by search across name/owner", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByPlaceholderText("Search scenarios"), "packaging");
    expect(screen.getByText("Packaging fault drill")).toBeTruthy();
    expect(screen.queryByText("Morning ramp-up")).toBeNull();
  });

  it("admin sees the New scenario action", () => {
    setRole({ accessMode: "local" });
    renderPage();
    expect(screen.getByRole("button", { name: "New scenario" })).toBeTruthy();
  });

  it("shared user does not see create or duplicate actions", async () => {
    setRole({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    expect(screen.queryByRole("button", { name: "New scenario" })).toBeNull();

    // Row actions render as direct buttons; Duplicate must be absent for a user.
    const row = screen.getByText("Morning ramp-up").closest("tr")!;
    expect(within(row).queryByRole("button", { name: "Duplicate" })).toBeNull();
    expect(within(row).getByRole("button", { name: "Open" })).toBeTruthy();
  });

  it("runs an idle scenario, shows a success toast, and navigates to the run view", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = screen.getByText("Morning ramp-up").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "Run" }));

    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "success", title: expect.stringContaining("Started") }),
    );
    expect(screen.getByText("Run view")).toBeTruthy();
  });

  it("does not navigate when runScenario returns null (API error)", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new Error("network error"));
    const user = userEvent.setup();
    renderPage();
    const row = screen.getByText("Morning ramp-up").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "Run" }));

    expect(mockNotifyPush).not.toHaveBeenCalled();
    expect(screen.queryByText("Run view")).toBeNull();
  });

  it("stops a running scenario through the confirmation dialog and shows a toast", async () => {
    // Clear the lock so the running scenario is stoppable, and seed liveRuns
    // so the stop guard finds a runId to pass to the API.
    useScenariosStore.setState((s) => ({
      scenarios: s.scenarios.map((x) => (x.id === "scn-02" ? { ...x, lockedBy: null } : x)),
      liveRuns: { "scn-02": { runId: "run-99", evidenceId: null, state: "running", stepOrdinals: {} } },
    }));
    const user = userEvent.setup();
    renderPage();
    const row = screen.getByText("Packaging fault drill").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "Stop" }));

    // Confirmation dialog appears.
    expect(screen.getByText(/Stop "Packaging fault drill"\?/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Stop scenario" }));

    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining("Stopped") }),
    );
  });

  it("hides Run/Stop for a scenario locked by another editor but still offers View run", async () => {
    // scn-02 is Running and locked by "Anna Kosol"; the current shared admin
    // is not that editor, so Stop is hidden. View run is available to all users.
    setRole({ accessMode: "shared", sharedRole: "admin" });
    renderPage();
    const row = screen.getByText("Packaging fault drill").closest("tr")!;
    expect(within(row).queryByRole("button", { name: "Stop" })).toBeNull();
    expect(within(row).getByRole("button", { name: "Open" })).toBeTruthy();
    expect(within(row).getByRole("button", { name: "View run" })).toBeTruthy();
  });

  it("navigates to the run view via View run on a running scenario", async () => {
    const user = userEvent.setup();
    renderPage();
    // scn-02 (Packaging fault drill) is Running in the fixtures.
    const row = screen.getByText("Packaging fault drill").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "View run" }));
    expect(screen.getByText("Run view")).toBeTruthy();
  });

  it("does not offer View run for a non-running scenario", () => {
    renderPage();
    // scn-01 (Morning ramp-up) is Idle.
    const row = screen.getByText("Morning ramp-up").closest("tr")!;
    expect(within(row).queryByRole("button", { name: "View run" })).toBeNull();
  });

  it("shows a no-results state when the filter matches nothing", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByPlaceholderText("Search scenarios"), "zzz-no-match");
    expect(screen.getByText("No matching scenarios.")).toBeTruthy();
  });
});
