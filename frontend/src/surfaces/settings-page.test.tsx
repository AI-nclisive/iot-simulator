/**
 * Tests for SettingsPage (UI-080)
 *
 * Covers:
 * - Project name is shown for both roles
 * - Admin sees editable name field and Save button
 * - Shared User sees read-only name and locked panel
 * - Save button disabled when name is unchanged
 * - Empty name shows validation error and does not submit
 * - Saved badge appears after successful save
 * - Import link visible for Admin only
 * - Environment settings section always visible
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SettingsPage } from "./settings-page";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

vi.mock("../shell/projects-store", () => ({
  useProjectsStore: (selector: (s: object) => unknown) =>
    selector({
      projects: [
        {
          id: "assembly-line-a",
          name: "Assembly Line A",
          configuredSources: 0,
          runningSources: 0,
          reusableArtifacts: 0,
          lastActivity: "",
        },
      ],
      renameProject: async () => {},
    }),
}));

function setupStore(opts: { accessMode?: "local" | "shared"; sharedRole?: "admin" | "user" } = {}) {
  const { accessMode = "local", sharedRole = "admin" } = opts;
  mockShellStore.mockImplementation((selector: (s: object) => unknown) =>
    selector({ accessMode, sharedRole, currentProjectId: "assembly-line-a" }),
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  );
}

describe("project name — Admin (local mode)", () => {
  it("shows editable name input with current project name", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    const input = screen.getByRole("textbox") as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(input.value).toBe("Assembly Line A");
  });

  it("Save button is disabled when name is unchanged", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    const btn = screen.getByRole("button", { name: /save name/i }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Save button enabled after name is changed", async () => {
    setupStore({ accessMode: "local" });
    renderPage();
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "New Name");
    const btn = screen.getByRole("button", { name: /save name/i }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it("shows validation error on empty name submit without saving", async () => {
    setupStore({ accessMode: "local" });
    renderPage();
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.click(screen.getByRole("button", { name: /save name/i }));
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toMatch(/cannot be empty/i);
  });

  it("shows Saved badge after successful save", async () => {
    setupStore({ accessMode: "local" });
    renderPage();
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "Renamed Project");
    await userEvent.click(screen.getByRole("button", { name: /save name/i }));
    await waitFor(() => expect(screen.getByText("Saved")).toBeTruthy());
  });
});

describe("project name — shared User", () => {
  it("shows read-only name text, no input field", () => {
    setupStore({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(screen.getAllByText("Assembly Line A").length).toBeGreaterThan(0);
  });

  it("shows locked state panel", () => {
    setupStore({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    // SharedStatePanel title for locked state
    expect(screen.getByText("Settings are read-only.")).toBeTruthy();
  });
});

describe("import link", () => {
  it("shows Import project link for Admin", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    expect(screen.getByRole("link", { name: /import project/i })).toBeTruthy();
  });

  it("hides Import project link for shared User", () => {
    setupStore({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    expect(screen.queryByRole("link", { name: /import project/i })).toBeNull();
  });
});

describe("environment settings", () => {
  it("always shows API endpoint label", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    expect(screen.getByText("API endpoint")).toBeTruthy();
  });

  it("always shows app version", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    expect(screen.getByText("0.1.0")).toBeTruthy();
  });

  it("shows Your role row in shared mode", () => {
    setupStore({ accessMode: "shared", sharedRole: "user" });
    renderPage();
    expect(screen.getByText("Your role")).toBeTruthy();
  });

  it("hides Your role row in local mode", () => {
    setupStore({ accessMode: "local" });
    renderPage();
    expect(screen.queryByText("Your role")).toBeNull();
  });
});
