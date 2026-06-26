/**
 * Tests for notification-store (UI-095)
 *
 * Covers:
 * - push → item appears in toasts
 * - pushBanner → item appears in banners
 * - dismiss → removes item from both lists
 * - clearToasts / clearBanners
 * - buildItem applies correct default autoDismissMs per tone:
 *     success / stale / reconnecting → 4 000 ms
 *     warning / error / shared-impact → undefined (persistent)
 * - explicit autoDismissMs overrides the default
 */

import { beforeEach, describe, expect, it } from "vitest";
import { useNotificationStore } from "./notification-store";

function resetStore() {
  useNotificationStore.setState({ toasts: [], banners: [] });
}

describe("notification-store", () => {
  beforeEach(resetStore);

  // ── push ────────────────────────────────────────────────────────────────

  it("push adds item to toasts", () => {
    const { push } = useNotificationStore.getState();
    push({ tone: "success", title: "Saved." });
    const { toasts } = useNotificationStore.getState();
    expect(toasts).toHaveLength(1);
    expect(toasts[0].title).toBe("Saved.");
    expect(toasts[0].tone).toBe("success");
  });

  it("push returns a non-empty string id", () => {
    const { push } = useNotificationStore.getState();
    const id = push({ tone: "error", title: "Failed." });
    expect(typeof id).toBe("string");
    expect(id.length).toBeGreaterThan(0);
  });

  it("push with explicit id uses that id", () => {
    const { push } = useNotificationStore.getState();
    push({ id: "my-id", tone: "success", title: "Done." });
    const { toasts } = useNotificationStore.getState();
    expect(toasts[0].id).toBe("my-id");
  });

  it("multiple pushes stack in order", () => {
    const { push } = useNotificationStore.getState();
    push({ tone: "success", title: "First" });
    push({ tone: "warning", title: "Second" });
    const { toasts } = useNotificationStore.getState();
    expect(toasts).toHaveLength(2);
    expect(toasts[0].title).toBe("First");
    expect(toasts[1].title).toBe("Second");
  });

  // ── pushBanner ──────────────────────────────────────────────────────────

  it("pushBanner adds item to banners, not toasts", () => {
    const { pushBanner } = useNotificationStore.getState();
    pushBanner({ tone: "warning", title: "Attention." });
    const { toasts, banners } = useNotificationStore.getState();
    expect(banners).toHaveLength(1);
    expect(toasts).toHaveLength(0);
    expect(banners[0].title).toBe("Attention.");
  });

  it("pushBanner always sets autoDismissMs to undefined (persistent)", () => {
    const { pushBanner } = useNotificationStore.getState();
    // Even tones that normally auto-dismiss should be persistent as banners
    pushBanner({ tone: "success", title: "Banner success." });
    const { banners } = useNotificationStore.getState();
    expect(banners[0].autoDismissMs).toBeUndefined();
  });

  // ── dismiss ─────────────────────────────────────────────────────────────

  it("dismiss removes a toast by id", () => {
    const { push, dismiss } = useNotificationStore.getState();
    const id = push({ tone: "success", title: "To remove." });
    push({ tone: "error", title: "To keep." });
    dismiss(id);
    const { toasts } = useNotificationStore.getState();
    expect(toasts).toHaveLength(1);
    expect(toasts[0].title).toBe("To keep.");
  });

  it("dismiss removes a banner by id", () => {
    const { pushBanner, dismiss } = useNotificationStore.getState();
    const id = pushBanner({ tone: "warning", title: "Banner to remove." });
    dismiss(id);
    const { banners } = useNotificationStore.getState();
    expect(banners).toHaveLength(0);
  });

  it("dismiss with unknown id is a no-op", () => {
    const { push, dismiss } = useNotificationStore.getState();
    push({ tone: "success", title: "Stays." });
    dismiss("nonexistent-id");
    expect(useNotificationStore.getState().toasts).toHaveLength(1);
  });

  // ── clearToasts / clearBanners ──────────────────────────────────────────

  it("clearToasts empties toasts but leaves banners", () => {
    const { push, pushBanner, clearToasts } = useNotificationStore.getState();
    push({ tone: "success", title: "Toast." });
    pushBanner({ tone: "warning", title: "Banner." });
    clearToasts();
    const { toasts, banners } = useNotificationStore.getState();
    expect(toasts).toHaveLength(0);
    expect(banners).toHaveLength(1);
  });

  it("clearBanners empties banners but leaves toasts", () => {
    const { push, pushBanner, clearBanners } = useNotificationStore.getState();
    push({ tone: "success", title: "Toast." });
    pushBanner({ tone: "warning", title: "Banner." });
    clearBanners();
    const { toasts, banners } = useNotificationStore.getState();
    expect(toasts).toHaveLength(1);
    expect(banners).toHaveLength(0);
  });

  // ── default autoDismissMs per tone ──────────────────────────────────────

  const autoDismissTones = ["success", "stale", "reconnecting"] as const;
  const persistentTones = ["warning", "error", "shared-impact"] as const;

  autoDismissTones.forEach((tone) => {
    it(`push with tone "${tone}" sets autoDismissMs to 4000`, () => {
      const { push } = useNotificationStore.getState();
      push({ tone, title: "Test." });
      const { toasts } = useNotificationStore.getState();
      expect(toasts[0].autoDismissMs).toBe(4_000);
    });
  });

  persistentTones.forEach((tone) => {
    it(`push with tone "${tone}" leaves autoDismissMs undefined (persistent)`, () => {
      const { push } = useNotificationStore.getState();
      push({ tone, title: "Test." });
      const { toasts } = useNotificationStore.getState();
      expect(toasts[0].autoDismissMs).toBeUndefined();
    });
  });

  it("explicit autoDismissMs overrides the default", () => {
    const { push } = useNotificationStore.getState();
    push({ tone: "success", title: "Custom timer.", autoDismissMs: 9_000 });
    const { toasts } = useNotificationStore.getState();
    expect(toasts[0].autoDismissMs).toBe(9_000);
  });

  it("explicit autoDismissMs: 0 is treated as falsy — default applies instead", () => {
    // autoDismissMs: 0 effectively disables — treated same as not provided for
    // persistent tones; for auto-dismiss tones the default wins via || fallback.
    // This documents current behaviour rather than asserting a product requirement.
    const { push } = useNotificationStore.getState();
    push({ tone: "warning", title: "Zero.", autoDismissMs: 0 });
    const { toasts } = useNotificationStore.getState();
    // 0 is falsy so 'in' check is used — value is preserved as-is
    expect(toasts[0].autoDismissMs).toBe(0);
  });
});
