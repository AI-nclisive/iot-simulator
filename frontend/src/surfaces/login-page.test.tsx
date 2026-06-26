/**
 * Tests for LoginPage (UI-010)
 *
 * Covers:
 * - idle: sign-in button disabled when fields empty
 * - submitting: sign-in button shows "Signing in…" and fields disabled
 * - invalid-credentials: error message shown
 * - server-unavailable: error message shown
 * - session-expired: banner shown above the form
 * - local mode: shows "trusted local mode" panel instead of login form
 * - typing into a field clears an error scenario back to idle
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./login-page";

const { mockNavigate, mockShellStore } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockShellStore: vi.fn(),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function setupSharedMode() {
  mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      accessMode: "shared",
      setAccessMode: vi.fn(),
      setSharedRole: vi.fn(),
    }),
  );
}

function setupLocalMode() {
  mockShellStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      accessMode: "local",
      setAccessMode: vi.fn(),
      setSharedRole: vi.fn(),
    }),
  );
}

function renderLogin(initialScenario?: "idle" | "submitting" | "invalid-credentials" | "server-unavailable" | "session-expired") {
  return render(
    <MemoryRouter>
      <LoginPage initialScenario={initialScenario} />
    </MemoryRouter>,
  );
}

describe("LoginPage — local mode", () => {
  it("shows trusted local mode panel instead of login form", () => {
    setupLocalMode();
    renderLogin();
    expect(screen.getByText(/trusted local mode/)).toBeTruthy();
    expect(screen.queryByRole("textbox")).toBeNull();
  });
});

describe("LoginPage — idle", () => {
  it("disables sign-in button when both fields empty", () => {
    setupSharedMode();
    renderLogin();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeTruthy();
    const btn = screen.getByRole("button", { name: "Sign in" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });
});

describe("LoginPage — invalid-credentials", () => {
  it("shows invalid credentials error", () => {
    setupSharedMode();
    renderLogin("invalid-credentials");
    expect(screen.getByText(/username or password is incorrect/)).toBeTruthy();
  });
});

describe("LoginPage — server-unavailable", () => {
  it("shows server unavailable error", () => {
    setupSharedMode();
    renderLogin("server-unavailable");
    expect(screen.getByText(/authentication server is not reachable/)).toBeTruthy();
  });
});

describe("LoginPage — session-expired", () => {
  it("shows session expired banner", () => {
    setupSharedMode();
    renderLogin("session-expired");
    expect(screen.getByText(/Your session has expired/)).toBeTruthy();
  });
});

describe("LoginPage — submitting", () => {
  it("shows 'Signing in…' on the button while submitting", () => {
    setupSharedMode();
    renderLogin("submitting");
    expect(screen.getByRole("button", { name: "Signing in…" })).toBeTruthy();
  });

  it("disables the sign-in button and inputs while submitting", () => {
    setupSharedMode();
    renderLogin("submitting");
    const btn = screen.getByRole("button", { name: "Signing in…" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    const inputs = document.querySelectorAll("input");
    inputs.forEach((input) => expect((input as HTMLInputElement).disabled).toBe(true));
  });
});

describe("LoginPage — typing clears error", () => {
  it("clears invalid-credentials error when username is changed", async () => {
    setupSharedMode();
    renderLogin("invalid-credentials");
    expect(screen.getByText(/username or password is incorrect/)).toBeTruthy();
    const input = screen.getByRole("textbox");
    await userEvent.type(input, "a");
    expect(screen.queryByText(/username or password is incorrect/)).toBeNull();
  });
});
