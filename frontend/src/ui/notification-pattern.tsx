/**
 * UI-095 — Notification pattern
 *
 * Delivers transient and persistent feedback consistently across all surfaces.
 *
 * Three display forms:
 *  - Toast      – auto-dismissing, non-blocking, bottom-right corner.
 *  - Banner     – persistent, top of main content area; persists until dismissed.
 *  - Inline     – rendered inline inside a surface section.
 *
 * Six semantic tones (matches DESIGN.md § Failure Handling + Notifications spec):
 *  - success, warning, error, stale, reconnecting, shared-impact
 *
 * Accessibility: role=status (polite) for success/stale/reconnecting,
 *                role=alert  (assertive) for warning/error/shared-impact.
 * Dismissal:     Escape key closes focused or most recent toast.
 * No color-only: every tone carries a distinct label and icon shape.
 */

import { useCallback, useEffect, useId } from "react";
import { createPortal } from "react-dom";

// ─── Types ────────────────────────────────────────────────────────────────────

export type NotificationTone =
  | "success"
  | "warning"
  | "error"
  | "stale"
  | "reconnecting"
  | "shared-impact";

export type NotificationVariant = "toast" | "banner" | "inline";

export interface NotificationItem {
  id: string;
  tone: NotificationTone;
  title: string;
  message?: string;
  /** ms until auto-dismiss; undefined = persistent (banner/inline default) */
  autoDismissMs?: number;
  actionLabel?: string;
  onAction?: () => void;
}

// ─── Tone metadata ─────────────────────────────────────────────────────────

type ToneMeta = {
  label: string;
  /** ARIA live region role */
  ariaRole: "alert" | "status";
  /** Tailwind classes for the container border + bg */
  containerCls: string;
  /** Tailwind classes for the icon area background */
  iconBgCls: string;
  /** Tailwind text color for title */
  titleCls: string;
  /** SVG icon path(s) */
  icon: React.ReactNode;
};

function CheckIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <path
        d="M3 8.5 6.5 12 13 5"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.75"
      />
    </svg>
  );
}

function WarningIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <path
        d="M8 2 14.5 13.5H1.5L8 2Z"
        stroke="currentColor"
        strokeLinejoin="round"
        strokeWidth="1.5"
      />
      <path d="M8 6v3.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.75" />
      <circle cx="8" cy="11.5" fill="currentColor" r="0.75" />
    </svg>
  );
}

function ErrorIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <circle cx="8" cy="8" r="6" stroke="currentColor" strokeWidth="1.5" />
      <path d="M8 5v3.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.75" />
      <circle cx="8" cy="10.5" fill="currentColor" r="0.75" />
    </svg>
  );
}

function StaleIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <circle cx="8" cy="8" r="6" stroke="currentColor" strokeWidth="1.5" />
      <path d="M8 5v3l2 2" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" />
    </svg>
  );
}

function ReconnectingIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <path
        d="M3 8a5 5 0 0 1 8.66-2.5M13 8a5 5 0 0 1-8.66 2.5"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.5"
      />
      <path d="M11.5 5.5 13 3l1.5 2.5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" />
      <path d="M4.5 10.5 3 13l-1.5-2.5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" />
    </svg>
  );
}

function SharedImpactIcon() {
  return (
    <svg aria-hidden="true" fill="none" height="16" viewBox="0 0 16 16" width="16">
      <circle cx="8" cy="4" r="2" stroke="currentColor" strokeWidth="1.5" />
      <circle cx="3" cy="12" r="2" stroke="currentColor" strokeWidth="1.5" />
      <circle cx="13" cy="12" r="2" stroke="currentColor" strokeWidth="1.5" />
      <path d="M8 6v2M6.3 9.7 3 10M9.7 9.7 13 10" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
    </svg>
  );
}

const TONE_META: Record<NotificationTone, ToneMeta> = {
  success: {
    label: "Success",
    ariaRole: "status",
    containerCls: "border-shell-accent/25 bg-shell-accent/5",
    iconBgCls: "bg-shell-accent/10 text-shell-accent",
    titleCls: "text-shell-ink",
    icon: <CheckIcon />,
  },
  warning: {
    label: "Warning",
    ariaRole: "alert",
    containerCls: "border-shell-warning/30 bg-shell-warning/5",
    iconBgCls: "bg-shell-warning/10 text-shell-warning",
    titleCls: "text-shell-ink",
    icon: <WarningIcon />,
  },
  error: {
    label: "Error",
    ariaRole: "alert",
    containerCls: "border-shell-danger/30 bg-shell-danger/5",
    iconBgCls: "bg-shell-danger/10 text-shell-danger",
    titleCls: "text-shell-ink",
    icon: <ErrorIcon />,
  },
  stale: {
    label: "Stale",
    ariaRole: "status",
    containerCls: "border-shell-warning/20 bg-shell-warning/5",
    iconBgCls: "bg-shell-warning/10 text-shell-warning",
    titleCls: "text-shell-ink",
    icon: <StaleIcon />,
  },
  reconnecting: {
    label: "Reconnecting",
    ariaRole: "status",
    containerCls: "border-shell-line bg-shell-base/60",
    iconBgCls: "bg-shell-line/60 text-shell-muted",
    titleCls: "text-shell-ink",
    icon: <ReconnectingIcon />,
  },
  "shared-impact": {
    label: "Shared impact",
    ariaRole: "alert",
    containerCls: "border-shell-warning/30 bg-shell-warning/8",
    iconBgCls: "bg-shell-warning/15 text-shell-warning",
    titleCls: "text-shell-ink",
    icon: <SharedImpactIcon />,
  },
};

