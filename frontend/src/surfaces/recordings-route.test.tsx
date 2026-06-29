import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingsPage } from "./recordings-page";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({
  useShellStore: mockShellStore,
}));

afterEach(cleanup);

describe("RecordingsPage route", () => {
  beforeEach(() => {
    mockShellStore.mockReturnValue({ accessMode: "local", sharedRole: "admin" });
  });

  it("renders the Recordings & Samples heading — not a stub", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: /Recordings/i })).toBeTruthy();
  });

  it("renders the filter controls (not a surface stub)", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByPlaceholderText(/Search by name or tag/i)).toBeTruthy();
  });
});
