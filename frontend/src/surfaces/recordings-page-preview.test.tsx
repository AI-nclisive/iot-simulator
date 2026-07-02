/**
 * Tests for recordings page display (UI-052, UI-115)
 *
 * Covers:
 * - Page heading renders correctly
 * - Empty state renders when no recordings are present
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingsPage } from "./recordings-page";

vi.mock("../shell/shell-store", () => ({
  useShellStore: vi.fn(
    (selector: (s: { accessMode: string; sharedRole: string; currentProjectId: string }) => unknown) =>
      selector({ accessMode: "local", sharedRole: "observer", currentProjectId: "" }),
  ),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: vi.fn(
    (selector: (s: {
      artifacts: never[];
      samples: never[];
      isLoading: boolean;
      isSamplesLoading: boolean;
      error: null;
      samplesError: null;
      loadRecordings: () => void;
      loadSamples: () => void;
      deleteSample: () => void;
    }) => unknown) =>
      selector({
        artifacts: [],
        samples: [],
        isLoading: false,
        isSamplesLoading: false,
        error: null,
        samplesError: null,
        loadRecordings: vi.fn(),
        loadSamples: vi.fn(),
        deleteSample: vi.fn(),
      }),
  ),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: vi.fn(
    (selector: (s: { dataSources: never[] }) => unknown) =>
      selector({ dataSources: [] }),
  ),
}));

afterEach(() => {
  cleanup();
});

describe("RecordingsPage — heading", () => {
  it("renders Recordings heading", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { name: /Recordings/i })).toBeTruthy();
  });
});

describe("RecordingsPage — empty state", () => {
  it("renders no results message when recordings list is empty", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(screen.getByText("No results.")).toBeTruthy();
  });
});
