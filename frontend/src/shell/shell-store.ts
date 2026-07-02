import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AccessMode, SharedRole } from "./access-policy";

type ShellState = {
  accessMode: AccessMode;
  currentProjectId: string;
  setAccessMode: (accessMode: AccessMode) => void;
  setCurrentProjectId: (projectId: string) => void;
  setSharedRole: (sharedRole: SharedRole) => void;
  sharedRole: SharedRole;
};

export const useShellStore = create<ShellState>()(
  persist(
    (set) => ({
      accessMode: "local",
      currentProjectId: "",
      setAccessMode: (accessMode) => set({ accessMode }),
      setCurrentProjectId: (currentProjectId) => set({ currentProjectId }),
      setSharedRole: (sharedRole) => set({ sharedRole }),
      sharedRole: "admin",
    }),
    {
      name: "iot-shell",
      partialize: (state) => ({
        accessMode: state.accessMode,
        currentProjectId: state.currentProjectId,
        sharedRole: state.sharedRole,
      }),
    },
  ),
);
