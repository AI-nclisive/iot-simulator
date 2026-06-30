/**
 * Tests for useLiveValues (UI-098) — verifies SSE events map to store rows.
 * Reuses a fake EventSource (jsdom has none) and React Testing Library's
 * renderHook to drive the hook.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useLiveValues } from "./use-live-values";

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
  addEventListener(type: string, fn: (ev: MessageEvent) => void) {
    const a = this.listeners.get(type) ?? [];
    a.push(fn);
    this.listeners.set(type, a);
  }
  close() {
    this.closed = true;
  }
  emitOpen() {
    this.onopen?.();
  }
  emit(type: string, data: string) {
    for (const fn of this.listeners.get(type) ?? []) fn({ data } as MessageEvent);
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

function snapshotPayload() {
  return JSON.stringify([
    { nodeId: "oven.temp", value: 182.4, quality: "GOOD", qualityReason: null, sourceTime: "2026-06-30T09:41:12Z" },
    { nodeId: "line.speed", value: 1.82, quality: "GOOD", qualityReason: null, sourceTime: "2026-06-30T09:41:11Z" },
  ]);
}

describe("useLiveValues", () => {
  it("populates rows from a values-snapshot", () => {
    const { result } = renderHook(() => useLiveValues("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit("values-snapshot", snapshotPayload());
    });
    expect(result.current.rows).toHaveLength(2);
    expect(result.current.rows[0]).toMatchObject({
      sourceId: "src-01",
      path: "oven.temp",
      currentValue: "182.4",
      freshness: "Live",
    });
  });

  it("updates an existing row on a values delta", () => {
    const { result } = renderHook(() => useLiveValues("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit("values-snapshot", snapshotPayload());
    });
    act(() => {
      FakeEventSource.latest().emit(
        "values",
        JSON.stringify({ nodeId: "oven.temp", value: 190.0, quality: "GOOD", qualityReason: null, sourceTime: "2026-06-30T09:41:20Z" }),
      );
    });
    const row = result.current.rows.find((r) => r.path === "oven.temp");
    expect(row?.currentValue).toBe("190");
    expect(result.current.rows).toHaveLength(2); // updated, not appended
  });

  it("appends a new node seen only in a delta", () => {
    const { result } = renderHook(() => useLiveValues("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit("values-snapshot", snapshotPayload());
      FakeEventSource.latest().emit(
        "values",
        JSON.stringify({ nodeId: "new.node", value: 1, quality: "GOOD", qualityReason: null, sourceTime: "2026-06-30T09:41:25Z" }),
      );
    });
    expect(result.current.rows).toHaveLength(3);
    expect(result.current.rows.some((r) => r.path === "new.node")).toBe(true);
  });

  it("marks rows No updates when the stream goes stale", () => {
    const { result } = renderHook(() => useLiveValues("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit("values-snapshot", snapshotPayload());
    });
    act(() => {
      vi.advanceTimersByTime(15_000); // default stale window
    });
    expect(result.current.status).toBe("stale");
    expect(result.current.rows.every((r) => r.freshness === "No updates")).toBe(true);
  });

  it("maps non-GOOD quality to No updates freshness", () => {
    const { result } = renderHook(() => useLiveValues("src-01"));
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emit(
        "values-snapshot",
        JSON.stringify([{ nodeId: "n", value: null, quality: "BAD", qualityReason: "stale", sourceTime: "2026-06-30T09:00:00Z" }]),
      );
    });
    expect(result.current.rows[0].freshness).toBe("No updates");
    expect(result.current.rows[0].currentValue).toBe("—");
  });

  it("closes the stream on unmount", () => {
    const { unmount } = renderHook(() => useLiveValues("src-01"));
    act(() => FakeEventSource.latest().emitOpen());
    const es = FakeEventSource.latest();
    unmount();
    expect(es.closed).toBe(true);
  });

  it("does not subscribe when disabled", () => {
    renderHook(() => useLiveValues("src-01", false));
    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
