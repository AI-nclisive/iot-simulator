/**
 * Tests for notification-pattern components (UI-095)
 *
 * Covers:
 * - Toast: auto-dismiss timer fires onDismiss after autoDismissMs
 * - Toast: does NOT dismiss when autoDismissMs is undefined
 * - ToastRegion: Escape key dismisses only the most recent toast
 * - ToastRegion: Escape key is a no-op when toasts list is empty
 * - ToastRegion: renders all tones without throwing
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Toast, ToastRegion } from "./notification-pattern";
import type { NotificationItem } from "./notification-pattern";

afterEach(cleanup);

function makeNotification(
  overrides: Partial<NotificationItem> = {},
): NotificationItem {
  return {
    id: "test-id",
    tone: "success",
    title: "Test notification",
    ...overrides,
  };
}

// ── Toast auto-dismiss timer ─────────────────────────────────────────────────

describe("Toast — auto-dismiss timer", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("calls onDismiss after autoDismissMs", () => {
    const onDismiss = vi.fn();
    render(
      <Toast
        notification={makeNotification({ autoDismissMs: 4_000 })}
        onDismiss={onDismiss}
      />,
    );
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(4_000);
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("does not call onDismiss when autoDismissMs is undefined", () => {
    const onDismiss = vi.fn();
    render(
      <Toast
        notification={makeNotification({ autoDismissMs: undefined })}
        onDismiss={onDismiss}
      />,
    );
    vi.advanceTimersByTime(60_000);
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it("clears timer on unmount — no stale callback after unmount", () => {
    const onDismiss = vi.fn();
    const { unmount } = render(
      <Toast
        notification={makeNotification({ autoDismissMs: 4_000 })}
        onDismiss={onDismiss}
      />,
    );
    unmount();
    vi.advanceTimersByTime(4_000);
    expect(onDismiss).not.toHaveBeenCalled();
  });
});

// ── ToastRegion — Escape key ─────────────────────────────────────────────────

describe("ToastRegion — Escape key", () => {
  it("pressing Escape dismisses only the most recent toast", () => {
    const onDismiss = vi.fn();
    const toasts: NotificationItem[] = [
      makeNotification({ id: "first", title: "First" }),
      makeNotification({ id: "second", title: "Second" }),
    ];

    render(<ToastRegion toasts={toasts} onDismiss={onDismiss} />);

    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));

    expect(onDismiss).toHaveBeenCalledOnce();
    expect(onDismiss).toHaveBeenCalledWith("second");
  });

  it("Escape is a no-op when toasts list is empty", () => {
    const onDismiss = vi.fn();
    render(<ToastRegion toasts={[]} onDismiss={onDismiss} />);
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it("non-Escape key does not call onDismiss", () => {
    const onDismiss = vi.fn();
    const toasts = [makeNotification({ id: "t1" })];
    render(<ToastRegion toasts={toasts} onDismiss={onDismiss} />);
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    expect(onDismiss).not.toHaveBeenCalled();
  });
});

// ── ToastRegion — rendering ───────────────────────────────────────────────────

describe("ToastRegion — rendering", () => {
  const allTones: NotificationItem["tone"][] = [
    "success",
    "warning",
    "error",
    "stale",
    "reconnecting",
    "shared-impact",
  ];

  allTones.forEach((tone) => {
    it(`renders tone "${tone}" without throwing`, () => {
      const onDismiss = vi.fn();
      const toasts = [makeNotification({ id: tone, tone, title: `${tone} title` })];
      expect(() => render(<ToastRegion toasts={toasts} onDismiss={onDismiss} />)).not.toThrow();
      expect(screen.getByText(`${tone} title`)).toBeTruthy();
    });
  });

  it("renders nothing when toasts is empty", () => {
    const { container } = render(<ToastRegion toasts={[]} onDismiss={vi.fn()} />);
    expect(container.childNodes).toHaveLength(0);
  });
});
