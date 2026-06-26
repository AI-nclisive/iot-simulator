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

  it("calls onDismissId with notification.id after autoDismissMs", () => {
    const onDismissId = vi.fn();
    render(
      <Toast
        notification={makeNotification({ id: "test-id", autoDismissMs: 4_000 })}
        onDismissId={onDismissId}
      />,
    );
    expect(onDismissId).not.toHaveBeenCalled();
    vi.advanceTimersByTime(4_000);
    expect(onDismissId).toHaveBeenCalledOnce();
    expect(onDismissId).toHaveBeenCalledWith("test-id");
  });

  it("does not call onDismissId when autoDismissMs is undefined", () => {
    const onDismissId = vi.fn();
    render(
      <Toast
        notification={makeNotification({ autoDismissMs: undefined })}
        onDismissId={onDismissId}
      />,
    );
    vi.advanceTimersByTime(60_000);
    expect(onDismissId).not.toHaveBeenCalled();
  });

  it("clears timer on unmount — no stale callback after unmount", () => {
    const onDismissId = vi.fn();
    const { unmount } = render(
      <Toast
        notification={makeNotification({ autoDismissMs: 4_000 })}
        onDismissId={onDismissId}
      />,
    );
    unmount();
    vi.advanceTimersByTime(4_000);
    expect(onDismissId).not.toHaveBeenCalled();
  });

  it("timer is not restarted when ToastRegion re-renders with a new toast added", () => {
    // Regression: inline () => onDismiss(t.id) in ToastRegion created a new
    // function ref on every render, which restarted the useEffect timer on all
    // existing toasts. With onDismissId + useCallback the timer should fire only
    // once at the original deadline.
    const onDismissId = vi.fn();
    const first = makeNotification({ id: "first", autoDismissMs: 4_000 });

    const { rerender } = render(
      <ToastRegion toasts={[first]} onDismiss={onDismissId} />,
    );

    // Advance half way, then add a second toast (simulates a new push)
    vi.advanceTimersByTime(2_000);
    const second = makeNotification({ id: "second", autoDismissMs: 4_000 });
    rerender(<ToastRegion toasts={[first, second]} onDismiss={onDismissId} />);

    // Advance the remaining 2 s — first toast should fire exactly once
    vi.advanceTimersByTime(2_000);
    expect(onDismissId).toHaveBeenCalledTimes(1);
    expect(onDismissId).toHaveBeenCalledWith("first");
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
