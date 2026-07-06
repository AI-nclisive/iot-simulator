/**
 * Tests for recording name display in RecordingsPage (UI-131).
 *
 * Covers:
 * - Displays name when present
 * - Falls back to sourceId when name is absent
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RecordingsPage } from "./recordings-page";

const { mockArtifactsStore } = vi.hoisted(() => ({ mockArtifactsStore: vi.fn() }));

vi.mock("../shell/shell-store", () => ({
  useShellStore: vi.fn(
    (selector: (s: { accessMode: string; sharedRole: string; currentProjectId: string }) => unknown) =>
      selector({ accessMode: "local", sharedRole: "observer", currentProjectId: "" }),
  ),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: mockArtifactsStore,
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: vi.fn(
    (selector: (s: { dataSources: never[] }) => unknown) =>
      selector({ dataSources: [] }),
  ),
}));

function makeArtifactState(overrides: { name?: string; sourceId?: string }) {
  const artifact = {
    id: "rec-01",
    name: overrides.name,
    sourceId: overrides.sourceId ?? "src-99",
    origin: "captured" as const,
    valueCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    createdBy: "alice",
  };
  mockArtifactsStore.mockImplementation(
    (selector: (s: Record<string, unknown>) => unknown) =>
      selector({
        artifacts: [artifact],
        samples: [],
        isLoading: false,
        isSamplesLoading: false,
        error: null,
        samplesError: null,
        loadRecordings: vi.fn(),
        loadSamples: vi.fn(),
        deleteSample: vi.fn(),
      }),
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RecordingsPage — name display (UI-131)", () => {
  beforeEach(() => {
    makeArtifactState({});
  });

  it("shows name when recording has a name", () => {
    makeArtifactState({ name: "My Capture" });
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByText("My Capture")).toBeTruthy();
  });

  it("falls back to sourceId when name is absent", () => {
    makeArtifactState({ sourceId: "src-99" });
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getAllByText("src-99").length).toBeGreaterThanOrEqual(1);
  });

  it("search filters by name: matching query keeps the row visible", async () => {
    makeArtifactState({ name: "Pump A baseline" });
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    const searchInput = screen.getByPlaceholderText(/search/i);
    await userEvent.type(searchInput, "pump");
    await waitFor(() => expect(screen.getByText("Pump A baseline")).toBeTruthy());
  });

  it("search filters by name: non-matching query hides the row", async () => {
    makeArtifactState({ name: "Pump A baseline" });
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    const searchInput = screen.getByPlaceholderText(/search/i);
    await userEvent.type(searchInput, "zzznomatch");
    await waitFor(() => expect(screen.queryByText("Pump A baseline")).toBeNull());
  });
});
