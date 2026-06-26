export type EditLockState =
  | { kind: "unlocked" }
  | { kind: "locked-by-self"; since: string; onRelease: () => void }
  | { kind: "locked-by-other"; owner: string; since: string }
  | { kind: "stale"; owner: string; since: string; onTake: () => void };

type EditLockBannerProps = {
  lock: EditLockState;
};

export function EditLockBanner({ lock }: EditLockBannerProps) {
  if (lock.kind === "unlocked") {
    return null;
  }

  if (lock.kind === "locked-by-self") {
    return (
      <div className="flex items-center justify-between rounded-md border border-shell-accent/30 bg-shell-accent/5 px-4 py-3" role="status">
        <div className="flex items-center gap-3">
          <span className="h-2 w-2 rounded-full bg-shell-accent" aria-hidden="true" />
          <p className="text-sm text-shell-ink">
            You are editing.{" "}
            <span className="text-shell-muted">Lock acquired {lock.since}.</span>
          </p>
        </div>
        <button
          className="shell-text-action shrink-0"
          type="button"
          onClick={lock.onRelease}
        >
          Release lock
        </button>
      </div>
    );
  }

  if (lock.kind === "locked-by-other") {
    return (
      <div
        className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3"
        role="status"
      >
        <p className="text-sm text-amber-800">
          <span className="font-medium">{lock.owner}</span> is currently editing.
          This view is read-only until they release the lock.{" "}
          <span className="text-amber-600">Lock acquired {lock.since}.</span>
        </p>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-between rounded-md border border-amber-200 bg-amber-50 px-4 py-3" role="status">
      <p className="text-sm text-amber-800">
        <span className="font-medium">{lock.owner}</span> held this lock since{" "}
        {lock.since} and appears inactive. You can take over editing.
      </p>
      <button
        className="shell-text-action shrink-0 text-amber-700 hover:text-amber-900"
        type="button"
        onClick={lock.onTake}
      >
        Take lock
      </button>
    </div>
  );
}
