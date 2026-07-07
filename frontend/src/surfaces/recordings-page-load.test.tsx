/**
 * Tests for RecordingsPage data-loading on mount (UI-139)
 *
 * Covers:
 * - loadDataSources is called with currentProjectId on mount
 * - loadRecordings is called with currentProjectId on mount
 */

import { cleanup, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingsPage } from "./recordings-page";

const { mockLoadDataSources, mockLoadRecordings } = vi.hoisted(() => ({
  mockLoadDataSources: vi.fn().mockResolvedValue(undefined),
  mockLoadRecordings: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      artifacts: [],
      isLoading: false,
      error: null,
      loadRecordings: mockLoadRecordings,
    }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ dataSources: [], loadDataSources: mockLoadDataSources }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RecordingsPage — loadDataSources on mount (UI-139)", () => {
  it("calls loadDataSources with currentProjectId on mount", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(mockLoadDataSources).toHaveBeenCalledWith("proj-1");
  });

  it("calls loadRecordings with currentProjectId on mount", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(mockLoadRecordings).toHaveBeenCalledWith("proj-1");
  });

  it("calls both loaders exactly once on mount", () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );
    expect(mockLoadDataSources).toHaveBeenCalledTimes(1);
    expect(mockLoadRecordings).toHaveBeenCalledTimes(1);
  });
});
