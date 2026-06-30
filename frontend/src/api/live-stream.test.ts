/**
 * Tests for the live-stream EventSource layer (UI-098).
 *
 * jsdom has no EventSource, so we install a controllable fake on globalThis
 * that lets each test drive open/message/error and assert reconnect + stale
 * behaviour deterministically with fake timers.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { openLiveStream, type LiveStatus } from "./live-stream";

// ── Fake EventSource ─────────────────────────────────────────────────────────

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  url: string;
  withCredentials: boolean;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((ev: MessageEvent) => void)[]>();

  constructor(url: string, init?: { withCredentials?: boolean }) {
    this.url = url;
    this.withCredentials = init?.withCredentials ?? false;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, fn: (ev: MessageEvent) => void): void {
    const arr = this.listeners.get(type) ?? [];
    arr.push(fn);
    this.listeners.set(type, arr);
  }

  close(): void {
    this.closed = true;
  }

  // ── test drivers ──
  emitOpen(): void {
    this.onopen?.();
  }

  emit(type: string, data: string): void {
    for (const fn of this.listeners.get(type) ?? []) {
      fn({ data } as MessageEvent);
    }
  }

  emitError(): void {
    this.onerror?.();
  }

  static latest(): FakeEventSource {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1];
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeEventSource.reset();
  vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

// ── connection + events ──────────────────────────────────────────────────────

describe("openLiveStream — events", () => {
  it("opens an EventSource at the API path and reports open", () => {
    const statuses: LiveStatus[] = [];
    openLiveStream("/api/v1/data-sources/src-01/stream/values", {
      eventTypes: ["values-snapshot", "values"],
      onEvent: () => {},
      onStatus: (s) => statuses.push(s),
    });

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.latest().url).toContain("/stream/values");

    FakeEventSource.latest().emitOpen();
    expect(statuses).toContain("open");
  });

  it("parses JSON payloads and forwards them by event type", () => {
    const received: Array<{ type: string; data: unknown }> = [];
    openLiveStream("/x", {
      eventTypes: ["values-snapshot", "values"],
      onEvent: (type, data) => received.push({ type, data }),
    });
    FakeEventSource.latest().emitOpen();

    FakeEventSource.latest().emit("values-snapshot", JSON.stringify([{ nodeId: "n1" }]));
    FakeEventSource.latest().emit("values", JSON.stringify({ nodeId: "n1", value: "42" }));

    expect(received).toEqual([
      { type: "values-snapshot", data: [{ nodeId: "n1" }] },
      { type: "values", data: { nodeId: "n1", value: "42" } },
    ]);
  });

  it("passes non-JSON payloads through unchanged", () => {
    const received: unknown[] = [];
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: (_t, data) => received.push(data),
    });
    FakeEventSource.latest().emitOpen();
    FakeEventSource.latest().emit("values", "not-json");
    expect(received).toEqual(["not-json"]);
  });
});

// ── reconnect + backoff ────────────────────────────────────────────────────────

describe("openLiveStream — reconnect with backoff", () => {
  it("reconnects after an error and reports reconnecting", () => {
    const statuses: LiveStatus[] = [];
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      onStatus: (s) => statuses.push(s),
      baseDelayMs: 1_000,
      staleAfterMs: 0,
    });
    FakeEventSource.latest().emitOpen();
    expect(FakeEventSource.instances).toHaveLength(1);

    FakeEventSource.latest().emitError();
    expect(statuses).toContain("reconnecting");

    // First backoff is ~1000ms (+/- jitter); advance generously.
    vi.advanceTimersByTime(2_000);
    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it("resets backoff after a successful reopen", () => {
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      baseDelayMs: 1_000,
      staleAfterMs: 0,
    });
    // drop #1 → reconnect
    FakeEventSource.latest().emitError();
    vi.advanceTimersByTime(2_000);
    expect(FakeEventSource.instances).toHaveLength(2);
    // success resets attempt
    FakeEventSource.latest().emitOpen();
    // drop #2 should again reconnect after ~base delay (not a grown delay)
    FakeEventSource.latest().emitError();
    vi.advanceTimersByTime(2_000);
    expect(FakeEventSource.instances).toHaveLength(3);
  });

  it("does not reconnect after close()", () => {
    const handle = openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      staleAfterMs: 0,
    });
    FakeEventSource.latest().emitOpen();
    handle.close();
    expect(handle.status()).toBe("closed");

    FakeEventSource.latest().emitError();
    vi.advanceTimersByTime(60_000);
    expect(FakeEventSource.instances).toHaveLength(1);
  });
});

// ── stale handling ─────────────────────────────────────────────────────────────

describe("openLiveStream — stale handling", () => {
  it("marks stale after the window with no traffic", () => {
    const statuses: LiveStatus[] = [];
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      onStatus: (s) => statuses.push(s),
      staleAfterMs: 15_000,
    });
    FakeEventSource.latest().emitOpen();

    vi.advanceTimersByTime(15_000);
    expect(statuses).toContain("stale");
  });

  it("heartbeat keeps the stream from going stale", () => {
    const statuses: LiveStatus[] = [];
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      onStatus: (s) => statuses.push(s),
      staleAfterMs: 15_000,
    });
    FakeEventSource.latest().emitOpen();

    // Heartbeat at 10s resets the window; at 20s total we should NOT be stale yet.
    vi.advanceTimersByTime(10_000);
    FakeEventSource.latest().emit("heartbeat", "");
    vi.advanceTimersByTime(10_000);
    expect(statuses).not.toContain("stale");
  });

  it("recovers from stale to open when an event arrives", () => {
    const statuses: LiveStatus[] = [];
    openLiveStream("/x", {
      eventTypes: ["values"],
      onEvent: () => {},
      onStatus: (s) => statuses.push(s),
      staleAfterMs: 15_000,
    });
    FakeEventSource.latest().emitOpen();
    vi.advanceTimersByTime(15_000);
    expect(statuses).toContain("stale");

    FakeEventSource.latest().emit("values", JSON.stringify({ v: 1 }));
    // back to open
    expect(statuses[statuses.length - 1]).toBe("open");
  });
});
