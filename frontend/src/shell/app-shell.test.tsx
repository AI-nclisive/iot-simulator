/**
 * Tests for AppShell responsive nav behavior (UI-094)
 *
 * Covers:
 * - hamburger button is present below lg (rendered unconditionally, hidden via CSS)
 * - clicking hamburger shows the project-rail aside
 * - clicking hamburger again hides the project-rail aside
 * - aria-expanded reflects navOpen state
 * - clicking a NavLink closes the nav (setNavOpen(false))
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});
import { AppShell } from "./app-shell";

const { mockShellStore, mockNotificationStore, mockProjectsStore } = vi.hoisted(() => ({
  mockShellStore: vi.fn(),
  mockNotificationStore: vi.fn(),
  mockProjectsStore: vi.fn(),
}));

vi.mock("./shell-store", () => ({ useShellStore: mockShellStore }));
vi.mock("./notification-store", () => ({ useNotificationStore: mockNotificationStore }));
vi.mock("./projects-store", () => ({ useProjectsStore: mockProjectsStore }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const PROJECTS = [
  { id: "proj-1", name: "Project One" },
];

function setupStores() {
  mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      accessMode: "local",
      currentProjectId: "proj-1",
      setCurrentProjectId: vi.fn(),
      sharedRole: "user",
    }),
  );
  mockNotificationStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      toasts: [],
      dismiss: vi.fn(),
    }),
  );
  mockProjectsStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      projects: PROJECTS,
      loadProjects: vi.fn().mockResolvedValue(undefined),
    }),
  );
}

function renderShell() {
  setupStores();
  return render(
    <MemoryRouter initialEntries={["/overview"]}>
      <AppShell />
    </MemoryRouter>,
  );
}

// ── Hamburger button presence ────────────────────────────────────────────────

describe("AppShell — hamburger button", () => {
  it("renders the hamburger toggle button", () => {
    renderShell();
    // The button carries a descriptive aria-label
    expect(screen.getByRole("button", { name: /open navigation/i })).toBeTruthy();
  });
});

// ── navOpen toggle ───────────────────────────────────────────────────────────

describe("AppShell — hamburger toggle", () => {
  it("shows the project-rail aside after clicking the hamburger", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    const aside = container.querySelector("#project-rail") as HTMLElement;
    // Initially hidden — classList includes "hidden" but not bare "block"
    expect(aside.classList.contains("hidden")).toBe(true);
    expect(aside.classList.contains("block")).toBe(false);

    await user.click(screen.getByRole("button", { name: /open navigation/i }));

    expect(aside.classList.contains("block")).toBe(true);
    expect(aside.classList.contains("hidden")).toBe(false);
  });

  it("hides the project-rail aside after clicking hamburger twice", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    const aside = container.querySelector("#project-rail") as HTMLElement;
    const hamburger = screen.getByRole("button", { name: /open navigation/i });

    await user.click(hamburger);
    expect(aside.classList.contains("block")).toBe(true);
    expect(aside.classList.contains("hidden")).toBe(false);

    await user.click(screen.getByRole("button", { name: /close navigation/i }));
    expect(aside.classList.contains("hidden")).toBe(true);
    expect(aside.classList.contains("block")).toBe(false);
  });
});

// ── aria-expanded sync ───────────────────────────────────────────────────────

describe("AppShell — aria-expanded", () => {
  it("starts with aria-expanded=false", () => {
    renderShell();
    const btn = screen.getByRole("button", { name: /open navigation/i }) as HTMLButtonElement;
    expect(btn.getAttribute("aria-expanded")).toBe("false");
  });

  it("sets aria-expanded=true after opening", async () => {
    const user = userEvent.setup();
    renderShell();
    const btn = screen.getByRole("button", { name: /open navigation/i }) as HTMLButtonElement;
    await user.click(btn);
    expect(btn.getAttribute("aria-expanded")).toBe("true");
  });

  it("returns aria-expanded=false after closing", async () => {
    const user = userEvent.setup();
    renderShell();

    await user.click(screen.getByRole("button", { name: /open navigation/i }));
    await user.click(screen.getByRole("button", { name: /close navigation/i }));

    const btn = screen.getByRole("button", { name: /open navigation/i }) as HTMLButtonElement;
    expect(btn.getAttribute("aria-expanded")).toBe("false");
  });
});

// ── NavLink click closes nav ─────────────────────────────────────────────────

describe("AppShell — NavLink closes nav", () => {
  it("collapses the nav when a NavLink is clicked", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    // Open nav first
    await user.click(screen.getByRole("button", { name: /open navigation/i }));
    const aside = container.querySelector("#project-rail") as HTMLElement;
    expect(aside.className).toContain("block");

    // Click any nav link — Overview is always rendered (local mode, canManageAdmin=false)
    const overviewLink = screen.getByRole("link", { name: "Overview" });
    await user.click(overviewLink);

    // Nav should collapse — bare "block" class removed, "hidden" added
    expect(aside.classList.contains("hidden")).toBe(true);
    expect(aside.classList.contains("block")).toBe(false);
  });
});

// ── Project selection guard ──────────────────────────────────────────────────

describe("AppShell — project selection guard", () => {
  function setupEmptyProject() {
    mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
      selector({
        accessMode: "local",
        currentProjectId: "",
        setCurrentProjectId: vi.fn(),
        sharedRole: "user",
      }),
    );
    mockNotificationStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
      selector({ toasts: [], dismiss: vi.fn() }),
    );
    mockProjectsStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
      selector({ projects: [], loadProjects: vi.fn().mockResolvedValue(undefined) }),
    );
  }

  it("redirects to /projects when currentProjectId is empty and not already on /projects", () => {
    setupEmptyProject();
    render(
      <MemoryRouter initialEntries={["/overview"]}>
        <AppShell />
      </MemoryRouter>,
    );
    expect(mockNavigate).toHaveBeenCalledWith("/projects", { replace: true });
  });

  it("does not redirect when already on /projects", () => {
    setupEmptyProject();
    render(
      <MemoryRouter initialEntries={["/projects"]}>
        <AppShell />
      </MemoryRouter>,
    );
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("does not redirect when currentProjectId is set", () => {
    setupStores();
    render(
      <MemoryRouter initialEntries={["/overview"]}>
        <AppShell />
      </MemoryRouter>,
    );
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
