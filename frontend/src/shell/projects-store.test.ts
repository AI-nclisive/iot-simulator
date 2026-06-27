/**
 * Tests for useProjectsStore (UI-013)
 *
 * Covers all four mutations:
 * - renameProject: updates project name
 * - duplicateProject: appends a copy with "(copy)" suffix
 * - archiveProject: moves project to archivedProjects (not permanently deleted)
 * - deleteProject: permanently removes from projects list
 */

import { afterEach, describe, expect, it } from "vitest";
import { useProjectsStore } from "./projects-store";

afterEach(() => {
  useProjectsStore.setState({ projects: [], archivedProjects: [] });
});

function seedProject(id = "p1", name = "Test Project") {
  useProjectsStore.setState({
    projects: [
      { id, name, configuredSources: 2, runningSources: 0, reusableArtifacts: 1, lastActivity: "Yesterday" },
    ],
    archivedProjects: [],
  });
}

describe("renameProject", () => {
  it("updates the project name", () => {
    seedProject();
    useProjectsStore.getState().renameProject("p1", "Renamed Project");
    const project = useProjectsStore.getState().projects.find((p) => p.id === "p1");
    expect(project?.name).toBe("Renamed Project");
  });

  it("does not affect other projects", () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Alpha", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
        { id: "p2", name: "Beta", configuredSources: 0, runningSources: 0, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    useProjectsStore.getState().renameProject("p1", "Alpha Renamed");
    expect(useProjectsStore.getState().projects.find((p) => p.id === "p2")?.name).toBe("Beta");
  });
});

describe("duplicateProject", () => {
  it("adds a copy with '(copy)' suffix", () => {
    seedProject();
    useProjectsStore.getState().duplicateProject("p1");
    const projects = useProjectsStore.getState().projects;
    expect(projects).toHaveLength(2);
    expect(projects[1].name).toBe("Test Project (copy)");
  });

  it("sets runningSources to 0 on the copy", () => {
    useProjectsStore.setState({
      projects: [
        { id: "p1", name: "Running", configuredSources: 3, runningSources: 2, reusableArtifacts: 0, lastActivity: "" },
      ],
      archivedProjects: [],
    });
    useProjectsStore.getState().duplicateProject("p1");
    const copy = useProjectsStore.getState().projects[1];
    expect(copy.runningSources).toBe(0);
  });

  it("does nothing when project id is not found", () => {
    seedProject();
    useProjectsStore.getState().duplicateProject("does-not-exist");
    expect(useProjectsStore.getState().projects).toHaveLength(1);
  });
});

describe("archiveProject", () => {
  it("removes the project from active projects", () => {
    seedProject();
    useProjectsStore.getState().archiveProject("p1");
    expect(useProjectsStore.getState().projects).toHaveLength(0);
  });

  it("moves the project to archivedProjects", () => {
    seedProject("p1", "Archive Me");
    useProjectsStore.getState().archiveProject("p1");
    expect(useProjectsStore.getState().archivedProjects).toHaveLength(1);
    expect(useProjectsStore.getState().archivedProjects[0].name).toBe("Archive Me");
  });
});

describe("deleteProject", () => {
  it("permanently removes the project from projects", () => {
    seedProject();
    useProjectsStore.getState().deleteProject("p1");
    expect(useProjectsStore.getState().projects).toHaveLength(0);
  });

  it("does not add the project to archivedProjects", () => {
    seedProject();
    useProjectsStore.getState().deleteProject("p1");
    expect(useProjectsStore.getState().archivedProjects).toHaveLength(0);
  });
});
