/**
 * use-edit-lease.ts — advisory edit-lock hook (UI-459).
 *
 * Acquires an edit lease on mount via POST, renews it every 60 s (TTL is 300 s),
 * and releases it on unmount via DELETE. Lock is ADVISORY — if the call fails the
 * hook returns "error" state and the consumer continues in editable mode.
 *
 * API contract (IS-081):
 *   POST   /api/v1/projects/{pid}/{objectType}/{objectId}/edit-lease
 *   DELETE /api/v1/projects/{pid}/{objectType}/{objectId}/edit-lease
 *   GET    /api/v1/projects/{pid}/{objectType}/{objectId}/edit-lease
 */

import { useEffect, useRef, useState } from "react";
import { apiFetch } from "../api";

export type EditLeaseObjectType = "data-sources" | "scenarios";

export type LeaseState = "acquiring" | "held" | "locked-by-other" | "error";

export interface EditLeaseResult {
  leaseState: LeaseState;
  /** Populated only when leaseState === "locked-by-other". */
  lockedByHolder: string | null;
}

interface LeaseResponse {
  objectType: string;
  objectId: string;
  holder: string;
  expiresAt: string;
  heldByCurrentUser: boolean;
}

const RENEWAL_INTERVAL_MS = 60_000;

/**
 * Acquires an edit lease for the given object on mount, renews it periodically,
 * and releases it on unmount. Advisory — errors are logged and the caller is
 * allowed to continue editing.
 */
export function useEditLease(
  objectType: EditLeaseObjectType,
  objectId: string,
  projectId: string,
): EditLeaseResult {
  const [leaseState, setLeaseState] = useState<LeaseState>("acquiring");
  const [lockedByHolder, setLockedByHolder] = useState<string | null>(null);

  // Keep the latest state in a ref so the cleanup function can read it without
  // needing to be recreated every render.
  const stateRef = useRef<LeaseState>("acquiring");
  stateRef.current = leaseState;

  useEffect(() => {
    if (!projectId || !objectId) return;

    const path = `/api/v1/projects/${projectId}/${objectType}/${objectId}/edit-lease`;

    function applyResponse(resp: LeaseResponse) {
      if (resp.heldByCurrentUser) {
        setLeaseState("held");
        setLockedByHolder(null);
      } else {
        setLeaseState("locked-by-other");
        setLockedByHolder(resp.holder);
      }
    }

    // Acquire on mount
    apiFetch<LeaseResponse>(path, { method: "POST" })
      .then(applyResponse)
      .catch((err: unknown) => {
        console.warn("[useEditLease] Failed to acquire lease:", err);
        setLeaseState("error");
      });

    // Renew every 60 s
    const intervalId = setInterval(() => {
      // Only bother renewing if we currently hold the lease.
      if (stateRef.current !== "held") return;
      apiFetch<LeaseResponse>(path, { method: "POST" })
        .then(applyResponse)
        .catch((err: unknown) => {
          console.warn("[useEditLease] Failed to renew lease:", err);
          // Do not flip to "error" on a renewal failure — advisory only.
        });
    }, RENEWAL_INTERVAL_MS);

    return () => {
      clearInterval(intervalId);
      // Release fire-and-forget; we only attempt release if we held the lease.
      if (stateRef.current === "held") {
        // Use Promise.resolve() to be defensive against test mocks that may not
        // return a real Promise when the mock queue is exhausted.
        Promise.resolve(apiFetch(path, { method: "DELETE" })).catch((err: unknown) => {
          console.warn("[useEditLease] Failed to release lease:", err);
        });
      }
    };
  }, [objectType, objectId, projectId]);

  return { leaseState, lockedByHolder };
}
