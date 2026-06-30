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

// Backend response shape from GET /api/v1/projects/overview
type ProjectOverviewResponse = {
  projectId: string;
  name: string;
  configuredSources: number;
  runningSources: number;
  reusableArtifacts: number;
  sourcesNeedingAttention: number;
};

function mapProject(
  p: ProjectResponse,
  overviewMap: Map<string, ProjectOverviewResponse>,
): ProjectSummary {
  const overview = overviewMap.get(p.id);
  return {
    id: p.id,
    name: p.name,
    configuredSources: overview?.configuredSources ?? 0,
    runningSources: overview?.runningSources ?? 0,
    reusableArtifacts: overview?.reusableArtifacts ?? 0,
    sourcesNeedingAttention: overview?.sourcesNeedingAttention ?? 0,
    // lastActivity: no dedicated backend field — approximated from updatedAt (IS-055/IS-083 will improve this)
    lastActivity: p.updatedAt,
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
      // Fetch projects list and overview counts in parallel
      const [data, overviewData] = await Promise.all([
        apiFetch<ProjectResponse[]>("/api/v1/projects"),
        apiFetch<ProjectOverviewResponse[]>("/api/v1/projects/overview").catch((err) => {
          console.warn("Failed to load project overview counts; falling back to 0:", err);
          return [] as ProjectOverviewResponse[];
        }),
      ]);

      const overviewMap = new Map<string, ProjectOverviewResponse>(
        overviewData.map((o) => [o.projectId, o]),
      );

      const active = data.filter((p) => p.status === "ACTIVE").map((p) => mapProject(p, overviewMap));
      const archived = data.filter((p) => p.status === "ARCHIVED").map((p) => mapProject(p, overviewMap));
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
    const summary = mapProject(data, new Map());
    set((state) => ({ projects: [...state.projects, summary] }));
    return summary;
  },

  renameProject: async (id, name) => {
    const data = await apiFetch<ProjectResponse>(`/api/v1/projects/${id}`, {
      method: "PUT",
      body: JSON.stringify({ name }),
    });
    // Preserve existing overview counts when renaming
    const existing = get().projects.find((p) => p.id === id);
    const overviewMap = existing
      ? new Map<string, ProjectOverviewResponse>([
          [
            id,
            {
              projectId: id,
              name: existing.name,
              configuredSources: existing.configuredSources,
              runningSources: existing.runningSources,
              reusableArtifacts: existing.reusableArtifacts,
              sourcesNeedingAttention: existing.sourcesNeedingAttention ?? 0,
            },
          ],
        ])
      : new Map<string, ProjectOverviewResponse>();
    const updated = mapProject(data, overviewMap);
    set((state) => ({
      projects: state.projects.map((p) => (p.id === id ? updated : p)),
    }));
  },

  duplicateProject: async (id) => {
    const data = await apiFetch<ProjectResponse>(`/api/v1/projects/${id}/duplicate`, {
      method: "POST",
    });
    const copy = mapProject(data, new Map());
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