// ─── DismissButton ─────────────────────────────────────────────────────────

function DismissButton({ onDismiss }: { onDismiss: () => void }) {
  return (
    <button
      aria-label="Dismiss notification"
      className="ml-2 shrink-0 rounded p-1 text-shell-muted transition hover:text-shell-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-shell-accent/40"
      type="button"
      onClick={onDismiss}
    >
      <svg aria-hidden="true" fill="none" height="14" viewBox="0 0 14 14" width="14">
        <path
          d="M2 2 12 12M12 2 2 12"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.75"
        />
      </svg>
    </button>
  );
}

// ─── NotificationCard (shared inner layout) ────────────────────────────────

interface NotificationCardProps {
  notification: NotificationItem;
  onDismiss?: () => void;
  compact?: boolean;
}

function NotificationCard({ notification, onDismiss, compact = false }: NotificationCardProps) {
  const meta = TONE_META[notification.tone];
  const labelId = useId();

  return (
    <div
      aria-labelledby={labelId}
      className={`flex items-start gap-3 rounded-lg border px-4 ${compact ? "py-3" : "py-3.5"} shadow-panel/50 ${meta.containerCls}`}
      role={meta.ariaRole}
    >
      {/* Icon */}
      <span
        aria-hidden="true"
        className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${meta.iconBgCls}`}
      >
        {meta.icon}
      </span>

      {/* Body */}
      <div className="min-w-0 flex-1">
        {/* Tone label + title */}
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="shell-chip border-transparent bg-transparent px-0 py-0 text-[10px] uppercase tracking-widest text-shell-muted">
            {meta.label}
          </span>
          <p id={labelId} className={`text-sm font-semibold ${meta.titleCls}`}>
            {notification.title}
          </p>
        </div>

        {/* Optional message */}
        {notification.message ? (
          <p className="mt-1 text-sm leading-5 text-shell-muted">{notification.message}</p>
        ) : null}

        {/* Optional action */}
        {notification.actionLabel && notification.onAction ? (
          <button
            className="shell-text-action mt-2 pl-0 text-xs"
            type="button"
            onClick={notification.onAction}
          >
            {notification.actionLabel}
          </button>
        ) : null}
      </div>

      {/* Dismiss */}
      {onDismiss ? <DismissButton onDismiss={onDismiss} /> : null}
    </div>
  );
}

// ─── Toast (auto-dismiss card, no own portal) ─────────────────────────────
//
// Toast renders a plain NotificationCard with an auto-dismiss timer.
// It does NOT create its own portal — ToastRegion owns the single portal
// and the positioning container. Rendering another portal here would teleport
// each card out of that container, leaving the flex stack empty.
//
// Escape-key handling lives in ToastRegion (one listener for the whole stack).
//
// onDismissId receives the stable store-level dismiss reference + the item id
// separately so useCallback can produce a stable onDismiss callback. This
// prevents the () => onDismissId(id) inline closure in ToastRegion from
// restarting the auto-dismiss timer on existing toasts whenever a new toast
// is pushed.

interface ToastProps {
  notification: NotificationItem;
  /** Stable dismiss function (e.g. from Zustand store). Called with notification.id. */
  onDismissId: (id: string) => void;
}

export function Toast({ notification, onDismissId }: ToastProps) {
  // Stable callback — only recreated when id or onDismissId changes, not on
  // every ToastRegion re-render caused by an unrelated toast being added.
  const onDismiss = useCallback(
    () => onDismissId(notification.id),
    [onDismissId, notification.id],
  );

  // Auto-dismiss timer only — Escape is handled by ToastRegion.
  useEffect(() => {
    if (!notification.autoDismissMs) return;
    const timer = setTimeout(onDismiss, notification.autoDismissMs);
    return () => clearTimeout(timer);
  }, [notification.autoDismissMs, onDismiss]);

  return (
    <div className="pointer-events-auto w-full max-w-sm">
      <NotificationCard compact notification={notification} onDismiss={onDismiss} />
    </div>
  );
}

// ─── ToastRegion (renders all active toasts, owns the portal) ─────────────

interface ToastRegionProps {
  toasts: NotificationItem[];
  onDismiss: (id: string) => void;
}

export function ToastRegion({ toasts, onDismiss }: ToastRegionProps) {
  // Single Escape-key listener for the entire stack — dismisses only the
  // most recent toast so users can clear one at a time.
  useEffect(() => {
    if (toasts.length === 0) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        const last = toasts[toasts.length - 1];
        onDismiss(last.id);
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [toasts, onDismiss]);

  if (toasts.length === 0 || typeof document === "undefined") return null;

  return createPortal(
    <div
      aria-label="Notifications"
      aria-live="polite"
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2"
    >
      {toasts.map((t) => (
        <Toast key={t.id} notification={t} onDismissId={onDismiss} />
      ))}
    </div>,
    document.body,
  );
}

// ─── Banner (persistent, sits above page content) ─────────────────────────

interface BannerProps {
  notification: NotificationItem;
  onDismiss?: () => void;
}

export function Banner({ notification, onDismiss }: BannerProps) {
  return (
    <div className="mb-4">
      <NotificationCard notification={notification} onDismiss={onDismiss} />
    </div>
  );
}

// ─── InlineNotification (rendered inside a surface section) ───────────────

interface InlineNotificationProps {
  notification: NotificationItem;
  onDismiss?: () => void;
}

export function InlineNotification({ notification, onDismiss }: InlineNotificationProps) {
  return <NotificationCard compact notification={notification} onDismiss={onDismiss} />;
}

