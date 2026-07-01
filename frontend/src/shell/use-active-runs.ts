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
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activeRef = useRef(true);

  const fetchRuns = useCallback(async () => {
    if (!projectId) return;

    try {
      const envelope = await apiFetch<ActiveRunsEnvelope>(
        `/api/v1/projects/${projectId}/active-runs`,
      );
      if (activeRef.current) {
        setRuns(envelope.items ?? []);
        setError(null);
      }
    } catch (err) {
      if (activeRef.current) {
        setError(err instanceof Error ? err.message : "Failed to load active runs");
      }
    } finally {
      if (activeRef.current) {
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

    activeRef.current = true;
    setIsLoading(true);

    void fetchRuns();

    const schedule = () => {
      timerRef.current = setTimeout(() => {
        void fetchRuns().then(() => {
          if (activeRef.current) schedule();
        });
      }, POLL_INTERVAL_MS);
    };

    schedule();

    return () => {
      activeRef.current = false;
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [projectId, fetchRuns]);

  return { runs, isLoading, error };
}
