/**
 * Tests for EditLockBanner (UI-005)
 *
 * Covers all four EditLockState branches:
 * - unlocked → renders nothing
 * - locked-by-self → shows "You are editing" + Release lock button
 * - locked-by-other → shows owner name + read-only message
 * - stale → shows owner name + Take lock button
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EditLockBanner } from "./edit-lock-banner";

afterEach(cleanup);

describe("EditLockBanner — unlocked", () => {
  it("renders nothing when lock is unlocked", () => {
    const { container } = render(<EditLockBanner lock={{ kind: "unlocked" }} />);
    expect(container.firstChild).toBeNull();
  });
});

describe("EditLockBanner — locked-by-self", () => {
  it("shows editing message and release button", () => {
    render(
      <EditLockBanner
        lock={{ kind: "locked-by-self", since: "2 min ago", onRelease: vi.fn() }}
      />,
    );
    expect(screen.getByText(/You are editing/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Release lock" })).toBeTruthy();
  });

  it("calls onRelease when Release lock is clicked", async () => {
    const onRelease = vi.fn();
    render(
      <EditLockBanner lock={{ kind: "locked-by-self", since: "1 min ago", onRelease }} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Release lock" }));
    expect(onRelease).toHaveBeenCalledOnce();
  });

  it("has role=status for screen reader announcement", () => {
    const { container } = render(
      <EditLockBanner lock={{ kind: "locked-by-self", since: "now", onRelease: vi.fn() }} />,
    );
    expect(container.querySelector('[role="status"]')).toBeTruthy();
  });
});

describe("EditLockBanner — locked-by-other", () => {
  it("shows owner name and read-only message", () => {
    render(
      <EditLockBanner lock={{ kind: "locked-by-other", owner: "alice", since: "5 min ago" }} />,
    );
    expect(screen.getByText(/alice/)).toBeTruthy();
    expect(screen.getByText(/read-only/)).toBeTruthy();
  });

  it("has role=status for screen reader announcement", () => {
    const { container } = render(
      <EditLockBanner lock={{ kind: "locked-by-other", owner: "alice", since: "5 min ago" }} />,
    );
    expect(container.querySelector('[role="status"]')).toBeTruthy();
  });
});

describe("EditLockBanner — stale", () => {
  it("shows owner name and Take lock button", () => {
    render(
      <EditLockBanner
        lock={{ kind: "stale", owner: "bob", since: "10 min ago", onTake: vi.fn() }}
      />,
    );
    expect(screen.getByText(/bob/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Take lock" })).toBeTruthy();
  });

  it("calls onTake when Take lock is clicked", async () => {
    const onTake = vi.fn();
    render(
      <EditLockBanner lock={{ kind: "stale", owner: "bob", since: "10 min ago", onTake }} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Take lock" }));
    expect(onTake).toHaveBeenCalledOnce();
  });

  it("has role=status for screen reader announcement", () => {
    const { container } = render(
      <EditLockBanner
        lock={{ kind: "stale", owner: "bob", since: "10 min ago", onTake: vi.fn() }}
      />,
    );
    expect(container.querySelector('[role="status"]')).toBeTruthy();
  });
});
