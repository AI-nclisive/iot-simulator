import { StatusBadge, type StatusTone } from "./status-badge";

type SharedUiState = "loading" | "empty" | "error" | "stale" | "locked" | "warning";

type SharedStatePanelProps = {
  state: SharedUiState;
  title?: string;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  secondaryActionLabel?: string;
  onSecondaryAction?: () => void;
};

const stateMeta: Record<
  SharedUiState,
  {
    badge: string;
    title: string;
    tone: StatusTone;
  }
> = {
  loading: {
    badge: "Loading",
    title: "Content is loading.",
    tone: "neutral",
  },
  empty: {
    badge: "Empty",
    title: "Nothing is here yet.",
    tone: "neutral",
  },
  error: {
    badge: "Error",
    title: "Something went wrong.",
    tone: "danger",
  },
  stale: {
    badge: "Stale",
    title: "This view may be out of date.",
    tone: "warning",
  },
  locked: {
    badge: "Locked",
    title: "Editing is blocked right now.",
    tone: "warning",
  },
  warning: {
    badge: "Warning",
    title: "Attention is needed.",
    tone: "warning",
  },
};

export function SharedStateBadge({
  state,
  label,
}: {
  state: SharedUiState;
  label?: string;
}) {
  return <StatusBadge label={label ?? stateMeta[state].badge} tone={stateMeta[state].tone} />;
}

export function SharedStatePanel({
  state,
  title,
  message,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
}: SharedStatePanelProps) {
  const meta = stateMeta[state];

  return (
    <section className="rounded-md border border-shell-line bg-white px-4 py-4">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <SharedStateBadge state={state} />
          <h3 className="mt-3 text-sm font-medium text-shell-ink">{title ?? meta.title}</h3>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-shell-muted">{message}</p>
        </div>

        {actionLabel || secondaryActionLabel ? (
          <div className="flex flex-wrap gap-2">
            {actionLabel ? (
              <button className="shell-action" type="button" onClick={onAction}>
                {actionLabel}
              </button>
            ) : null}
            {secondaryActionLabel ? (
              <button className="shell-action" type="button" onClick={onSecondaryAction}>
                {secondaryActionLabel}
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}

export type { SharedUiState };
