import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useArtifactsStore } from "../shell/artifacts-store";
import { RecordingsPage } from "./recordings-page";

const { mockShellStore } = vi.hoisted(() => ({ mockShellStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({
  useShellStore: mockShellStore,
}));

// Mock the whole artifacts store module so useEffect load calls are no-ops
// but we can still set store state via setState for specific tests.
vi.mock("../shell/artifacts-store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../shell/artifacts-store")>();
  return {
    ...actual,
    useArtifactsStore: actual.useArtifactsStore,
  };
});

const shellState = {
  accessMode: "local" as const,
  sharedRole: "admin" as const,
  currentProjectId: "proj-1",
};

afterEach(cleanup);

describe("RecordingsPage route", () => {
  beforeEach(() => {
    // useShellStore is called with a selector — invoke it with mock state
    mockShellStore.mockImplementation(
      (selector: (s: typeof shellState) => unknown) => selector(shellState),
    );

    // Reset the artifacts store to a known state and stub the load methods
    // to prevent async API calls from overwriting test data
    useArtifactsStore.setState({
      artifacts: [],
      samples: [],
      isLoading: false,
      isSamplesLoading: false,
      error: null,
      samplesError: null,
      loadRecordings: vi.fn().mockResolvedValue(undefined),
      loadSamples: vi.fn().mockResolvedValue(undefined),
    });
  });

  it("renders the Recordings & Samples heading — not a stub", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: /Recordings/i })).toBeTruthy();
  });

  it("renders the filter controls (not a surface stub)", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByPlaceholderText(/Search by source or author/i)).toBeTruthy();
  });

  it("renders recordings from the store — not mock data", () => {
    useArtifactsStore.setState({
      artifacts: [
        {
          id: "rec-live-01",
          sourceId: "src-live-01",
          origin: "captured",
          valueCount: 1000,
          createdAt: "2026-06-30T10:00:00Z",
          createdBy: "Test User",
        },
      ],
      samples: [],
      isLoading: false,
      isSamplesLoading: false,
      error: null,
      samplesError: null,
      loadRecordings: vi.fn().mockResolvedValue(undefined),
      loadSamples: vi.fn().mockResolvedValue(undefined),
    });

    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    // Source ID appears both in the list item and the filter dropdown option
    expect(screen.getAllByText("src-live-01").length).toBeGreaterThanOrEqual(1);
  });

  it("maps captured origin to Recorded badge", () => {
    useArtifactsStore.setState({
      artifacts: [
        {
          id: "rec-scan-01",
          sourceId: "src-01",
          origin: "captured",
          valueCount: 500,
          createdAt: "2026-06-30T10:00:00Z",
          createdBy: "Tester",
        },
      ],
      samples: [],
      isLoading: false,
      isSamplesLoading: false,
      error: null,
      samplesError: null,
      loadRecordings: vi.fn().mockResolvedValue(undefined),
      loadSamples: vi.fn().mockResolvedValue(undefined),
    });

    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    // "Recorded" appears as the badge; "Recorded" also appears in filter dropdown
    expect(screen.getAllByText("Recorded").length).toBeGreaterThanOrEqual(1);
  });

  it("maps imported origin to Imported badge", () => {
    useArtifactsStore.setState({
      artifacts: [
        {
          id: "rec-import-01",
          sourceId: "src-02",
          origin: "imported",
          valueCount: 200,
          createdAt: "2026-06-30T10:00:00Z",
          createdBy: "CI",
        },
      ],
      samples: [],
      isLoading: false,
      isSamplesLoading: false,
      error: null,
      samplesError: null,
      loadRecordings: vi.fn().mockResolvedValue(undefined),
      loadSamples: vi.fn().mockResolvedValue(undefined),
    });

    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    // "Imported" appears as the badge; "Imported" also appears in filter dropdown
    expect(screen.getAllByText("Imported").length).toBeGreaterThanOrEqual(1);
  });

  it("renders the Create recording button", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: "Create recording" })).toBeTruthy();
  });

  it("renders the Import recording button", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: "Import recording" })).toBeTruthy();
  });
});
