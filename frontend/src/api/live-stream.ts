/**
 * live-stream.ts — EventSource subscription layer (UI-098)
 *
 * Drives live surfaces from server-sent events (SSE) instead of static mocks.
 * Wraps the browser `EventSource` with:
 *   - typed, named-event subscriptions matching the backend contract
 *     (`values-snapshot` / `values`, `clients-snapshot` / `clients`,
 *     `runtime-snapshot` / `runtime`, plus transport `heartbeat`);
 *   - automatic reconnect with exponential backoff + jitter;
 *   - stale detection (no event — including heartbeat — within a window);
 *   - `Last-Event-ID` resume via the browser's native handling.
 *
 * Backend contract (see backend-specs/05_API_CONTRACT.md, IS-046/051/052/055):
 *   GET /api/v1/data-sources/{id}/stream/values   → values-snapshot, values
 *   GET /api/v1/data-sources/{id}/stream/clients   → clients-snapshot, clients
 *   GET /api/v1/projects/{pid}/stream/runtime      → runtime-snapshot, runtime
 * The server sends a snapshot on connect (full current state) followed by
 * deltas. `id:` lines carry a per-stream monotonic seq; transport events
 * (heartbeat, resync) omit it so they don't advance Last-Event-ID.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

/** Connection lifecycle as surfaced to the UI (maps to UI-002/UI-095 states). */
export type LiveStatus = "connecting" | "open" | "stale" | "reconnecting" | "closed";

export interface LiveStreamOptions {
  /** Named SSE events to listen for, e.g. ["values-snapshot", "values"]. */
  eventTypes: string[];
  /** Parsed payload of a named event. */
  onEvent: (type: string, data: unknown) => void;
  /** Connection status changes (drive toast/banner via UI-095). */
  onStatus?: (status: LiveStatus) => void;
  /** First reconnect delay, ms (default 1000). */
  baseDelayMs?: number;
  /** Maximum reconnect delay, ms (default 30000). */
  maxDelayMs?: number;
  /**
   * If no event (data or heartbeat) arrives within this window, the stream is
   * marked `stale`. Default 15000 ms — the backend heartbeat cadence is well
   * under this, so silence means a real problem. Set 0 to disable.
   */
  staleAfterMs?: number;
}

export interface LiveStreamHandle {
  /** Permanently close the stream and stop reconnecting. */
  close: () => void;
  /** Current status (also delivered via onStatus). */
  status: () => LiveStatus;
}

/**
 * Open a live SSE subscription to `path` (relative to the API base URL).
 * Returns a handle; call `close()` when the surface unmounts.
 */
export function openLiveStream(path: string, options: LiveStreamOptions): LiveStreamHandle {
  const {
    eventTypes,
    onEvent,
    onStatus,
    baseDelayMs = 1_000,
    maxDelayMs = 30_000,
    staleAfterMs = 15_000,
  } = options;

  const url = `${BASE_URL}${path}`;
  let source: EventSource | null = null;
  let attempt = 0;
  let closedByCaller = false;
  let everOpened = false;
  let status: LiveStatus = "connecting";
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let staleTimer: ReturnType<typeof setTimeout> | null = null;

  function setStatus(next: LiveStatus): void {
    if (status === next) return;
    status = next;
    onStatus?.(next);
  }

  function clearStaleTimer(): void {
    if (staleTimer !== null) {
      clearTimeout(staleTimer);
      staleTimer = null;
    }
  }

  // Any inbound traffic (named event or heartbeat) proves the link is alive,
  // so it clears `stale` and restarts the stale countdown.
  function bumpActivity(): void {
    clearStaleTimer();
    if (status === "stale") {
      setStatus("open");
    }
    if (staleAfterMs > 0) {
      staleTimer = setTimeout(() => setStatus("stale"), staleAfterMs);
    }
  }

  function backoffDelay(): number {
    // Exponential: base * 2^attempt, capped, with up to ±25% jitter to avoid
    // a thundering herd of clients reconnecting in lockstep.
    const exp = Math.min(maxDelayMs, baseDelayMs * 2 ** attempt);
    const jitter = exp * 0.25 * (Math.random() * 2 - 1);
    return Math.max(0, Math.round(exp + jitter));
  }

  function scheduleReconnect(): void {
    if (closedByCaller) return;
    setStatus(everOpened ? "reconnecting" : "connecting");
    const delay = backoffDelay();
    attempt += 1;
    reconnectTimer = setTimeout(connect, delay);
  }

  function connect(): void {
    if (closedByCaller) return;
    reconnectTimer = null;

    // EventSource resumes from Last-Event-ID automatically on its own internal
    // reconnect, but we manage reconnect ourselves (for backoff + status), so
    // we recreate it. The browser still sends Last-Event-ID it last saw within
    // a single EventSource lifetime; across our manual recreation the server's
    // snapshot-on-connect is the resync, which is the documented contract.
    source = new EventSource(url, { withCredentials: false });

    source.onopen = () => {
      attempt = 0;
      everOpened = true;
      setStatus("open");
      bumpActivity();
    };

    source.onerror = () => {
      // EventSource fires onerror on transient drops; we take over reconnection.
      if (source) {
        source.close();
        source = null;
      }
      clearStaleTimer();
      scheduleReconnect();
    };

    const handler = (type: string) => (ev: MessageEvent) => {
      bumpActivity();
      let parsed: unknown = ev.data;
      try {
        parsed = JSON.parse(ev.data);
      } catch {
        // Non-JSON payloads are passed through as-is (e.g. heartbeat comments).
      }
      onEvent(type, parsed);
    };

    for (const type of eventTypes) {
      source.addEventListener(type, handler(type));
    }
    // Heartbeat keeps the stale timer at bay even when there are no data deltas.
    source.addEventListener("heartbeat", () => bumpActivity());
  }

  connect();

  return {
    close() {
      closedByCaller = true;
      if (reconnectTimer !== null) clearTimeout(reconnectTimer);
      clearStaleTimer();
      if (source) {
        source.close();
        source = null;
      }
      setStatus("closed");
    },
    status: () => status,
  };
}
