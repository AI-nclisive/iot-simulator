/**
 * notification-store.ts
 *
 * Global notification state. Used by ToastRegion in AppShell and by any
 * surface that needs to push feedback.
 *
 * Usage:
 *   const notify = useNotificationStore(s => s.push);
 *   notify({ tone: "success", title: "Recording saved." });
 *
 *   const dismiss = useNotificationStore(s => s.dismiss);
 *   dismiss(id);
 *
 * Default autoDismissMs:
 *   - success / stale / reconnecting → 4 000 ms
 *   - warning / error / shared-impact → persistent (undefined = stays until dismissed)
 */

import { create } from "zustand";
import type { NotificationItem, NotificationTone } from "../ui/notification-pattern";

type PushPayload = Omit<NotificationItem, "id"> & { id?: string };

const DEFAULT_AUTO_DISMISS: Partial<Record<NotificationTone, number>> = {
  success: 4_000,
  stale: 4_000,
  reconnecting: 4_000,
};

type NotificationState = {
  toasts: NotificationItem[];
  banners: NotificationItem[];

  /** Push a toast notification. Returns the generated id. */
  push: (payload: PushPayload) => string;
  /** Push a persistent banner. Returns the generated id. */
  pushBanner: (payload: PushPayload) => string;
  /** Dismiss a toast or banner by id. */
  dismiss: (id: string) => void;
  /** Dismiss all toasts. */
  clearToasts: () => void;
  /** Dismiss all banners. */
  clearBanners: () => void;
};

let counter = 0;
function nextId() {
  return `notif-${++counter}-${Date.now()}`;
}

function buildItem(payload: PushPayload): NotificationItem {
  const id = payload.id ?? nextId();
  const autoDismissMs =
    "autoDismissMs" in payload
      ? payload.autoDismissMs
      : DEFAULT_AUTO_DISMISS[payload.tone];
  return { ...payload, id, autoDismissMs };
}

export const useNotificationStore = create<NotificationState>((set) => ({
  toasts: [],
  banners: [],

  push: (payload) => {
    const item = buildItem(payload);
    set((state) => ({ toasts: [...state.toasts, item] }));
    return item.id;
  },

  pushBanner: (payload) => {
    const item = buildItem({ autoDismissMs: undefined, ...payload });
    set((state) => ({ banners: [...state.banners, item] }));
    return item.id;
  },

  dismiss: (id) =>
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
      banners: state.banners.filter((b) => b.id !== id),
    })),

  clearToasts: () => set({ toasts: [] }),
  clearBanners: () => set({ banners: [] }),
}));
