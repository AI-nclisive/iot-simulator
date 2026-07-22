/**
 * use-live-values.ts — bind a data source's Values tab to the live SSE stream (UI-098).
 *
 * Subscribes to `GET /api/v1/data-sources/{id}/stream/values` and translates the
 * backend `values-snapshot` / `values` events (StreamValue shape) into the
 * existing `SourceValueRow` store rows. Connection status is surfaced so the tab
 * can show reconnecting / stale via the UI-095 notification pattern.
 *
 * The snapshot replaces the current rows for the source; subsequent `values`
 * deltas update-or-insert by nodeId. When the stream goes stale, rows are marked
 * `No updates` (UI-002 stale convention) without being dropped.
 */

import { useEffect, useState } from "react";
import { openLiveStream, type LiveStatus } from "../api/live-stream";
import type { SourceValueRow } from "../surfaces/mock-source-values";

/** Backend SSE payload for one live value (mirrors api StreamValue record). */
interface StreamValue {
  nodeId: string;
  value: unknown;
  quality: string;
  qualityReason: string | null;
  sourceTime: string;
}

function isStreamValue(x: unknown): x is StreamValue {
  return typeof x === "object" && x !== null && "nodeId" in x;
}

/**
 * Keep live readings scannable without losing the source precision: the unrounded
 * representation is retained in `exactValue` and exposed by the table as a title.
 */
export function formatLiveValue(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "number") {
    if (!Number.isFinite(value)) return String(value);
    return new Intl.NumberFormat("en-GB", {
      maximumFractionDigits: Number.isInteger(value) ? 0 : 4,
      useGrouping: true,
    }).format(value);
  }
  return String(value);
}

function formatTime(sourceTime: string): string {
  // Backend sends an ISO instant; show wall-clock HH:MM:SS to match the tab.
  const d = new Date(sourceTime);
  return Number.isNaN(d.getTime())
    ? sourceTime
    : d.toLocaleTimeString("en-GB", { hour12: false });
}

function toRow(sourceId: string, v: StreamValue): SourceValueRow {
  return {
    id: `${sourceId}:${v.nodeId}`,
    sourceId,
    path: v.nodeId,
    // The stream does not carry a UI dataType; default to string and let a later
    // schema join refine it. Never fabricate a type we don't have.
    dataType: "string",
    currentValue: formatLiveValue(v.value),
    exactValue: v.value === null || v.value === undefined ? undefined : String(v.value),
    updatedAt: formatTime(v.sourceTime),
    freshness: v.quality === "GOOD" ? "Live" : "No updates",
    pinned: false,
  };
}

export interface LiveValuesResult {
  rows: SourceValueRow[];
  status: LiveStatus;
}

/**
 * Subscribe to live values for `sourceId`. Returns current rows + connection
 * status. Pass `enabled = false` to keep the tab on whatever it had (e.g. when
 * the source is not running).
 */
export function useLiveValues(sourceId: string, enabled = true): LiveValuesResult {
  const [rows, setRows] = useState<SourceValueRow[]>([]);
  const [status, setStatus] = useState<LiveStatus>("connecting");

  useEffect(() => {
    if (!enabled) return;

    const handle = openLiveStream(`/api/v1/data-sources/${sourceId}/stream/values`, {
      eventTypes: ["values-snapshot", "values"],
      onStatus: (s) => {
        setStatus(s);
        if (s === "stale") {
          // Keep rows but mark them as no-longer-fresh.
          setRows((prev) => prev.map((r) => ({ ...r, freshness: "No updates" })));
        }
      },
      onEvent: (type, data) => {
        if (type === "values-snapshot" && Array.isArray(data)) {
          const next = data.filter(isStreamValue).map((v) => toRow(sourceId, v));
          setRows(next);
          return;
        }
        if (type === "values") {
          // Publishers batch changed nodes into one SSE event. Accepting a single
          // value too keeps the consumer compatible with older workers.
          const updates = Array.isArray(data)
            ? data.filter(isStreamValue)
            : isStreamValue(data)
              ? [data]
              : [];
          if (updates.length === 0) return;
          setRows((prev) => {
            const next = prev.slice();
            const indexById = new Map(next.map((row, index) => [row.id, index]));
            for (const update of updates) {
              const row = toRow(sourceId, update);
              const index = indexById.get(row.id);
              if (index === undefined) {
                indexById.set(row.id, next.length);
                next.push(row);
              } else {
                next[index] = row;
              }
            }
            return next;
          });
        }
      },
    });

    return () => handle.close();
  }, [sourceId, enabled]);

  return { rows, status };
}
