/**
 * Tests for useLiveClients (UI-098) — SSE clients stream → client rows.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useLiveClients } from "./use-live-clients";

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((ev: MessageEvent) => void)[]>();
  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }
  addEventListener(t: string, fn: (ev: MessageEvent) => void) {
    const a = this.listeners.get(t) ?? [];
    a.push(fn);
    this.listeners.set(t, a);
  }
  close() {
    this.closed = true;
  }
  emitOpen() {
    this.onopen?.();
  }
  emit(t: string, data: string) {
    for (const fn of this.listeners.get(t) ?? []) fn({ data } as MessageEvent);
  }
  static latest() {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1];
  }
  static reset() {
    FakeEventSource.instances = [];
  }
}

beforeEach(() => {
  vi.useFakeTimers();
  FakeEventSource.reset();
  vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("useLiveClients", () => {
  it("populates rows from a clients-snapshot", () => {
    const { result } = renderHook(() => useLiveClients("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "clients-snapshot",
        JSON.stringify([
          { clientId: "c1", connectedAt: "2026-06-30T09:00:00Z", disconnectedAt: null, connected: true },
          { clientId: "c2", connectedAt: "2026-06-30T09:01:00Z", disconnectedAt: null, connected: true },
        ]),
      );
    });
    expect(result.current.rows).toHaveLength(2);
    expect(result.current.rows[0]).toMatchObject({ clientId: "c1", connected: true });
  });

  it("appends a client on CONNECTED", () => {
    const { result } = renderHook(() => useLiveClients("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit("clients-snapshot", JSON.stringify([]));
      FakeEventSource.latest().emit(
        "CONNECTED",
        JSON.stringify({ dataSourceId: "src-01", clientId: "c9", kind: "CONNECTED", at: "2026-06-30T09:05:00Z" }),
      );
    });
    expect(result.current.rows).toHaveLength(1);
    expect(result.current.rows[0]).toMatchObject({ clientId: "c9", connected: true, disconnectedAt: null });
  });

  it("patches a client to disconnected on DISCONNECTED", () => {
    const { result } = renderHook(() => useLiveClients("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "clients-snapshot",
        JSON.stringify([{ clientId: "c1", connectedAt: "2026-06-30T09:00:00Z", disconnectedAt: null, connected: true }]),
      );
      FakeEventSource.latest().emit(
        "DISCONNECTED",
        JSON.stringify({ dataSourceId: "src-01", clientId: "c1", kind: "DISCONNECTED", at: "2026-06-30T09:10:00Z" }),
      );
    });
    expect(result.current.rows).toHaveLength(1);
    expect(result.current.rows[0]).toMatchObject({
      clientId: "c1",
      connected: false,
      disconnectedAt: "2026-06-30T09:10:00Z",
    });
  });

  it("closes the stream on unmount", () => {
    const { unmount } = renderHook(() => useLiveClients("src-01"));
    act(() => FakeEventSource.latest().emitOpen());
    const es = FakeEventSource.latest();
    unmount();
    expect(es.closed).toBe(true);
  });

  it("does not subscribe when disabled", () => {
    renderHook(() => useLiveClients("src-01", false));
    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
