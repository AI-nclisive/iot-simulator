/**
 * Tests for useEditLease (UI-459).
 *
 * Covers:
 * - Starts in "acquiring" state, transitions to "held" when POST returns heldByCurrentUser=true
 * - Transitions to "locked-by-other" + sets lockedByHolder when POST returns heldByCurrentUser=false
 * - Transitions to "error" when POST rejects
 * - Calls DELETE on unmount when lease is held
 * - Does NOT call DELETE on unmount when lease is locked-by-other
 * - Sets up a renewal interval that re-POSTs every 60 s
 * - Skips renewal when state is locked-by-other (no-op)
 * - Does nothing when projectId or objectId are empty
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { apiFetch } from "../api";
import { useEditLease } from "./use-edit-lease";

vi.mock("../api", () => ({
  apiFetch: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(
      public readonly status: number,
      public readonly title: string,
      public readonly detail: string | undefined,
      public readonly type: string | undefined,
    ) {
      super(title);
      this.name = "ApiError";
    }
  },
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;

const HELD_RESPONSE = {
  objectType: "data-sources",
  objectId: "src-1",
  holder: "anna",
  expiresAt: "2026-07-07T12:05:00Z",
  heldByCurrentUser: true,
};

const LOCKED_RESPONSE = {
  objectType: "data-sources",
  objectId: "src-1",
  holder: "bob",
  expiresAt: "2026-07-07T12:05:00Z",
  heldByCurrentUser: false,
};

beforeEach(() => {
  vi.useFakeTimers();
  mockApiFetch.mockClear();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("useEditLease — acquire held", () => {
  it("starts in acquiring state and transitions to held", async () => {
    mockApiFetch.mockResolvedValueOnce(HELD_RESPONSE);

    const { result } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    expect(result.current.leaseState).toBe("acquiring");

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.leaseState).toBe("held");
    expect(result.current.lockedByHolder).toBeNull();
  });

  it("calls POST with the correct URL", async () => {
    mockApiFetch.mockResolvedValueOnce(HELD_RESPONSE);

    renderHook(() => useEditLease("data-sources", "src-1", "proj-1"));

    await act(async () => {
      await Promise.resolve();
    });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/src-1/edit-lease",
      { method: "POST" },
    );
  });
});

describe("useEditLease — locked-by-other", () => {
  it("sets leaseState to locked-by-other and lockedByHolder when heldByCurrentUser is false", async () => {
    mockApiFetch.mockResolvedValueOnce(LOCKED_RESPONSE);

    const { result } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.leaseState).toBe("locked-by-other");
    expect(result.current.lockedByHolder).toBe("bob");
  });
});

describe("useEditLease — error", () => {
  it("sets leaseState to error when POST rejects", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("network error"));

    const { result } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.leaseState).toBe("error");
  });
});

describe("useEditLease — release on unmount", () => {
  it("calls DELETE when unmounting while holding the lease", async () => {
    mockApiFetch
      .mockResolvedValueOnce(HELD_RESPONSE) // POST acquire
      .mockResolvedValueOnce(undefined);    // DELETE release

    const { unmount } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    await act(async () => {
      await Promise.resolve();
    });

    unmount();

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/src-1/edit-lease",
      { method: "DELETE" },
    );
  });

  it("does NOT call DELETE on unmount when locked-by-other", async () => {
    mockApiFetch.mockResolvedValueOnce(LOCKED_RESPONSE);

    const { unmount } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    await act(async () => {
      await Promise.resolve();
    });

    unmount();

    // Only the initial POST should have been called, no DELETE
    const deleteCalls = mockApiFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "DELETE",
    );
    expect(deleteCalls).toHaveLength(0);
  });

  it("does NOT call DELETE on unmount when in error state", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("network error"));

    const { unmount } = renderHook(() =>
      useEditLease("data-sources", "src-1", "proj-1"),
    );

    await act(async () => {
      await Promise.resolve();
    });

    unmount();

    const deleteCalls = mockApiFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "DELETE",
    );
    expect(deleteCalls).toHaveLength(0);
  });
});

describe("useEditLease — renewal interval", () => {
  it("re-POSTs after 60 s when held", async () => {
    mockApiFetch
      .mockResolvedValueOnce(HELD_RESPONSE) // initial acquire
      .mockResolvedValueOnce(HELD_RESPONSE); // renewal

    renderHook(() => useEditLease("data-sources", "src-1", "proj-1"));

    await act(async () => {
      await Promise.resolve();
    });

    // Advance past the renewal interval
    await act(async () => {
      vi.advanceTimersByTime(60_000);
      await Promise.resolve();
    });

    const postCalls = mockApiFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    );
    expect(postCalls).toHaveLength(2);
  });

  it("skips renewal when locked-by-other (stateRef guard)", async () => {
    mockApiFetch.mockResolvedValueOnce(LOCKED_RESPONSE);

    renderHook(() => useEditLease("data-sources", "src-1", "proj-1"));

    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      vi.advanceTimersByTime(60_000);
      await Promise.resolve();
    });

    // Only the initial POST; renewal should not fire
    const postCalls = mockApiFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    );
    expect(postCalls).toHaveLength(1);
  });
});

describe("useEditLease — no-op when ids are empty", () => {
  it("makes no API calls when projectId is empty string", () => {
    renderHook(() => useEditLease("data-sources", "src-1", ""));
    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("makes no API calls when objectId is empty string", () => {
    renderHook(() => useEditLease("data-sources", "", "proj-1"));
    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});

describe("useEditLease — scenario objectType", () => {
  it("uses scenarios in the URL path", async () => {
    mockApiFetch.mockResolvedValueOnce({ ...HELD_RESPONSE, objectType: "scenarios", objectId: "scn-1" });

    renderHook(() => useEditLease("scenarios", "scn-1", "proj-1"));

    await act(async () => {
      await Promise.resolve();
    });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/scenarios/scn-1/edit-lease",
      { method: "POST" },
    );
  });
});
