import { create } from "zustand";
import type { AccessMode, SharedRole } from "./access-policy";
import { projects } from "./mock-workspace";

type ShellState = {
  accessMode: AccessMode;
  currentProjectId: string;
  projectRailCollapsed: boolean;
  setAccessMode: (accessMode: AccessMode) => void;
  setCurrentProjectId: (projectId: string) => void;
  setSharedRole: (sharedRole: SharedRole) => void;
  sharedRole: SharedRole;
  toggleProjectRail: () => void;
};

export const useShellStore = create<ShellState>((set) => ({
  accessMode: "local",
  currentProjectId: projects[0].id,
  projectRailCollapsed: false,
  setAccessMode: (accessMode) => set({ accessMode }),
  setCurrentProjectId: (currentProjectId) => set({ currentProjectId }),
  setSharedRole: (sharedRole) => set({ sharedRole }),
  sharedRole: "admin",
  toggleProjectRail: () =>
    set((state) => ({ projectRailCollapsed: !state.projectRailCollapsed })),
}));
