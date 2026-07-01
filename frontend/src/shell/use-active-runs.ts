/**
 * use-active-runs.ts — poll GET /api/v1/projects/{projectId}/active-runs (UI-111).
 *
 * Returns the list of active runs for the current project, refreshed every
 * 5 seconds. When projectId is null the request is skipped.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../api/client";

export interface ActiveRunResponse {
  id: string;
  label: string;
  processType: "Recording" | "Replay" | "Scenario";
  runState: "running" | "queued" | "failed" | "completed" | "stopped";
  startedAt: string;
  initiator: string;
  relatedSourceId: string | null;
  relatedLabel: string | null;
}

interface ActiveRunsResult {
  runs: ActiveRunResponse[];
  isLoading: boolean;
  error: string | null;
}

interface ActiveRunsEnvelope {
  items: ActiveRunResponse[];
}

const POLL_INTERVAL_MS = 5_000;

export function useActiveRuns(projectId: string | null): ActiveRunsResult {
  const [runs, setRuns] = useState<ActiveRunResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(!!projectId);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchRuns = useCallback(async (signal?: AbortSignal) => {
    if (!projectId) return;

    try {
      const envelope = await apiFetch<ActiveRunsEnvelope>(
        `/api/v1/projects/${projectId}/active-runs`,
        { signal },
      );
      if (!signal?.aborted) {
        setRuns(envelope.items ?? []);
        setError(null);
      }
    } catch (err) {
      if (!signal?.aborted) {
        setError(err instanceof Error ? err.message : "Failed to load active runs");
      }
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) {
      setRuns([]);
      setIsLoading(false);
      setError(null);
      return;
    }

    setIsLoading(true);

    const controller = new AbortController();

    void fetchRuns(controller.signal);

    const schedule = () => {
      timerRef.current = setTimeout(() => {
        void fetchRuns(controller.signal).then(() => {
          if (!controller.signal.aborted) schedule();
        });
      }, POLL_INTERVAL_MS);
    };

    schedule();

    return () => {
      controller.abort();
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [projectId, fetchRuns]);

  return { runs, isLoading, error };
}
