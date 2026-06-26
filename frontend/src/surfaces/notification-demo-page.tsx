/**
 * notification-demo-page.tsx
 *
 * Development / review page for UI-095: Notification pattern.
 * Demonstrates all tones × all variants so reviewers can verify
 * visual consistency and keyboard behavior without running real flows.
 *
 * Route: /notifications-demo  (add to router.tsx for review)
 */

import { useState } from "react";
import {
  Banner,
  InlineNotification,
  type NotificationItem,
  type NotificationTone,
} from "../ui/notification-pattern";
import { useNotificationStore } from "../shell/notification-store";

const TONES: NotificationTone[] = [
  "success",
  "warning",
  "error",
  "stale",
  "reconnecting",
  "shared-impact",
];

const TONE_EXAMPLES: Record<NotificationTone, Pick<NotificationItem, "title" | "message">> = {
  success: {
    title: "Recording saved.",
    message: "The recording was captured and is ready for replay or export.",
  },
  warning: {
    title: "Source connection is slow.",
    message: "Response times are above normal. The source is still reachable.",
  },
  error: {
    title: "Replay failed.",
    message: "The target source rejected the connection. Check endpoint settings.",
  },
  stale: {
    title: "Live values may be out of date.",
    message: "The worker has not sent an update in the last 30 seconds.",
  },
  reconnecting: {
    title: "Reconnecting to source…",
    message: "Lost connection to OPC-UA server. Retrying every 5 seconds.",
  },
  "shared-impact": {
    title: "Schema change affects 3 active sources.",
    message: "Saving this schema version will interrupt running replays on those sources.",
  },
};

// ─── Section wrapper ─────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-10">
      <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-shell-muted">
        {title}
      </h2>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  );
}

// ─── Main demo page ───────────────────────────────────────────────────────────

export function NotificationDemoPage() {
  const push = useNotificationStore((s) => s.push);
  const pushBanner = useNotificationStore((s) => s.pushBanner);
  const clearToasts = useNotificationStore((s) => s.clearToasts);
  const dismiss = useNotificationStore((s) => s.dismiss);

  const [banners, setBanners] = useState<NotificationItem[]>([]);

  function addBanner(tone: NotificationTone) {
    const ex = TONE_EXAMPLES[tone];
    const id = pushBanner({ tone, title: ex.title, message: ex.message });
    setBanners((prev) => [
      ...prev,
      { id, tone, title: ex.title, message: ex.message },
    ]);
  }

  function removeBanner(id: string) {
    dismiss(id);
    setBanners((prev) => prev.filter((b) => b.id !== id));
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <div className="mb-8">
        <h1 className="text-xl font-semibold text-shell-ink">Notification Pattern</h1>
        <p className="mt-2 text-sm text-shell-muted">
          UI-095 — All six tones × three display forms. Verify visual consistency,
          dismiss behavior, and screen-reader labels.
        </p>
      </div>

      {/* Active banners at top of content */}
      {banners.map((b) => (
        <Banner key={b.id} notification={b} onDismiss={() => removeBanner(b.id)} />
      ))}

      {/* ── Toasts ─────────────────────────────────────────────────────── */}
      <Section title="Toast — non-blocking, auto-dismiss">
        <p className="text-sm text-shell-muted">
          Toasts appear bottom-right and dismiss automatically (success/stale/reconnecting 4 s;
          warning/error/shared-impact persist until dismissed).
        </p>
        <div className="flex flex-wrap gap-2">
          {TONES.map((tone) => (
            <button
              key={tone}
              className="shell-action"
              type="button"
              onClick={() => {
                const ex = TONE_EXAMPLES[tone];
                push({ tone, title: ex.title, message: ex.message });
              }}
            >
              Push {tone}
            </button>
          ))}
          <button className="shell-action" type="button" onClick={clearToasts}>
            Clear all toasts
          </button>
        </div>
      </Section>

      {/* ── Banners ─────────────────────────────────────────────────────── */}
      <Section title="Banner — persistent, above page content">
        <p className="text-sm text-shell-muted">
          Banners persist until dismissed. Shared-impact banners should stay until the
          user can act on them.
        </p>
        <div className="flex flex-wrap gap-2">
          {TONES.map((tone) => (
            <button
              key={tone}
              className="shell-action"
              type="button"
              onClick={() => addBanner(tone)}
            >
              Add {tone} banner
            </button>
          ))}
        </div>
      </Section>

      {/* ── Inline ─────────────────────────────────────────────────────── */}
      <Section title="Inline — rendered inside a surface section">
        <p className="text-sm text-shell-muted">
          Inline notifications sit inside a panel or form section without a portal.
        </p>
        <div className="rounded-lg border border-shell-line bg-white p-4">
          <p className="mb-4 text-sm font-medium text-shell-ink">
            Example: Schema editor validation section
          </p>
          <div className="flex flex-col gap-2">
            {TONES.map((tone) => {
              const ex = TONE_EXAMPLES[tone];
              return (
                <InlineNotification
                  key={tone}
                  notification={{ id: `inline-${tone}`, tone, title: ex.title, message: ex.message }}
                />
              );
            })}
          </div>
        </div>
      </Section>

      {/* ── With action ─────────────────────────────────────────────────── */}
      <Section title="With action label">
        <InlineNotification
          notification={{
            id: "inline-action-warning",
            tone: "warning",
            title: "Replay target has changed.",
            message: "The schema on the target source was updated since this recording was created.",
            actionLabel: "View compatibility",
            onAction: () => push({ tone: "success", title: "Opening compatibility view…" }),
          }}
        />
        <InlineNotification
          notification={{
            id: "inline-action-shared",
            tone: "shared-impact",
            title: "Stopping this source will affect 2 other active users.",
            actionLabel: "View active users",
            onAction: () => push({ tone: "success", title: "Opening active users panel…" }),
          }}
        />
      </Section>

      {/* ── Message-only (no body text) ─────────────────────────────────── */}
      <Section title="Title only (no body message)">
        {TONES.map((tone) => (
          <InlineNotification
            key={tone}
            notification={{
              id: `title-only-${tone}`,
              tone,
              title: TONE_EXAMPLES[tone].title,
            }}
          />
        ))}
      </Section>
    </div>
  );
}
