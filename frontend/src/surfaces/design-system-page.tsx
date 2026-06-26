import { useState } from "react";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="shell-panel px-6 py-6">
      <h2 className="text-lg font-semibold text-shell-ink">{title}</h2>
      <div className="mt-5">{children}</div>
    </section>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3 border-b border-shell-line/60 pb-4 last:border-0 last:pb-0 sm:flex-row sm:items-start">
      <p className="w-40 shrink-0 text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}

function ColorSwatch({
  name,
  bg,
  text,
  border,
}: {
  name: string;
  bg: string;
  text: string;
  border?: string;
}) {
  return (
    <div className="flex flex-col items-center gap-2">
      <div
        className={`h-10 w-20 rounded-md border ${border ?? "border-shell-line"} ${bg}`}
      />
      <p className={`text-xs font-medium ${text}`}>{name}</p>
    </div>
  );
}

function TypeSample({
  label,
  className,
  text,
}: {
  label: string;
  className: string;
  text: string;
}) {
  return (
    <div className="border-b border-shell-line/60 pb-3 last:border-0 last:pb-0">
      <p className="mb-1 text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <p className={className}>{text}</p>
    </div>
  );
}

export function DesignSystemPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogTone, setDialogTone] = useState<"warning" | "danger">("danger");

  return (
    <div
      className="min-h-screen bg-shell-base"
    >
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-8">
        <header className="shell-panel px-6 py-5">
          <StatusBadge label="Internal reference" tone="accent" />
          <h1 className="mt-3 text-2xl font-semibold text-shell-ink">Visual System Baseline</h1>
          <p className="mt-2 text-sm leading-6 text-shell-muted">
            UI-090 · Approved stack: React 18 · React Router 6 · Tailwind CSS 3 · Zustand.
            This page is the single reference for typography, spacing, color tokens, component
            variants, and shared interaction rules used across the product shell.
          </p>
        </header>

        {/* COLOR TOKENS */}
        <Section title="Color tokens">
          <div className="space-y-4">
            <Row label="Shell palette">
              <ColorSwatch name="base" bg="bg-shell-base" text="text-shell-ink" />
              <ColorSwatch name="panel" bg="bg-shell-panel" text="text-shell-ink" />
              <ColorSwatch name="line" bg="bg-shell-line" text="text-shell-ink" />
              <ColorSwatch name="ink" bg="bg-shell-ink" text="text-white" />
              <ColorSwatch name="muted" bg="bg-shell-muted" text="text-white" />
              <ColorSwatch name="accent" bg="bg-shell-accent" text="text-white" />
              <ColorSwatch name="warning" bg="bg-shell-warning" text="text-white" />
              <ColorSwatch name="danger" bg="bg-shell-danger" text="text-white" />
            </Row>
            <Row label="Usage rules">
              <ul className="space-y-1 text-sm leading-6 text-shell-muted">
                <li>
                  <strong className="text-shell-ink">base / panel</strong> — backgrounds: base for
                  body, panel for elevated cards
                </li>
                <li>
                  <strong className="text-shell-ink">line</strong> — borders, dividers; never use
                  alone to signal state
                </li>
                <li>
                  <strong className="text-shell-ink">ink / muted</strong> — primary and secondary
                  text; muted must not carry critical information
                </li>
                <li>
                  <strong className="text-shell-ink">accent</strong> — interactive affordances,
                  links, active states
                </li>
                <li>
                  <strong className="text-shell-ink">warning / danger</strong> — status and
                  destructive actions; must never rely on color alone
                </li>
              </ul>
            </Row>
          </div>
        </Section>

        {/* TYPOGRAPHY */}
        <Section title="Typography">
          <div className="space-y-3">
            <TypeSample
              label="Page heading — text-2xl font-semibold"
              className="text-2xl font-semibold text-shell-ink"
              text="Assembly Line A telemetry"
            />
            <TypeSample
              label="Section heading — text-lg font-semibold"
              className="text-lg font-semibold text-shell-ink"
              text="Data Sources"
            />
            <TypeSample
              label="Sub-heading — text-base font-semibold"
              className="text-base font-semibold text-shell-ink"
              text="Timeline"
            />
            <TypeSample
              label="Body — text-sm leading-6"
              className="text-sm leading-6 text-shell-ink"
              text="Source is running and actively publishing values to connected clients."
            />
            <TypeSample
              label="Muted body — text-sm leading-6 text-shell-muted"
              className="text-sm leading-6 text-shell-muted"
              text="Last seen 3 minutes ago · 4 clients connected"
            />
            <TypeSample
              label="Label — text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted"
              className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted"
              text="Run origin"
            />
            <TypeSample
              label="Dense mono — text-xs font-mono"
              className="text-xs font-mono text-shell-ink"
              text="opc.tcp://192.168.1.10:4840"
            />
          </div>
        </Section>

        {/* STATUS BADGES */}
        <Section title="Status badges — StatusBadge">
          <div className="space-y-3">
            <Row label="Tones">
              <StatusBadge label="Neutral" tone="neutral" />
              <StatusBadge label="Accent" tone="accent" />
              <StatusBadge label="Warning" tone="warning" />
              <StatusBadge label="Danger" tone="danger" />
            </Row>
            <Row label="Runtime states">
              <StatusBadge label="Running" tone="accent" />
              <StatusBadge label="Stopped" tone="neutral" />
              <StatusBadge label="Recording" tone="warning" />
              <StatusBadge label="Error" tone="danger" />
              <StatusBadge label="Stale" tone="warning" />
              <StatusBadge label="Ready" tone="accent" />
              <StatusBadge label="Capturing" tone="warning" />
              <StatusBadge label="Export failed" tone="danger" />
            </Row>
            <Row label="Usage rule">
              <p className="text-sm leading-6 text-shell-muted">
                Badges must carry a visible text label. Color alone is not enough — label and
                tone must both communicate state to pass contrast and screen-reader checks.
              </p>
            </Row>
          </div>
        </Section>

        {/* SHARED STATE PANELS */}
        <Section title="Shared state panels — SharedStatePanel">
          <div className="space-y-3">
            {(
              [
                {
                  state: "loading",
                  message: "Fetching data sources. This should complete in a moment.",
                },
                { state: "empty", message: "No data sources have been created yet. Create one to get started." },
                {
                  state: "error",
                  message: "The source list could not be loaded. Check the connection and retry.",
                  actionLabel: "Retry",
                },
                {
                  state: "stale",
                  message: "Live data may be out of date. The last refresh was more than 2 minutes ago.",
                  actionLabel: "Refresh",
                },
                {
                  state: "locked",
                  message: "Another user is editing this schema. It will become available when they finish.",
                },
                {
                  state: "warning",
                  message: "This source has unsaved changes that will be lost if you leave now.",
                  actionLabel: "Save changes",
                  secondaryActionLabel: "Discard",
                },
              ] as const
            ).map((item) => (
              <SharedStatePanel
                key={item.state}
                state={item.state}
                message={item.message}
                actionLabel={"actionLabel" in item ? item.actionLabel : undefined}
                secondaryActionLabel={"secondaryActionLabel" in item ? item.secondaryActionLabel : undefined}
                onAction={() => {}}
                onSecondaryAction={() => {}}
              />
            ))}
          </div>
        </Section>

        {/* ACTIONS */}
        <Section title="Action components">
          <div className="space-y-4">
            <Row label="shell-action">
              <button className="shell-action" type="button">
                Default
              </button>
              <button className="shell-action" type="button" disabled>
                Disabled
              </button>
            </Row>
            <Row label="shell-action-warning">
              <button className="shell-action-warning" type="button">
                Warning action
              </button>
              <button className="shell-action-warning" type="button" disabled>
                Disabled
              </button>
            </Row>
            <Row label="shell-action-danger">
              <button className="shell-action-danger" type="button">
                Danger action
              </button>
              <button className="shell-action-danger" type="button" disabled>
                Disabled
              </button>
            </Row>
            <Row label="shell-text-action">
              <button className="shell-text-action" type="button">
                Text action
              </button>
            </Row>
            <Row label="shell-text-action-danger">
              <button className="shell-text-action-danger" type="button">
                Danger text
              </button>
            </Row>
            <Row label="Usage rules">
              <ul className="space-y-1 text-sm leading-6 text-shell-muted">
                <li>
                  <strong className="text-shell-ink">shell-action</strong> — default for all
                  operational actions
                </li>
                <li>
                  <strong className="text-shell-ink">shell-action-warning</strong> — retry,
                  override, recovery flows
                </li>
                <li>
                  <strong className="text-shell-ink">shell-action-danger</strong> — destructive
                  confirmation buttons only; must follow a ConfirmationDialog
                </li>
                <li>
                  <strong className="text-shell-ink">shell-text-action</strong> — navigation
                  links and secondary contextual links
                </li>
                <li>
                  <strong className="text-shell-ink">shell-text-action-danger</strong> — inline
                  destructive text links (e.g. remove, revoke); prefer shell-action-danger for
                  button-form destructive actions
                </li>
              </ul>
            </Row>
          </div>
        </Section>

        {/* FORM FIELDS */}
        <Section title="Form fields">
          <div className="space-y-4">
            <Row label="shell-field input">
              <input
                className="shell-field w-64"
                placeholder="opc.tcp://192.168.1.10:4840"
                type="text"
              />
            </Row>
            <Row label="shell-field select">
              <select className="shell-field w-48">
                <option>OPC UA</option>
                <option>Modbus TCP</option>
              </select>
            </Row>
            <Row label="shell-field disabled">
              <input
                className="shell-field w-64 disabled:cursor-not-allowed disabled:opacity-60"
                disabled
                placeholder="Read-only"
                type="text"
                value="opc.tcp://192.168.1.10:4840"
                readOnly
              />
            </Row>
            <Row label="Usage rules">
              <ul className="space-y-1 text-sm leading-6 text-shell-muted">
                <li>All inputs use shell-field — never raw browser defaults</li>
                <li>Labels always sit above the field, not inside as persistent placeholders</li>
                <li>Validation errors appear below the field as inline text, not as floating toasts</li>
              </ul>
            </Row>
          </div>
        </Section>

        {/* SHELL PANEL */}
        <Section title="Shell panel — shell-panel">
          <div className="space-y-3">
            <div className="shell-panel px-5 py-5">
              <p className="text-sm font-semibold text-shell-ink">Standard panel</p>
              <p className="mt-1 text-sm text-shell-muted">
                Used for all main content sections: detail headers, tab bodies, and dashboard
                cards. Provides border, background, blur, and shadow in one class.
              </p>
            </div>
            <Row label="Usage rules">
              <ul className="space-y-1 text-sm leading-6 text-shell-muted">
                <li>
                  <strong className="text-shell-ink">shell-panel</strong> is the default for all
                  elevated content sections
                </li>
                <li>Never nest shell-panels more than one level deep</li>
                <li>
                  Inner cards within a panel use{" "}
                  <code className="rounded bg-shell-base px-1 text-xs font-mono text-shell-ink">
                    rounded-md border border-shell-line bg-white
                  </code>
                </li>
              </ul>
            </Row>
          </div>
        </Section>

        {/* NAVIGATION */}
        <Section title="Navigation items">
          <div className="space-y-4">
            <Row label="Rail items">
              <div className="w-52 space-y-1 rounded-md bg-shell-base/60 p-2">
                <a className="shell-nav-item" href="#design-system">
                  Overview
                </a>
                <a className="shell-nav-item shell-nav-item-active" href="#design-system">
                  Data Sources
                </a>
                <a className="shell-nav-item" href="#design-system">
                  Evidence
                </a>
                <span className="shell-nav-item shell-nav-item-disabled">
                  Admin
                </span>
              </div>
            </Row>
            <Row label="Compact tabs">
              <div className="flex gap-1 rounded-lg border border-shell-line bg-shell-base/60 p-1">
                <button className="shell-nav-item-compact shell-nav-item-compact-active w-24" type="button">
                  Overview
                </button>
                <button className="shell-nav-item-compact w-24" type="button">
                  Schema
                </button>
                <button className="shell-nav-item-compact w-24" type="button">
                  Values
                </button>
                <button
                  className="shell-nav-item-compact shell-nav-item-compact-disabled w-24"
                  type="button"
                  disabled
                >
                  Clients
                </button>
              </div>
            </Row>
          </div>
        </Section>

        {/* CHIPS */}
        <Section title="Chips and filter chips">
          <div className="space-y-3">
            <Row label="shell-chip">
              <span className="shell-chip border-shell-accent/20 bg-shell-accent/10 text-shell-accent">
                Running
              </span>
              <span className="shell-chip border-shell-warning/25 bg-shell-warning/10 text-shell-warning">
                Warning
              </span>
              <span className="shell-chip border-shell-danger/25 bg-shell-danger/10 text-shell-danger">
                Error
              </span>
              <span className="shell-chip border-shell-line bg-white text-shell-muted">
                Neutral
              </span>
            </Row>
            <Row label="shell-filter-chip">
              <button className="shell-filter-chip" type="button">
                All protocols
              </button>
              <button className="shell-filter-chip border-shell-accent text-shell-accent" type="button">
                OPC UA ✕
              </button>
            </Row>
          </div>
        </Section>

        {/* CONFIRMATION DIALOGS */}
        <Section title="Confirmation dialogs — ConfirmationDialog">
          <div className="space-y-3">
            <Row label="Preview">
              <button
                className="shell-action-warning"
                type="button"
                onClick={() => {
                  setDialogTone("warning");
                  setDialogOpen(true);
                }}
              >
                Open warning dialog
              </button>
              <button
                className="shell-action-danger"
                type="button"
                onClick={() => {
                  setDialogTone("danger");
                  setDialogOpen(true);
                }}
              >
                Open danger dialog
              </button>
            </Row>
            <Row label="Usage rules">
              <ul className="space-y-1 text-sm leading-6 text-shell-muted">
                <li>Every destructive action (delete, stop, discard) must go through ConfirmationDialog</li>
                <li>
                  <strong className="text-shell-ink">objectLabel</strong> names the affected
                  item; <strong className="text-shell-ink">impacts</strong> describe shared
                  consequences
                </li>
                <li>
                  <strong className="text-shell-ink">reversibilityLabel</strong> is mandatory —
                  must tell the user whether the action can be undone
                </li>
                <li>Escape key and backdrop click cancel by default; disabled during processing</li>
              </ul>
            </Row>
          </div>
        </Section>

        {/* SPACING */}
        <Section title="Spacing and density rules">
          <div className="space-y-2 text-sm leading-6 text-shell-muted">
            <p>
              <strong className="text-shell-ink">Section padding:</strong> px-5 py-5 inside
              shell-panel; px-6 py-6 for page-level reference sections.
            </p>
            <p>
              <strong className="text-shell-ink">Card inner padding:</strong> px-4 py-3 (compact
              info card) or px-4 py-4 (standard row card).
            </p>
            <p>
              <strong className="text-shell-ink">Gap between cards:</strong> gap-3 as the
              default; gap-4 or gap-5 only when content needs more breathing room.
            </p>
            <p>
              <strong className="text-shell-ink">Action bar:</strong> flex flex-wrap gap-2 for
              groups of buttons; always last in a section header.
            </p>
            <p>
              <strong className="text-shell-ink">Grid breakpoints:</strong> md:grid-cols-2 for
              paired summary cards; xl:grid-cols-* for large multi-panel layouts.
            </p>
            <p>
              <strong className="text-shell-ink">Tables:</strong> dense by default — no
              oversized row heights; search, filters, and sort live in the toolbar above.
            </p>
          </div>
        </Section>

        {/* ACCESSIBILITY RULES */}
        <Section title="Accessibility baseline">
          <div className="space-y-2 text-sm leading-6 text-shell-muted">
            <p>
              <strong className="text-shell-ink">Focus rings:</strong> all interactive elements
              use focus-visible:ring-2 focus-visible:ring-shell-accent/40; never suppress
              focus outlines.
            </p>
            <p>
              <strong className="text-shell-ink">Dialogs:</strong> role=&quot;dialog&quot;
              aria-modal=&quot;true&quot; aria-labelledby aria-describedby are required on every
              modal overlay.
            </p>
            <p>
              <strong className="text-shell-ink">Status:</strong> status badges carry visible
              text labels; color is a reinforcement, not the sole signal.
            </p>
            <p>
              <strong className="text-shell-ink">Buttons:</strong> every button has a visible
              label or aria-label; disabled buttons remain in the tab order to signal
              unavailability.
            </p>
            <p>
              <strong className="text-shell-ink">Tables:</strong> thead with scope=&quot;col&quot;
              headers; sortable columns announce direction via aria-sort.
            </p>
            <p>
              <strong className="text-shell-ink">Icons (future):</strong> any decorative icon
              must have aria-hidden=&quot;true&quot;; any functional icon button needs aria-label.
            </p>
          </div>
        </Section>

        <footer className="px-2 py-4 text-center text-xs text-shell-muted">
          UI-090 · Visual System Baseline · IoT Data Source Simulator
        </footer>
      </div>

      <ConfirmationDialog
        open={dialogOpen}
        tone={dialogTone}
        title={
          dialogTone === "danger"
            ? "Delete data source permanently?"
            : "Stop active recording run?"
        }
        message={
          dialogTone === "danger"
            ? "This will remove the source, its schema, and all associated runtime configuration."
            : "The recording session is still active. Stopping now will produce a partial capture."
        }
        objectLabel={
          dialogTone === "danger"
            ? "Assembly Line A telemetry (OPC UA)"
            : "Recording run #run-089"
        }
        impacts={
          dialogTone === "danger"
            ? [
                { label: "Attached recordings", value: "3 recordings will become orphaned" },
                { label: "Active clients", value: "2 clients will be disconnected" },
              ]
            : [{ label: "Captured values", value: "1 284 values saved so far" }]
        }
        reversibilityLabel={
          dialogTone === "danger"
            ? "This action cannot be undone."
            : "The partial recording will be saved and available in Recordings & Samples."
        }
        confirmLabel={dialogTone === "danger" ? "Delete source" : "Stop recording"}
        onConfirm={() => setDialogOpen(false)}
        onClose={() => setDialogOpen(false)}
      />
    </div>
  );
}
