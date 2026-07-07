/**
 * Tests for RecordingDetailPage (UI-115, UI-119, UI-122, UI-123)
 *
 * Covers:
 * - Loading state while isLoading is true
 * - Error state when store has error
 * - Not-found state when recording is absent
 * - Recording metadata renders when recording is found
 * - Schema tab is active by default
 * - Schema tab: empty state when API returns no nodes
 * - Schema tab: renders folder and variable nodes from API
 * - Schema tab: shows error panel when API fails
 * - Schema tab: collapses folder nodes when toggle clicked
 * - Values tab: empty state when valueCount = 0
 * - Values tab: renders table rows from API (UI-119)
 * - Values tab: shows "Load more" when nextCursor present
 * - Values tab: loads second page on "Load more" click
 * - Values tab: shows all-loaded message when no nextCursor
 * - Values tab filter panel (UI-122): quality checkboxes default all checked
 * - Values tab filter panel (UI-122): unchecking all quality boxes shows empty state, no API call
 * - Values tab filter panel (UI-122): unchecking one quality sends qualities param to API
 * - Values tab filter panel (UI-122): search input is debounced (~300 ms)
 * - Values tab filter panel (UI-122): from/to datetime inputs pass params to API
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useArtifactsStore } from "../shell/artifacts-store";
import { RecordingDetailPage } from "./recording-detail-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));
const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return { ...actual, apiFetch: mockApiFetch };
});

vi.mock("../shell/artifacts-store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../shell/artifacts-store")>();
  return { ...actual, useArtifactsStore: actual.useArtifactsStore };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderWithId(recordingId: string) {
  render(
    <MemoryRouter initialEntries={[`/recordings/${recordingId}`]}>
      <Routes>
        <Route path="/recordings/:recordingId" element={<RecordingDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const baseArtifact = {
  id: "rec-001",
  sourceId: "src-abc",
  origin: "captured" as const,
  valueCount: 1500,
  createdAt: "2026-07-01T10:00:00Z",
  createdBy: "test-user",
};

const sampleValues = [
  { parameterId: "ns=2;s=Temp", parameterPath: "/Root/Temp", timestamp: "2026-01-01T00:00:00Z", value: "21.5", quality: "GOOD" },
  { parameterId: "ns=2;s=Press", parameterPath: "/Root/Press", timestamp: "2026-01-01T00:00:01Z", value: "1013", quality: "GOOD" },
];

const sampleSchemaNodes = [
  {
    nodeId: "node-root",
    parentId: null,
    path: "/Root",
    name: "Root",
    kind: "FOLDER" as const,
    dataType: null,
  },
  {
    nodeId: "node-temp",
    parentId: "node-root",
    path: "/Root/Temp",
    name: "Temp",
    kind: "VARIABLE" as const,
    dataType: "FLOAT64",
  },
];

beforeEach(() => {
  mockApiFetch.mockImplementation((url: string) => {
    if ((url as string).endsWith("/schema")) {
      return Promise.resolve({ nodes: [] });
    }
    return Promise.resolve({ items: [], nextCursor: null, total: 0 });
  });
  useArtifactsStore.setState({
    artifacts: [],
    samples: [],
    isLoading: false,
    isSamplesLoading: false,
    error: null,
    samplesError: null,
    loadRecordings: vi.fn().mockResolvedValue(undefined),
    loadRecordingById: vi.fn().mockResolvedValue(undefined),
    loadSamples: vi.fn().mockResolvedValue(undefined),
  });
});

describe("RecordingDetailPage — loading state", () => {
  it("shows loading panel while isLoading is true", () => {
    useArtifactsStore.setState({ isLoading: true });
    renderWithId("rec-001");
    expect(screen.getByText("Loading…")).toBeTruthy();
  });
});

describe("RecordingDetailPage — error state", () => {
  it("shows error panel when store has error", () => {
    useArtifactsStore.setState({ error: "Network failure" });
    renderWithId("rec-001");
    expect(screen.getByText("Could not load recording.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — not found state", () => {
  it("shows not-found panel when recording is absent", () => {
    renderWithId("rec-unknown");
    expect(screen.getByText("Recording not found.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — recording found", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("renders source ID in metadata", () => {
    renderWithId("rec-001");
    expect(screen.getByText("src-abc")).toBeTruthy();
  });

  it("renders captured-by in metadata", () => {
    renderWithId("rec-001");
    expect(screen.getByText("test-user")).toBeTruthy();
  });

  it("renders Recorded badge for captured origin", () => {
    renderWithId("rec-001");
    expect(screen.getByText("Recorded")).toBeTruthy();
  });

  it("Schema tab is active by default", () => {
    renderWithId("rec-001");
    expect(screen.getByRole("button", { name: "Schema" })).toBeTruthy();
  });
});

describe("RecordingDetailPage — header name field (UI-131)", () => {
  it("shows recording name as header when name is present", () => {
    useArtifactsStore.setState({ artifacts: [{ ...baseArtifact, name: "My Pump Scan" }] });
    renderWithId("rec-001");
    expect(screen.getByText("My Pump Scan")).toBeTruthy();
    // id prefix also visible alongside the name
    expect(screen.getByText("rec-001".slice(0, 8))).toBeTruthy();
  });

  it("shows 'Recording <id>' fallback when name is absent", () => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
    renderWithId("rec-001");
    expect(screen.getByText("Recording")).toBeTruthy();
    expect(screen.getByText("rec-001".slice(0, 8))).toBeTruthy();
  });
});

describe("RecordingDetailPage — schema tab (UI-123)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("shows empty state when schema has no nodes", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) {
        return Promise.resolve({ nodes: [] });
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("No schema captured.")).toBeTruthy());
  });

  it("renders schema nodes from API", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) {
        return Promise.resolve({ nodes: sampleSchemaNodes });
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("Root")).toBeTruthy());
    expect(screen.getByText("Temp")).toBeTruthy();
    expect(screen.getByText("FLOAT64")).toBeTruthy();
  });

  it("shows error panel when schema API fails", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) {
        return Promise.reject(new ApiError(500, "Schema load failed", "Internal failure", undefined));
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("Failed to load schema.")).toBeTruthy());
  });

  it("shows empty state (not error) when schema API returns 404 — new shell recording (UI-455)", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) {
        return Promise.reject(new ApiError(404, "Not Found", undefined, undefined));
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("No schema captured.")).toBeTruthy());
    expect(screen.queryByText("Failed to load schema.")).toBeNull();
  });

  it("collapses folder nodes when toggle clicked", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) {
        return Promise.resolve({ nodes: sampleSchemaNodes });
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    // Wait for tree to render
    await waitFor(() => expect(screen.getByText("Root")).toBeTruthy());
    // Initially expanded — Temp is visible
    expect(screen.getByText("Temp")).toBeTruthy();
    // Click the collapse toggle (▼)
    await userEvent.click(screen.getByRole("button", { name: "▼" }));
    // After collapse — Temp should not be visible
    expect(screen.queryByText("Temp")).toBeNull();
    // Click again to expand
    await userEvent.click(screen.getByRole("button", { name: "▶" }));
    await waitFor(() => expect(screen.getByText("Temp")).toBeTruthy());
  });
});

describe("RecordingDetailPage — values tab empty state", () => {
  it("shows empty state on Values tab when valueCount is 0", async () => {
    useArtifactsStore.setState({ artifacts: [{ ...baseArtifact, valueCount: 0 }] });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    expect(screen.getByText("No values captured.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — values tab (UI-119)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
    // Schema call happens on mount; use mockImplementation to route by URL.
  });

  it("renders value rows in table after API call", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      return Promise.resolve({ items: sampleValues, nextCursor: null, total: 2 });
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("/Root/Temp")).toBeTruthy());
    expect(screen.getByText("/Root/Press")).toBeTruthy();
    expect(screen.getByText("21.5")).toBeTruthy();
  });

  it("shows Load more button when nextCursor is present", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      return Promise.resolve({ items: sampleValues, nextCursor: "abc123", total: 3 });
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Load more" })).toBeTruthy());
  });

  it("loads second page when Load more is clicked", async () => {
    const page2 = [
      { parameterId: "ns=2;s=Flow", timestamp: "2026-01-01T00:00:02Z", value: "5.0", quality: "GOOD" },
    ];
    let valuesCallCount = 0;
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      valuesCallCount++;
      if (valuesCallCount === 1) return Promise.resolve({ items: sampleValues, nextCursor: "abc123", total: 3 });
      return Promise.resolve({ items: page2, nextCursor: null, total: 3 });
    });

    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Load more" }));
    await userEvent.click(screen.getByRole("button", { name: "Load more" }));
    await waitFor(() => expect(screen.getByText("ns=2;s=Flow")).toBeTruthy());
    expect(screen.queryByRole("button", { name: "Load more" })).toBeNull();
  });

  it("shows all-loaded message when all values fetched", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      return Promise.resolve({ items: sampleValues, nextCursor: null, total: 2 });
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() =>
      expect(screen.getByText(/All.*values loaded/)).toBeTruthy(),
    );
  });

  it("shows error panel when initial API call fails (no rows loaded)", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      return Promise.reject(new ApiError(500, "Server error", "Internal failure", undefined));
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("Failed to load values.")).toBeTruthy());
  });

  it("keeps rows visible and shows inline error when Load more fails", async () => {
    const { ApiError } = await import("../api");
    let valuesCallCount = 0;
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      valuesCallCount++;
      if (valuesCallCount === 1) return Promise.resolve({ items: sampleValues, nextCursor: "abc123", total: 3 });
      return Promise.reject(new ApiError(500, "Server error", "Internal failure", undefined));
    });

    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Load more" }));
    await userEvent.click(screen.getByRole("button", { name: "Load more" }));

    await waitFor(() => expect(screen.getByText("Server error")).toBeTruthy());
    expect(screen.getByText("/Root/Temp")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Load more" })).toBeTruthy();
    expect(screen.queryByText("Failed to load values.")).toBeNull();
  });
});

describe("RecordingDetailPage — filter panel (UI-122)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return Promise.resolve({ nodes: [] });
      return Promise.resolve({ items: sampleValues, nextCursor: null, total: 2 });
    });
  });

  it("shows quality checkboxes for GOOD, UNCERTAIN, BAD — all checked by default", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("GOOD")).toBeTruthy());
    expect((screen.getByLabelText("GOOD") as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText("UNCERTAIN") as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText("BAD") as HTMLInputElement).checked).toBe(true);
  });

  it("shows search input in filter panel", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("Search")).toBeTruthy());
  });

  it("shows From and To datetime inputs in filter panel", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("From")).toBeTruthy());
    expect(screen.getByLabelText("To")).toBeTruthy();
  });

  it("shows empty state when all quality checkboxes are unchecked — last uncheck makes no API call", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("GOOD")).toBeTruthy());

    // Uncheck first two (these may each trigger a filtered reload)
    await userEvent.click(screen.getByLabelText("GOOD"));
    await userEvent.click(screen.getByLabelText("UNCERTAIN"));

    // Record call count right before unchecking the last box
    const callsBeforeLast = mockApiFetch.mock.calls.length;

    // Uncheck the final checkbox
    await userEvent.click(screen.getByLabelText("BAD"));

    await waitFor(() => expect(screen.getByText("No quality selected.")).toBeTruthy());
    // The final uncheck (0 qualities) must NOT trigger an API call
    expect(mockApiFetch.mock.calls.length).toBe(callsBeforeLast);
  });

  it("passes quality param to API when a quality is unchecked", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("BAD")).toBeTruthy());

    // Uncheck BAD
    await userEvent.click(screen.getByLabelText("BAD"));

    await waitFor(() => {
      const urls = mockApiFetch.mock.calls.map((c: unknown[]) => c[0] as string);
      const filtered = urls.filter((u) => u.includes("quality="));
      expect(filtered.length).toBeGreaterThan(0);
      const lastFiltered = filtered[filtered.length - 1];
      expect(lastFiltered).toContain("quality=");
      expect(lastFiltered).not.toContain("BAD");
    });
  });

  it("passes search param to API after debounce (300 ms)", async () => {
    // Use fake timers to control debounce timing precisely
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });

    renderWithId("rec-001");
    await user.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("Search")).toBeTruthy());

    const searchInput = screen.getByLabelText("Search");

    // Type without advancing timers — debounce not yet fired
    await user.type(searchInput, "Temp");

    // Immediately after typing: no search= in URLs yet
    const urlsBefore = mockApiFetch.mock.calls.map((c: unknown[]) => c[0] as string);
    expect(urlsBefore.some((u) => u.includes("search="))).toBe(false);

    // Advance past debounce threshold
    vi.advanceTimersByTime(350);

    await waitFor(() => {
      const urls = mockApiFetch.mock.calls.map((c: unknown[]) => c[0] as string);
      expect(urls.some((u) => u.includes("search=Temp"))).toBe(true);
    });

    vi.useRealTimers();
  });

  it("passes from and to params to API when time range is set", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByLabelText("From")).toBeTruthy());

    const fromInput = screen.getByLabelText("From") as HTMLInputElement;
    await userEvent.type(fromInput, "2026-01-01T00:00");

    await waitFor(() => {
      const urls = mockApiFetch.mock.calls.map((c: unknown[]) => c[0] as string);
      expect(urls.some((u) => u.includes("from="))).toBe(true);
    });
  });
});
