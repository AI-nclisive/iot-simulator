/**
 * Tests for useActiveRuns (UI-111) — poll /active-runs → run list.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useActiveRuns } from "./use-active-runs";

const RUNS = [
  {
    id: "run-1",
    label: "Batch replay",
    processType: "Replay",
    runState: "running",
    startedAt: "2026-07-01T14:31:00Z",
    initiator: "Alex M.",
    relatedSourceId: "src-01",
    relatedLabel: "Line A telemetry",
  },
];

// Flush all pending microtasks (resolved promises) without advancing timers.
async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "Content-Type": "application/json" }),
      json: async () => ({ items: RUNS }),
    } as Response),
  );
  // apiFetch reads sessionStorage for JWT
  vi.stubGlobal("sessionStorage", { getItem: () => null });
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.resetAllMocks();
});

describe("useActiveRuns", () => {
  it("skips fetch when projectId is null", async () => {
    const { result } = renderHook(() => useActiveRuns(null));
    await flushPromises();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.runs).toHaveLength(0);
    expect(vi.mocked(fetch)).not.toHaveBeenCalled();
  });

  it("fetches runs on mount and returns them", async () => {
    const { result } = renderHook(() => useActiveRuns("proj-1"));
    // isLoading starts true
    expect(result.current.isLoading).toBe(true);
    // flush fetch promise resolution
    await flushPromises();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.runs).toHaveLength(1);
    expect(result.current.runs[0].label).toBe("Batch replay");
    expect(result.current.error).toBeNull();
  });

  it("sets error when fetch fails", async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      headers: new Headers({ "Content-Type": "application/json" }),
    } as Response);

    const { result } = renderHook(() => useActiveRuns("proj-1"));
    await flushPromises();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).not.toBeNull();
    expect(result.current.runs).toHaveLength(0);
  });

  it("polls again after 5 seconds", async () => {
    const { result } = renderHook(() => useActiveRuns("proj-1"));
    await flushPromises();
    const callCount = vi.mocked(fetch).mock.calls.length;
    expect(callCount).toBeGreaterThan(0);

    // Advance timers to trigger the next poll
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    await flushPromises();

    expect(vi.mocked(fetch).mock.calls.length).toBeGreaterThan(callCount);
    expect(result.current.runs).toHaveLength(1);
  });

  it("stops polling on unmount", async () => {
    const { unmount } = renderHook(() => useActiveRuns("proj-1"));
    await flushPromises();
    const callCount = vi.mocked(fetch).mock.calls.length;

    unmount();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    await flushPromises();

    // No additional calls after unmount
    expect(vi.mocked(fetch).mock.calls.length).toBe(callCount);
  });
});
