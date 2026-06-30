/**
 * Tests for useProjectsStore (UI-097 live-API rewrite)
 *
 * All apiFetch calls are mocked via vi.mock('../api').
 * Covers:
 * - loadProjects: sets projects from API, handles error
 * - renameProject: updates project name via PUT
 * - duplicateProject: adds a copy via POST /projects/{id}/duplicate
 * - archiveProject: moves project to archivedProjects
 * - deleteProject: permanently removes from projects list
 */

import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { useProjectsStore } from "./projects-store";
import { apiFetch } from "../api";

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
  mapProtocol: vi.fn(),
  mapRuntimeStateToStatus: vi.fn(),
  mapRuntimeStateToHealth: vi.fn(),
  mapDataType: vi.fn(),
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;

function makeProjectResponse(overrides: Partial<{
  id: string;
  name: string;
  status: "ACTIVE" | "ARCHIVED";
}> = {}) {
  return {
    id: overrides.id ?? "p1",
    name: overrides.name ?? "Test Project",
    description: null,
    status: overrides.status ?? "ACTIVE",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    createdBy: "testuser",
    version: 1,
  };
}

beforeEach(() => {
  useProjectsStore.setState({ projects: [], archivedProjects: [], isLoading: false, error: null });
  mockApiFetch.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("loadProjects", () => {
  it("sets active projects from API response", async () => {
    mockApiFetch.mockResolvedValueOnce([
      makeProjectResponse({ id: "p1", name: "Alpha" }),
      makeProjectResponse({ id: "p2", name: "Beta" }),
    ]);
    await useProjectsStore.getState().loadProjects();
    const { projects, archivedProjects, isLoading, error } = useProjectsStore.getState();
    expect(projects).toHaveLength(2);
    expect(projects[0].id).toBe("p1");
    expect(projects[0].name).toBe("Alpha");
    expect(archivedProjects).toHaveLength(0);
    expect(isLoading).toBe(false);
    expect(error).toBeNull();
  });

  it("separates archived projects", async () => {
    mockApiFetch.mockResolvedValueOnce([
      makeProjectResponse({ id: "p1", status: "ACTIVE" }),
      makeProjectResponse({ id: "p2", status: "ARCHIVED" }),
    ]);
    await useProjectsStore.getState().loadProjects();
    expect(useProjectsStore.getState().projects).toHaveLength(1);
    expect(useProjectsStore.getState().archivedProjects).toHaveLength(1);
  });

  it("sets error on API failure", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("Network error"));
    await useProjectsStore.getState().loadProjects();
    const { error, isLoading, projects } = useProjectsStore.getState();
    expect(error).toBeTruthy();
    expect(isLoading).toBe(false);
    expect(projects).toHaveLength(0);
  });

  it("sets isLoading true during fetch and false after", async () => {
    let resolvePromise!: (v: unknown) => void;
    mockApiFetch.mockReturnValueOnce(
      new Promise((res) => {
        resolvePromise = res;
      }),
    );
    const loadPromise = useProjectsStore.getState().loadProjects();
    expect(useProjectsStore.getState().isLoading).toBe(true);
    resolvePromise([]);
    await loadPromise;
    expect(useProjectsStore.getState().isLoading).toBe(false);
  });
});

describe("renameProject", () => {
  it("updates the project name via PUT", async () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Alpha", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    mockApiFetch.mockResolvedValueOnce(makeProjectResponse({ id: "p1", name: "Alpha Renamed" }));
    await useProjectsStore.getState().renameProject("p1", "Alpha Renamed");
    expect(useProjectsStore.getState().projects[0].name).toBe("Alpha Renamed");
  });
});

describe("duplicateProject", () => {
  it("calls POST /projects/{id}/duplicate and adds the copy to the list", async () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Alpha", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    mockApiFetch.mockResolvedValueOnce(makeProjectResponse({ id: "p1-copy", name: "Alpha (copy)" }));
    await useProjectsStore.getState().duplicateProject("p1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/p1/duplicate",
      expect.objectContaining({ method: "POST" }),
    );
    expect(useProjectsStore.getState().projects).toHaveLength(2);
    expect(useProjectsStore.getState().projects[1].name).toBe("Alpha (copy)");
  });

  it("propagates the error when the API call fails", async () => {
    useProjectsStore.setState({ projects: [], archivedProjects: [] });
    mockApiFetch.mockRejectedValueOnce(new Error("Not found"));
    await expect(useProjectsStore.getState().duplicateProject("no-such-id")).rejects.toThrow("Not found");
  });
});

describe("archiveProject", () => {
  it("removes the project from active projects and moves to archived", async () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Archive Me", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    mockApiFetch.mockResolvedValueOnce(makeProjectResponse({ id: "p1", status: "ARCHIVED" }));
    await useProjectsStore.getState().archiveProject("p1");
    expect(useProjectsStore.getState().projects).toHaveLength(0);
    expect(useProjectsStore.getState().archivedProjects).toHaveLength(1);
    expect(useProjectsStore.getState().archivedProjects[0].name).toBe("Archive Me");
  });
});

describe("deleteProject", () => {
  it("permanently removes the project from projects", async () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Delete Me", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    mockApiFetch.mockResolvedValueOnce(undefined);
    await useProjectsStore.getState().deleteProject("p1");
    expect(useProjectsStore.getState().projects).toHaveLength(0);
    expect(useProjectsStore.getState().archivedProjects).toHaveLength(0);
  });
});
