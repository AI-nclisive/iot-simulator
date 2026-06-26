export type EditLockState =
  | { kind: "unlocked" }
  | { kind: "locked-by-other"; owner: string; since: string }
  | { kind: "stale"; owner: string; since: string };

export function EditLockBanner({ lock }: { lock: EditLockState }) {
  if (lock.kind === "unlocked") {
    return null;
  }

  const message =
    lock.kind === "locked-by-other"
      ? `${lock.owner} has this open for editing since ${lock.since}. Your changes are read-only until they finish.`
      : `${lock.owner} started editing at ${lock.since} but may have left. Fields are read-only until the lock clears.`;

  return (
    <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
      <p className="text-sm font-medium text-amber-700">
        {lock.kind === "locked-by-other" ? "Locked by another user" : "Stale edit lock"}
      </p>
      <p className="mt-1 text-sm leading-6 text-shell-muted">{message}</p>
    </section>
  );
}
