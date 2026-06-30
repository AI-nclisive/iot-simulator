/**
 * Tests for useLiveRuntime (UI-098) — SSE runtime stream → per-source state + events.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useLiveRuntime } from "./use-live-runtime";

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

describe("useLiveRuntime", () => {
  it("maps a runtime-state snapshot to UI status/health", () => {
    const { result } = renderHook(() => useLiveRuntime("p1"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "runtime-state",
        JSON.stringify([
          { dataSourceId: "src-01", state: "RUNNING", lastError: null },
          { dataSourceId: "src-02", state: "ERROR", lastError: { type: "io", detail: "boom", at: "t" } },
          { dataSourceId: "src-03", state: "STALE", lastError: null },
        ]),
      );
    });
    const s = result.current.sources;
    expect(s).toHaveLength(3);
    expect(s[0]).toMatchObject({ dataSourceId: "src-01", status: "Active", health: "Healthy" });
    expect(s[1]).toMatchObject({ dataSourceId: "src-02", status: "Stopped", health: "Error" });
    expect(s[2]).toMatchObject({ dataSourceId: "src-03", health: "Warning" });
  });

  it("records runtime deltas as events, most recent first", () => {
    const { result } = renderHook(() => useLiveRuntime("p1"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "STARTED",
        JSON.stringify({ dataSourceId: "src-01", type: "STARTED", at: "2026-06-30T09:00:00Z", detail: "" }),
      );
      FakeEventSource.latest().emit(
        "ERROR",
        JSON.stringify({ dataSourceId: "src-01", type: "ERROR", at: "2026-06-30T09:01:00Z", detail: "fault" }),
      );
    });
    expect(result.current.events).toHaveLength(2);
    expect(result.current.events[0]).toMatchObject({ type: "ERROR", detail: "fault" });
  });

  it("updates source state when a delta type is a known runtime state", () => {
    const { result } = renderHook(() => useLiveRuntime("p1"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "runtime-state",
        JSON.stringify([{ dataSourceId: "src-01", state: "RUNNING", lastError: null }]),
      );
      FakeEventSource.latest().emit(
        "ERROR",
        JSON.stringify({ dataSourceId: "src-01", type: "ERROR", at: "t", detail: "fault" }),
      );
    });
    const src = result.current.sources.find((s) => s.dataSourceId === "src-01");
    expect(src).toMatchObject({ rawState: "ERROR", health: "Error" });
    expect(src?.lastError?.detail).toBe("fault");
  });

  it("closes the stream on unmount", () => {
    const { unmount } = renderHook(() => useLiveRuntime("p1"));
    act(() => FakeEventSource.latest().emitOpen());
    const es = FakeEventSource.latest();
    unmount();
    expect(es.closed).toBe(true);
  });

  it("does not subscribe when disabled", () => {
    renderHook(() => useLiveRuntime("p1", false));
    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
