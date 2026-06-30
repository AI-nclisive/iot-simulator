/**
 * use-live-clients.ts — bind a data source's Clients tab to live SSE (UI-098).
 *
 * Subscribes to `GET /api/v1/data-sources/{id}/stream/clients` and translates:
 *   - `clients-snapshot` → array of ClientConnectionDto (replaces the list);
 *   - `CONNECTED` / `DISCONNECTED` deltas → upsert/patch one client.
 * Connection status is surfaced for the UI-095 reconnecting/stale patterns.
 *
 * The backend payload differs from the existing mock SourceClient shape (it has
 * no remote address / read counters — those are not observed yet), so we map to
 * a live-specific row and the tab renders what the backend actually provides,
 * never fabricating read counts.
 */

import { useEffect, useState } from "react";
import { openLiveStream, type LiveStatus } from "../api/live-stream";

/** SSE snapshot payload for one observed client (mirrors api ClientConnectionDto). */
interface ClientConnectionDto {
  clientId: string;
  connectedAt: string;
  disconnectedAt: string | null;
  connected: boolean;
}

/** SSE delta payload for a CONNECTED/DISCONNECTED event. */
interface ClientActivityDelta {
  dataSourceId: string;
  clientId: string;
  kind: string; // "CONNECTED" | "DISCONNECTED"
  at: string;
}

export interface LiveClientRow {
  clientId: string;
  connectedAt: string;
  disconnectedAt: string | null;
  connected: boolean;
}

function isSnapshotEntry(x: unknown): x is ClientConnectionDto {
  return typeof x === "object" && x !== null && "clientId" in x && "connected" in x;
}

function isDelta(x: unknown): x is ClientActivityDelta {
  return typeof x === "object" && x !== null && "clientId" in x && "kind" in x;
}

function toRow(dto: ClientConnectionDto): LiveClientRow {
  return {
    clientId: dto.clientId,
    connectedAt: dto.connectedAt,
    disconnectedAt: dto.disconnectedAt,
    connected: dto.connected,
  };
}

export interface LiveClientsResult {
  rows: LiveClientRow[];
  status: LiveStatus;
}

/**
 * Subscribe to live client connections for `sourceId`. Returns current rows +
 * connection status. `enabled = false` skips the subscription.
 */
export function useLiveClients(sourceId: string, enabled = true): LiveClientsResult {
  const [rows, setRows] = useState<LiveClientRow[]>([]);
  const [status, setStatus] = useState<LiveStatus>("connecting");

  useEffect(() => {
    if (!enabled) return;

    const handle = openLiveStream(`/api/v1/data-sources/${sourceId}/stream/clients`, {
      eventTypes: ["clients-snapshot", "CONNECTED", "DISCONNECTED"],
      onStatus: setStatus,
      onEvent: (type, data) => {
        if (type === "clients-snapshot" && Array.isArray(data)) {
          setRows(data.filter(isSnapshotEntry).map(toRow));
          return;
        }
        if ((type === "CONNECTED" || type === "DISCONNECTED") && isDelta(data)) {
          const connected = type === "CONNECTED";
          setRows((prev) => {
            const idx = prev.findIndex((r) => r.clientId === data.clientId);
            const patched: LiveClientRow = {
              clientId: data.clientId,
              connectedAt: connected ? data.at : (prev[idx]?.connectedAt ?? data.at),
              disconnectedAt: connected ? null : data.at,
              connected,
            };
            if (idx === -1) return [...prev, patched];
            const copy = prev.slice();
            copy[idx] = patched;
            return copy;
          });
        }
      },
    });

    return () => handle.close();
  }, [sourceId, enabled]);

  return { rows, status };
}
