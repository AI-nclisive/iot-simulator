/**
 * use-live-runtime.ts — bind Runtime Dashboard + Project Overview to live SSE (UI-098).
 *
 * Subscribes to `GET /api/v1/projects/{projectId}/stream/runtime` and tracks
 * per-source runtime state:
 *   - `runtime-state` snapshot → array of { dataSourceId, state, lastError };
 *   - activity deltas (named by event type, payload { dataSourceId, type, at,
 *     detail, origin }) → recorded as the latest runtime event and, when the
 *     event type is a known runtime state, used to update that source's state.
 *
 * Backend runtime states (RUNNING/STOPPED/STARTING/ERROR/STALE) are mapped to
 * the UI status/health vocabulary via the shared UI-096 mappers, so this hook
 * stays the single translation point.
 */

import { useEffect, useState } from "react";
import { openLiveStream, type LiveStatus } from "../api/live-stream";
import {
  mapRuntimeStateToHealth,
  mapRuntimeStateToStatus,
  type BackendRuntimeState,
} from "../api/mappers";

interface SourceError {
  type?: string;
  detail?: string;
  at?: string;
}

/** SSE `runtime-state` snapshot entry (mirrors api SourceRuntimeState). */
interface SourceRuntimeStateDto {
  dataSourceId: string;
  state: string;
  lastError: SourceError | null;
}

/** A runtime activity delta as published on the runtime stream. */
interface RuntimeActivityDelta {
  dataSourceId: string;
  type: string;
  at: string;
  detail?: string;
  origin?: string;
}

/** One source's live runtime state, in UI vocabulary. */
export interface LiveRuntimeSource {
  dataSourceId: string;
  rawState: string;
  status: "Active" | "Stopped";
  health: "Healthy" | "Warning" | "Error" | null;
  lastError: SourceError | null;
}

/** A runtime event for the dashboard's recent-activity / overview feed. */
export interface LiveRuntimeEvent {
  dataSourceId: string;
  type: string;
  at: string;
  detail?: string;
  origin?: string;
}

const KNOWN_STATES = new Set<BackendRuntimeState>([
  "STOPPED",
  "STARTING",
  "RUNNING",
  "ERROR",
  "STALE",
]);

function isKnownState(s: string): s is BackendRuntimeState {
  return KNOWN_STATES.has(s as BackendRuntimeState);
}

function toSource(dto: SourceRuntimeStateDto): LiveRuntimeSource {
  if (isKnownState(dto.state)) {
    return {
      dataSourceId: dto.dataSourceId,
      rawState: dto.state,
      status: mapRuntimeStateToStatus(dto.state),
      health: mapRuntimeStateToHealth(dto.state),
      lastError: dto.lastError,
    };
  }
  // Unknown/unmapped state: surface raw, treat as stopped with no health signal.
  return {
    dataSourceId: dto.dataSourceId,
    rawState: dto.state,
    status: "Stopped",
    health: null,
    lastError: dto.lastError,
  };
}

function isSnapshotEntry(x: unknown): x is SourceRuntimeStateDto {
  return typeof x === "object" && x !== null && "dataSourceId" in x && "state" in x;
}

function isDelta(x: unknown): x is RuntimeActivityDelta {
  return typeof x === "object" && x !== null && "dataSourceId" in x && "type" in x;
}

export interface LiveRuntimeResult {
  sources: LiveRuntimeSource[];
  /** Most-recent-first runtime events (capped). */
  events: LiveRuntimeEvent[];
  status: LiveStatus;
}

const MAX_EVENTS = 100;

/**
 * Subscribe to a project's live runtime stream. Returns per-source state, a
 * capped recent-event list, and connection status. `enabled = false` skips it.
 */
export function useLiveRuntime(projectId: string, enabled = true): LiveRuntimeResult {
  const [sources, setSources] = useState<LiveRuntimeSource[]>([]);
  const [events, setEvents] = useState<LiveRuntimeEvent[]>([]);
  const [status, setStatus] = useState<LiveStatus>("connecting");

  useEffect(() => {
    if (!enabled) return;

    const handle = openLiveStream(`/api/v1/projects/${projectId}/stream/runtime`, {
      // The snapshot is a named event; deltas use varied type names, so we also
      // attach a small set of known runtime-state event names. Unknown delta
      // types still arrive as long as the backend names them; we list the
      // documented ones explicitly.
      eventTypes: [
        "runtime-state",
        "STARTED",
        "STOPPED",
        "ERROR",
        "STALE",
        "RECOVERED",
        "STATE_CHANGED",
      ],
      onStatus: setStatus,
      onEvent: (type, data) => {
        if (type === "runtime-state" && Array.isArray(data)) {
          setSources(data.filter(isSnapshotEntry).map(toSource));
          return;
        }
        if (isDelta(data)) {
          const ev: LiveRuntimeEvent = {
            dataSourceId: data.dataSourceId,
            type: data.type ?? type,
            at: data.at,
            detail: data.detail,
            origin: data.origin,
          };
          setEvents((prev) => [ev, ...prev].slice(0, MAX_EVENTS));

          // If the delta's type is itself a runtime state, reflect it on the source.
          if (isKnownState(ev.type)) {
            setSources((prev) => {
              const idx = prev.findIndex((s) => s.dataSourceId === ev.dataSourceId);
              const updated = toSource({
                dataSourceId: ev.dataSourceId,
                state: ev.type,
                lastError:
                  ev.type === "ERROR"
                    ? { type: ev.type, detail: ev.detail, at: ev.at }
                    : (prev[idx]?.lastError ?? null),
              });
              if (idx === -1) return [...prev, updated];
              const copy = prev.slice();
              copy[idx] = updated;
              return copy;
            });
          }
        }
      },
    });

    return () => handle.close();
  }, [projectId, enabled]);

  return { sources, events, status };
}
