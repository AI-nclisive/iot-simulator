/**
 * data-source-rescan-panel.tsx — "Rescan tags" for an already-created SCAN-basis data
 * source: reuses the source's stored connection details (no re-entry needed, backend
 * pulls them from the same credential store RecordingService reuses for live capture)
 * to re-run discovery and save the result as a new schema version on the existing
 * source id.
 *
 * Mirrors the create wizard's scan step (create-data-source-wizard-page.tsx) at a
 * smaller scale — same 2s poll cadence and status handling, reusing its
 * `UnknownNodesList`/`fetchAllScanNodes` instead of duplicating them.
 */

import { useEffect, useRef, useState } from "react";
import { apiFetch, ApiError } from "../api";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useNotificationStore } from "../shell/notification-store";
import type { DataSourceRow } from "../shell/data-sources-store";
import {
  UnknownNodesList,
  fetchAllScanNodes,
  type DiscoveredNodeResponse,
  type TypeResolutionEntry,
} from "./create-data-source-wizard-page";

type RescanJobStatus =
  | "RUNNING"
  | "OK"
  | "PARTIAL"
  | "UNREACHABLE"
  | "AUTH_FAILURE"
  | "UNSUPPORTED"
  | "FAILED"
  | "CANCELLED";

type RescanJobResult = {
  jobId: string;
  status: RescanJobStatus;
  discoveredSoFar: number;
  discoveredCount: number;
  unknownCount: number;
  message: string | null;
};

type PanelStatus = "idle" | "scanning" | "resolving" | "applying" | "error";

const POLL_INTERVAL_MS = 2000;

