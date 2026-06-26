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

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => vi.fn() };
});

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function setupAdmin() {
  mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      accessMode: "local",
      sharedRole: "admin",
      setCurrentProjectId: vi.fn(),
    }),
  );
}

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
