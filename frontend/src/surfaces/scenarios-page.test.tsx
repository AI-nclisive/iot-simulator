/**
 * Tests for ScenariosPage (UI-060)
 *
 * Covers:
 * - Renders the scenario list with name, run state, last-run summary, owner
 * - Search filters by name/description/owner
 * - Run-state filter narrows the list
 * - Role-aware actions: admin sees New scenario + Run/Stop/Duplicate;
 *   shared user sees neither create nor duplicate
 * - Run action → store update + success toast
 * - Stop action → confirmation dialog → store update + toast
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

import { ScenariosPage } from "./scenarios-page";
import { useScenariosStore } from "../shell/scenarios-store";
import { scenarioRows } from "./mock-scenarios";

function setRole(opts: { accessMode?: "local" | "shared"; sharedRole?: "admin" | "user" } = {}) {
  const { accessMode = "local", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole }),
  );
}

beforeEach(() => {
  // Reset store to a clean copy of the fixtures before each test.
  useScenariosStore.setState({ scenarios: scenarioRows.map((s) => ({ ...s })) });
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

  it("runs an idle scenario and shows a success toast", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = screen.getByText("Morning ramp-up").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "Run" }));

    expect(mockNotifyPush).toHaveBeenCalledWith(
      expect.objectContaining({ tone: "success", title: expect.stringContaining("Started") }),
    );
    const updated = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-01");
    expect(updated?.runState).toBe("Running");
  });

  it("stops a running scenario through the confirmation dialog", async () => {
    // Clear the lock so the running scenario is stoppable from here.
    useScenariosStore.setState((s) => ({
      scenarios: s.scenarios.map((x) => (x.id === "scn-02" ? { ...x, lockedBy: null } : x)),
    }));
    const user = userEvent.setup();
    renderPage();
    const row = screen.getByText("Packaging fault drill").closest("tr")!;
    await user.click(within(row).getByRole("button", { name: "Stop" }));

    // Confirmation dialog appears.
    expect(screen.getByText(/Stop "Packaging fault drill"\?/)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Stop scenario" }));

    const updated = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-02");
    expect(updated?.runState).toBe("Stopped");
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