export function DataSourceRescanPanel({
  source,
  projectId,
}: {
  source: DataSourceRow;
  projectId: string;
}) {
  const push = useNotificationStore((s) => s.push);
  const applyRescan = useDataSourcesStore((s) => s.applyRescan);

  const [status, setStatus] = useState<PanelStatus>("idle");
  const [jobId, setJobId] = useState<string | null>(null);
  const [discoveredSoFar, setDiscoveredSoFar] = useState(0);
  const [nodes, setNodes] = useState<DiscoveredNodeResponse[]>([]);
  const [summary, setSummary] = useState<{ discoveredCount: number; unknownCount: number } | null>(null);
  const [typeResolutions, setTypeResolutions] = useState<TypeResolutionEntry[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const handlingTerminalRef = useRef(false);

  useEffect(() => {
    return () => {
      if (pollRef.current !== null) clearInterval(pollRef.current);
    };
  }, []);

  function reset() {
    if (pollRef.current !== null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
    handlingTerminalRef.current = false;
    setStatus("idle");
    setJobId(null);
    setDiscoveredSoFar(0);
    setNodes([]);
    setSummary(null);
    setTypeResolutions([]);
    setErrorMessage(null);
  }

  function pollJob(id: string) {
    pollRef.current = setInterval(async () => {
      if (handlingTerminalRef.current) return;
      try {
        const result = await apiFetch<RescanJobResult>(
          `/api/v1/projects/${projectId}/data-sources/scan/${id}`,
        );
        if (handlingTerminalRef.current) return;
        if (result.status === "OK" || result.status === "PARTIAL") {
          handlingTerminalRef.current = true;
          if (pollRef.current !== null) {
            clearInterval(pollRef.current);
            pollRef.current = null;
          }
          let fetched: DiscoveredNodeResponse[];
          try {
            fetched = await fetchAllScanNodes(projectId, id);
          } catch {
            setStatus("error");
            setErrorMessage("Failed to load discovered nodes");
            return;
          }
          const unknown = fetched.filter((n) => n.unknownType);
          setNodes(fetched);
          setSummary({ discoveredCount: result.discoveredCount, unknownCount: result.unknownCount });
          setTypeResolutions(
            unknown.map((n) => ({
              nodeId: n.nodeId,
              dataType: "",
              valueRank: n.valueRank ?? 1,
              access: n.access ?? "READ",
              exclude: false,
            })),
          );
          setStatus("resolving");
        } else if (
          result.status === "UNREACHABLE" ||
          result.status === "AUTH_FAILURE" ||
          result.status === "UNSUPPORTED" ||
          result.status === "FAILED" ||
          result.status === "CANCELLED"
        ) {
          if (pollRef.current !== null) {
            clearInterval(pollRef.current);
            pollRef.current = null;
          }
          setStatus("error");
          setErrorMessage(result.message ?? "Rescan failed");
        } else {
          setDiscoveredSoFar(result.discoveredSoFar);
        }
      } catch {
        if (pollRef.current !== null) {
          clearInterval(pollRef.current);
          pollRef.current = null;
        }
        setStatus("error");
        setErrorMessage("Failed to poll rescan status");
      }
    }, POLL_INTERVAL_MS);
  }

  async function startRescan() {
    reset();
    setStatus("scanning");
    try {
      const job = await apiFetch<{ jobId: string; status: string }>(
        `/api/v1/projects/${projectId}/data-sources/${source.id}/rescan`,
        { method: "POST" },
      );
      setJobId(job.jobId);
      pollJob(job.jobId);
    } catch (err) {
      setStatus("error");
      setErrorMessage(err instanceof ApiError ? (err.detail ?? err.message) : "Failed to start rescan");
    }
  }

  const unresolvedCount = typeResolutions.filter((r) => !r.exclude && !r.dataType).length;
  const canApply = status === "resolving" && unresolvedCount === 0;

  async function apply() {
    if (!jobId || !canApply) return;
    setStatus("applying");
    try {
      await applyRescan(source.id, jobId, typeResolutions, projectId);
      push({ tone: "success", title: "Rescan applied", message: "The schema was updated from the rescan." });
      reset();
    } catch (err) {
      setStatus("error");
      setErrorMessage(
        err instanceof ApiError ? (err.detail ?? err.message) : "Failed to apply the rescan",
      );
    }
  }

  return (
    <div className="space-y-3 rounded-md border border-shell-line bg-shell-base/30 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-shell-ink">Rescan tags</p>
          <p className="text-xs text-shell-muted">
            Re-scans this source's real endpoint and updates the schema with what's discovered now.
          </p>
        </div>
        {status === "idle" || status === "error" ? (
          <button className="shell-action" type="button" onClick={() => void startRescan()}>
            {status === "error" ? "Retry rescan" : "Rescan tags"}
          </button>
        ) : null}
      </div>

      {status === "scanning" ? (
        <p className="text-sm text-shell-muted">Scanning… {discoveredSoFar} discovered so far.</p>
      ) : null}

      {status === "error" ? <p className="text-sm text-shell-danger">{errorMessage}</p> : null}

      {(status === "resolving" || status === "applying") && summary ? (
        <div className="space-y-3">
          <p className="text-sm text-shell-muted">
            {summary.discoveredCount} node{summary.discoveredCount === 1 ? "" : "s"} discovered
            {summary.unknownCount > 0
              ? ` — ${summary.unknownCount} need${summary.unknownCount === 1 ? "s" : ""} a type below.`
              : "."}
          </p>
          {summary.unknownCount > 0 ? (
            <UnknownNodesList
              nodes={nodes.filter((n) => n.unknownType)}
              typeResolutionsByNodeId={new Map(typeResolutions.map((r) => [r.nodeId, r]))}
              onChange={(nodeId, patch) =>
                setTypeResolutions((prev) =>
                  prev.map((r) => (r.nodeId === nodeId ? { ...r, ...patch } : r)),
                )
              }
            />
          ) : null}
          <div className="flex items-center gap-2">
            <button
              className="shell-action"
              disabled={status === "applying" || !canApply}
              type="button"
              onClick={() => void apply()}
            >
              {status === "applying" ? "Applying…" : "Apply rescan"}
            </button>
            <button className="shell-action" disabled={status === "applying"} type="button" onClick={reset}>
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
