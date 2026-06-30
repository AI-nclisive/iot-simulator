import { create } from "zustand";
import type { AccessMode, SharedRole } from "./access-policy";

type ShellState = {
  accessMode: AccessMode;
  currentProjectId: string;
  setAccessMode: (accessMode: AccessMode) => void;
  setCurrentProjectId: (projectId: string) => void;
  setSharedRole: (sharedRole: SharedRole) => void;
  sharedRole: SharedRole;
};

export const useShellStore = create<ShellState>((set) => ({
  accessMode: "local",
  currentProjectId: "",
  setAccessMode: (accessMode) => set({ accessMode }),
  setCurrentProjectId: (currentProjectId) => set({ currentProjectId }),
  setSharedRole: (sharedRole) => set({ sharedRole }),
  sharedRole: "admin",
}));
