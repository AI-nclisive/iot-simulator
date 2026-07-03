/**
 * Tests for RecordingDetailPage (UI-115, UI-119, UI-122, UI-123)
 *
 * Covers:
 * - Loading / error / not-found states
 * - Recording metadata renders
 * - Schema tab: loading, error, empty, tree render, folder collapse (UI-123)
 * - Values tab: empty state, table rows, Load more, all-loaded, error states (UI-119)
 * - Values tab: filter panel toggle, search, quality, time range (UI-122)
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
  { nodeId: "n1", parentId: null, path: "/Root", name: "Root", kind: "FOLDER", dataType: null, valueRank: null, access: null, unit: null, description: null },
  { nodeId: "n2", parentId: "n1", path: "/Root/Temp", name: "Temp", kind: "VARIABLE", dataType: "FLOAT64", valueRank: null, access: "READ_ONLY", unit: "°C", description: null },
];

function schemaResponse() {
  return Promise.resolve({ nodes: sampleSchemaNodes });
}
function emptySchemaResponse() {
  return Promise.resolve({ nodes: [] });
}
function valuesResponse(items = sampleValues, nextCursor: string | null = null, total = 2) {
  return Promise.resolve({ items, nextCursor, total });
}

beforeEach(() => {
  // Default: schema returns empty, values returns empty
  mockApiFetch.mockImplementation((url: string) => {
    if ((url as string).endsWith("/schema")) return emptySchemaResponse();
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

// ── Loading / error / not-found ──────────────────────────────────────────────

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
    // Schema tab button exists
    expect(screen.getByRole("button", { name: "Schema" })).toBeTruthy();
  });
});

// ── Schema tab (UI-123) ──────────────────────────────────────────────────────

describe("RecordingDetailPage — schema tab (UI-123)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("shows empty state when schema has no nodes", async () => {
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("No schema captured.")).toBeTruthy());
  });

  it("renders schema nodes from API", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return schemaResponse();
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
        return Promise.reject(new ApiError(500, "Server error", "Internal failure", undefined));
      }
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => expect(screen.getByText("Failed to load schema.")).toBeTruthy());
  });

  it("collapses folder nodes when toggle clicked", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return schemaResponse();
      return Promise.resolve({ items: [], nextCursor: null, total: 0 });
    });
    renderWithId("rec-001");
    await waitFor(() => screen.getByText("Temp"));
    // Click collapse on the folder (▼ button)
    await userEvent.click(screen.getByRole("button", { name: "▼" }));
    expect(screen.queryByText("Temp")).toBeNull();
    // Re-expand
    await userEvent.click(screen.getByRole("button", { name: "▶" }));
    await waitFor(() => expect(screen.getByText("Temp")).toBeTruthy());
  });
});

// ── Values tab empty state ───────────────────────────────────────────────────

describe("RecordingDetailPage — values tab empty state", () => {
  it("shows empty state on Values tab when valueCount is 0", async () => {
    useArtifactsStore.setState({ artifacts: [{ ...baseArtifact, valueCount: 0 }] });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    expect(screen.getByText("No values captured.")).toBeTruthy();
  });
});

// ── Values tab (UI-119) ──────────────────────────────────────────────────────

describe("RecordingDetailPage — values tab (UI-119)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("renders value rows in table after API call", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse();
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("/Root/Temp")).toBeTruthy());
    expect(screen.getByText("/Root/Press")).toBeTruthy();
    expect(screen.getByText("21.5")).toBeTruthy();
  });

  it("shows Load more button when nextCursor is present", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse(sampleValues, "abc123", 3);
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Load more" })).toBeTruthy());
  });

  it("loads second page when Load more is clicked", async () => {
    const page2 = [
      { parameterId: "ns=2;s=Flow", parameterPath: "", timestamp: "2026-01-01T00:00:02Z", value: "5.0", quality: "GOOD" },
    ];
    let callCount = 0;
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      callCount++;
      if (callCount === 1) return valuesResponse(sampleValues, "abc123", 3);
      return valuesResponse(page2, null, 3);
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
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse();
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText(/All.*values loaded/)).toBeTruthy());
  });

  it("shows error panel when initial API call fails (no rows loaded)", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return Promise.reject(new ApiError(500, "Server error", "Internal failure", undefined));
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("Failed to load values.")).toBeTruthy());
  });

  it("keeps rows visible and shows inline error when Load more fails", async () => {
    const { ApiError } = await import("../api");
    let callCount = 0;
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      callCount++;
      if (callCount === 1) return valuesResponse(sampleValues, "abc123", 3);
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

// ── Values tab filters (UI-122) ───────────────────────────────────────────────

describe("RecordingDetailPage — values tab filters (UI-122)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse(sampleValues, null, 2);
    });
  });

  it("shows Filters button on Values tab", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Filters" })).toBeTruthy());
  });

  it("opens filter panel when Filters button clicked", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Filters" }));
    await userEvent.click(screen.getByRole("button", { name: "Filters" }));
    expect(screen.getByPlaceholderText("Filter by path or node ID…")).toBeTruthy();
    expect(screen.getByRole("checkbox", { name: "GOOD" })).toBeTruthy();
    expect(screen.getByRole("checkbox", { name: "BAD" })).toBeTruthy();
  });

  it("shows active filter count badge", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse([], null, 0);
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Filters" }));
    await userEvent.click(screen.getByRole("button", { name: "Filters" }));
    // Uncheck BAD quality
    const badCheckbox = screen.getAllByRole("checkbox").find(
      (cb) => cb.closest("label")?.textContent?.includes("BAD")
    )!;
    await userEvent.click(badCheckbox);
    // Badge with "1" should appear
    await waitFor(() => expect(screen.getByText("1")).toBeTruthy());
  });

  it("shows Clear all button when filters are active", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse([], null, 0);
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Filters" }));
    await userEvent.click(screen.getByRole("button", { name: "Filters" }));
    const badCheckbox = screen.getAllByRole("checkbox").find(
      (cb) => cb.closest("label")?.textContent?.includes("BAD")
    )!;
    await userEvent.click(badCheckbox);
    await waitFor(() => expect(screen.getByRole("button", { name: "Clear all" })).toBeTruthy());
  });

  it("shows total count with 'matching' label when filters active", async () => {
    mockApiFetch.mockImplementation((url: string) => {
      if ((url as string).endsWith("/schema")) return emptySchemaResponse();
      return valuesResponse([], null, 0);
    });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Filters" }));
    await userEvent.click(screen.getByRole("button", { name: "Filters" }));
    const badCheckbox = screen.getAllByRole("checkbox").find(
      (cb) => cb.closest("label")?.textContent?.includes("BAD")
    )!;
    await userEvent.click(badCheckbox);
    await waitFor(() => expect(screen.getByText(/matching/)).toBeTruthy());
  });
});
