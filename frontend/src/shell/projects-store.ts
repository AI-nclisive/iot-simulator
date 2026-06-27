import { create } from "zustand";
import { projects as initialProjects, type ProjectSummary } from "./mock-workspace";

type ProjectsState = {
  projects: ProjectSummary[];
  archivedProjects: ProjectSummary[];
  renameProject: (id: string, name: string) => void;
  duplicateProject: (id: string) => void;
  archiveProject: (id: string) => void;
  deleteProject: (id: string) => void;
};

export const useProjectsStore = create<ProjectsState>((set) => ({
  projects: initialProjects,
  archivedProjects: [],
  renameProject: (id, name) =>
    set((state) => ({
      projects: state.projects.map((p) => (p.id === id ? { ...p, name } : p)),
    })),
  duplicateProject: (id) =>
    set((state) => {
      const source = state.projects.find((p) => p.id === id);
      if (!source) return state;
      const copy: ProjectSummary = {
        ...source,
        id: `${source.id}-copy-${Date.now()}`,
        name: `${source.name} (copy)`,
        runningSources: 0,
        lastActivity: "Just created",
      };
      return { projects: [...state.projects, copy] };
    }),
  archiveProject: (id) =>
    set((state) => {
      const target = state.projects.find((p) => p.id === id);
      if (!target) return state;
      return {
        projects: state.projects.filter((p) => p.id !== id),
        archivedProjects: [...state.archivedProjects, target],
      };
    }),
  deleteProject: (id) =>
    set((state) => ({
      projects: state.projects.filter((p) => p.id !== id),
    })),
}));
