export type AccessMode = "local" | "shared";
export type SharedRole = "admin" | "user";

function roleLabel(role: SharedRole) {
  return role === "admin" ? "Admin" : "User";
}

export function resolveAccess(accessMode: AccessMode, sharedRole: SharedRole) {
  const isLocal = accessMode === "local";
  const isShared = accessMode === "shared";
  const isAdmin = isLocal || sharedRole === "admin";
  const isSharedUser = isShared && sharedRole === "user";

  return {
    accessMode,
    canConfigureReplay: isAdmin,
    canCreateProject: isAdmin,
    canCreateSource: isAdmin,
    canDeleteSource: isAdmin,
    canDuplicateSource: isAdmin,
    canImportProject: isAdmin,
    canManageAdmin: isShared && sharedRole === "admin",
    canRecordSource: isAdmin,
    canStopSource: true,
    canStartStoppedSource: true,
    effectiveRoleLabel: isLocal ? "Trusted local" : roleLabel(sharedRole),
    isAdmin,
    isLocal,
    isShared,
    isSharedUser,
    modeLabel: isLocal ? "Local mode" : "Shared mode",
    sharedRole,
    sharedRoleLabel: roleLabel(sharedRole),
  };
}
