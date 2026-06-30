import { create } from "zustand";
import { apiFetch, ApiError } from "../api";
import type { ProjectSummary } from "./mock-workspace";

// Backend response shape from GET/POST/PUT /api/v1/projects
type ProjectResponse = {
  id: string;
  name: string;
  description: string | null;
  status: "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  version: number;
};

function mapProject(p: ProjectResponse): ProjectSummary {
  return {
    id: p.id,
    name: p.name,
    configuredSources: 0, // TODO(UI-097): no backend field — derive from sources count later
    runningSources: 0, // TODO(UI-097): no backend field
    reusableArtifacts: 0, // TODO(UI-097): no backend field
    lastActivity: p.updatedAt, // TODO(UI-097): no dedicated field — using updatedAt
  };
}

type ProjectsState = {
  projects: ProjectSummary[];
  archivedProjects: ProjectSummary[];
  isLoading: boolean;
  error: string | null;
  loadProjects: () => Promise<void>;
  createProject: (name: string, description?: string) => Promise<ProjectSummary>;
  renameProject: (id: string, name: string) => Promise<void>;
  duplicateProject: (id: string) => Promise<void>;
  archiveProject: (id: string) => Promise<void>;
  deleteProject: (id: string) => Promise<void>;
};

export const useProjectsStore = create<ProjectsState>((set, get) => ({
  projects: [],
  archivedProjects: [],
  isLoading: false,
  error: null,

  loadProjects: async () => {
    set({ isLoading: true, error: null });
    try {
      const data = await apiFetch<ProjectResponse[]>("/api/v1/projects");
      const active = data.filter((p) => p.status === "ACTIVE").map(mapProject);
      const archived = data.filter((p) => p.status === "ARCHIVED").map(mapProject);
      set({ projects: active, archivedProjects: archived, isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load projects";
      set({ error: message, isLoading: false });
    }
  },

  createProject: async (name, description) => {
    const data = await apiFetch<ProjectResponse>("/api/v1/projects", {
      method: "POST",
      body: JSON.stringify({ name, description: description ?? null }),
    });
    const summary = mapProject(data);
    set((state) => ({ projects: [...state.projects, summary] }));
    return summary;
  },

  renameProject: async (id, name) => {
    const data = await apiFetch<ProjectResponse>(`/api/v1/projects/${id}`, {
      method: "PUT",
      body: JSON.stringify({ name }),
    });
    const updated = mapProject(data);
    set((state) => ({
      projects: state.projects.map((p) => (p.id === id ? updated : p)),
    }));
  },

  duplicateProject: async (id) => {
    const data = await apiFetch<ProjectResponse>(`/api/v1/projects/${id}/duplicate`, {
      method: "POST",
    });
    const copy = mapProject(data);
    set((state) => ({ projects: [...state.projects, copy] }));
  },

  archiveProject: async (id) => {
    const target = get().projects.find((p) => p.id === id);
    if (!target) return;
    await apiFetch<ProjectResponse>(`/api/v1/projects/${id}`, {
      method: "PUT",
      body: JSON.stringify({ status: "ARCHIVED" }),
    });
    set((state) => ({
      projects: state.projects.filter((p) => p.id !== id),
      archivedProjects: [...state.archivedProjects, { ...target }],
    }));
  },

  deleteProject: async (id) => {
    await apiFetch<undefined>(`/api/v1/projects/${id}`, { method: "DELETE" });
    set((state) => ({
      projects: state.projects.filter((p) => p.id !== id),
    }));
  },
}));
