/**
 * Tests for ImportProjectDialog inside ProjectEntryPage (UI-012)
 *
 * Covers:
 * - Import button is present for admin users
 * - Dialog opens when "Import project" is clicked
 * - Compatible filename enables the Import button
 * - Filename containing "old" shows incompatible message and keeps Import disabled
 * - Clicking Import while in ready+compatible state transitions to "Importing…"
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProjectEntryPage } from "./project-entry-page";

const { mockShellStore, mockProjectsStore, mockNavigate, mockPush } = vi.hoisted(() => ({
  mockShellStore: vi.fn(),
  mockProjectsStore: vi.fn(),
  mockNavigate: vi.fn(),
  mockPush: vi.fn(),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));
vi.mock("../shell/projects-store", () => ({ useProjectsStore: mockProjectsStore }));
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: mockPush }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

type AdminOverrides = {
  projects?: unknown[];
  createProject?: (...args: unknown[]) => unknown;
  setCurrentProjectId?: (...args: unknown[]) => unknown;
};

function setupAdmin(overrides: AdminOverrides = {}) {
  mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      accessMode: "local",
      sharedRole: "admin",
      setCurrentProjectId: overrides.setCurrentProjectId ?? vi.fn(),
    }),
  );
  mockProjectsStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      projects: overrides.projects ?? [],
      archivedProjects: [],
      isLoading: false,
      error: null,
      loadProjects: vi.fn(),
      createProject: overrides.createProject ?? vi.fn(),
      renameProject: vi.fn(),
      duplicateProject: vi.fn(),
      archiveProject: vi.fn(),
      deleteProject: vi.fn(),
    }),
  );
}

const sampleProject = {
  id: "p1",
  name: "Existing line",
  configuredSources: 0,
  runningSources: 0,
  reusableArtifacts: 0,
  lastActivity: "2026-01-01T00:00:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <ProjectEntryPage />
    </MemoryRouter>,
  );
}

describe("ProjectEntryPage — import button", () => {
  it("shows Import project button for admin", () => {
    setupAdmin();
    renderPage();
    expect(screen.getByRole("button", { name: "Import project" })).toBeTruthy();
  });
});

describe("ImportProjectDialog", () => {
  it("opens dialog when Import project is clicked", async () => {
    setupAdmin();
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Import project" }));
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Import project" })).toBeTruthy();
  });

  it("enables Import button when a compatible filename is entered", async () => {
    setupAdmin();
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Import project" }));
    await userEvent.type(screen.getByRole("textbox"), "factory-a.iotsim");
    expect(screen.getByText("Compatible")).toBeTruthy();
    const importBtn = screen.getAllByRole("button").find(
      (b) => (b as HTMLButtonElement).textContent === "Import",
    ) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(false);
  });

  it("shows Incompatible message and keeps Import disabled for filename with 'old'", async () => {
    setupAdmin();
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Import project" }));
    await userEvent.type(screen.getByRole("textbox"), "factory-old.iotsim");
    expect(screen.getByText(/Incompatible/)).toBeTruthy();
    const importBtn = screen.getAllByRole("button").find(
      (b) => (b as HTMLButtonElement).textContent === "Import",
    ) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(true);
  });

  it("transitions to Importing… after clicking Import", async () => {
    setupAdmin();
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Import project" }));
    await userEvent.type(screen.getByRole("textbox"), "factory-a.iotsim");
    const importBtn = screen.getAllByRole("button").find(
      (b) => (b as HTMLButtonElement).textContent === "Import",
    )!;
    await userEvent.click(importBtn);
    expect(screen.getByText("Importing…")).toBeTruthy();
  });
});

describe("CreateProjectDialog (UI-110)", () => {
  it("opens the dialog when Create project is clicked", async () => {
    setupAdmin({ projects: [sampleProject] });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Create project" })).toBeTruthy();
  });

  it("keeps Create disabled until a non-empty name is entered", async () => {
    setupAdmin({ projects: [sampleProject] });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    const createBtn = screen.getByRole("button", { name: "Create" }) as HTMLButtonElement;
    expect(createBtn.disabled).toBe(true);
    await userEvent.type(screen.getByLabelText("Project name"), "   ");
    expect(createBtn.disabled).toBe(true);
    await userEvent.type(screen.getByLabelText("Project name"), "Line A");
    expect(createBtn.disabled).toBe(false);
  });

  it("calls createProject and opens the new project on success", async () => {
    const created = { ...sampleProject, id: "new-1", name: "Line A" };
    const createProject = vi.fn().mockResolvedValue(created);
    const setCurrentProjectId = vi.fn();
    setupAdmin({ projects: [sampleProject], createProject, setCurrentProjectId });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    await userEvent.type(screen.getByLabelText("Project name"), "Line A");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(createProject).toHaveBeenCalledWith("Line A", undefined);
    await vi.waitFor(() => expect(setCurrentProjectId).toHaveBeenCalledWith("new-1"));
    expect(mockNavigate).toHaveBeenCalledWith("/overview");
  });

  it("shows an error and keeps the dialog open when create fails", async () => {
    const createProject = vi.fn().mockRejectedValue(new Error("A project with that name already exists"));
    setupAdmin({ projects: [sampleProject], createProject });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: "Create project" }));
    await userEvent.type(screen.getByLabelText("Project name"), "Dup");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    await vi.waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith({
        tone: "error",
        title: "A project with that name already exists",
      }),
    );
    expect(screen.getByRole("dialog")).toBeTruthy();
  });
});
