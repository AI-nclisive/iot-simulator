import { useEffect, useMemo, useState } from "react";
import { apiFetch, ApiError } from "../api";
import { resolveAccess } from "../shell/access-policy";
import { useNotificationStore } from "../shell/notification-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import {
  OperationalTable,
  TableToolbar,
  type ActiveTableFilter,
  type TableColumn,
  type TableFilterControl,
  type TableRowAction,
  type TableSortState,
} from "../ui/table-pattern";
import { type ActivityEventDto, type ActivityHistoryApiResponse } from "../types/activity";
import { type UserRow } from "./mock-users";

// ── Backend API shape (IS-118) ───────────────────────────────────────────────

type AdminUserApiResponse = {
  id: string;
  displayName: string;
  subject: string;
  status: string;
  role: string;
  lastSeenAt: string | null;
};

type AdminUserListApiResponse = { items: AdminUserApiResponse[] };

function mapApiUser(u: AdminUserApiResponse): UserRow {
  return {
    id: u.id,
    name: u.displayName,
    email: u.subject,
    role: (u.role === "admin" ? "admin" : "user") as UserRow["role"],
    status: (u.status.toLowerCase() === "active" ? "active" : "inactive") as UserRow["status"],
    lastActive: u.lastSeenAt ? new Date(u.lastSeenAt).toLocaleString() : "Never",
  };
}

// ── Helpers ──────────────────────────────────────────────────────────────────

type RoleFilter = "all" | "admin" | "user";
type StatusFilter = "all" | "active" | "inactive";

type ConfirmRequest =
  | { kind: "role-change"; userId: string; newRole: "admin" | "user" }
  | { kind: "deactivate"; userId: string }
  | null;

function roleBadgeTone(role: "admin" | "user") {
  return role === "admin" ? ("warning" as const) : ("neutral" as const);
}

function statusBadgeTone(status: "active" | "inactive") {
  return status === "active" ? ("accent" as const) : ("neutral" as const);
}

function roleLabel(role: "admin" | "user") {
  return role === "admin" ? "Admin" : "User";
}

function statusLabel(status: "active" | "inactive") {
  return status === "active" ? "Active" : "Inactive";
}

// ── Role change activity panel ────────────────────────────────────────────────

function formatEventTime(at: string): string {
  try {
    return new Date(at).toLocaleString();
  } catch {
    return at;
  }
}

function RoleChangeActivityPanel({ entries }: { entries: ActivityEventDto[] }) {
  if (entries.length === 0) {
    return (
      <section className="shell-panel px-5 py-5" aria-label="Role change activity">
        <h2 className="mb-4 text-base font-semibold text-shell-ink">Role change activity</h2>
        <SharedStatePanel
          state="empty"
          message="Role changes made by admins will appear here. Each entry shows who changed the role, what it changed from and to, and when."
        />
      </section>
    );
  }

  return (
    <section className="shell-panel px-5 py-5" aria-label="Role change activity">
      <h2 className="mb-4 text-base font-semibold text-shell-ink">Role change activity</h2>
      <ol className="space-y-3" aria-label="Role change log">
        {entries.map((entry) => {
          const newRole = typeof entry.detail.role === "string" ? entry.detail.role : null;
          const newStatus = typeof entry.detail.status === "string" ? entry.detail.status : null;
          const actionLabel =
            newRole != null
              ? `Role changed to ${roleLabel(newRole === "admin" ? "admin" : "user")}`
              : newStatus != null
                ? `Status changed to ${newStatus.toLowerCase()}`
                : entry.action;
          return (
            <li
              key={entry.id}
              className="flex flex-col gap-1 rounded-md border border-shell-line bg-white px-4 py-3 text-sm sm:flex-row sm:items-start sm:justify-between"
            >
              <div className="min-w-0">
                <p className="font-medium text-shell-ink">{entry.objectId ?? "—"}</p>
                <p className="mt-1 text-shell-muted">
                  {actionLabel}{" "}
                  {newRole != null && (
                    <StatusBadge
                      label={roleLabel(newRole === "admin" ? "admin" : "user")}
                      tone={roleBadgeTone(newRole === "admin" ? "admin" : "user")}
                    />
                  )}
                </p>
              </div>
              <div className="shrink-0 text-right text-shell-muted">
                <p>by {entry.actor}</p>
                <p className="text-xs">{formatEventTime(entry.at)}</p>
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export function AdminUsersPage() {
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);
  const notify = useNotificationStore((s) => s.push);

  const [users, setUsers] = useState<UserRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activityLog, setActivityLog] = useState<ActivityEventDto[]>([]);
  const [searchValue, setSearchValue] = useState("");
  const [roleFilter, setRoleFilter] = useState<RoleFilter>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [sortState, setSortState] = useState<TableSortState>({
    columnId: "name",
    direction: "asc",
  });
  const [confirmRequest, setConfirmRequest] = useState<ConfirmRequest>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [savingUserId, setSavingUserId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    Promise.all([
      apiFetch<AdminUserListApiResponse>("/api/v1/admin/users"),
      apiFetch<ActivityHistoryApiResponse>("/api/v1/admin/activity?objectType=user&limit=20").catch(() => null),
    ])
      .then(([usersData, activityData]) => {
        if (!cancelled) {
          setUsers(usersData.items.map(mapApiUser));
          if (activityData) setActivityLog(activityData.events);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.title : "Failed to load users.");
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const hasQueryState =
    searchValue.trim() !== "" || roleFilter !== "all" || statusFilter !== "all";

  const filtered = useMemo(() => {
    const q = searchValue.trim().toLowerCase();
    return users.filter((u) => {
      if (roleFilter !== "all" && u.role !== roleFilter) return false;
      if (statusFilter !== "all" && u.status !== statusFilter) return false;
      if (q && !u.name.toLowerCase().includes(q) && !u.email.toLowerCase().includes(q))
        return false;
      return true;
    });
  }, [users, searchValue, roleFilter, statusFilter]);

  function isLastActiveAdmin(userId: string): boolean {
    return (
      users.filter((u) => u.role === "admin" && u.status === "active" && u.id !== userId)
        .length === 0
    );
  }

  async function handleRoleChange(userId: string, newRole: "admin" | "user") {
    const affectedUser = users.find((u) => u.id === userId);
    if (!affectedUser) return;
    setIsSaving(true);
    try {
      const updated = await apiFetch<AdminUserApiResponse>(
        `/api/v1/admin/users/${userId}/roles`,
        { method: "PATCH", body: JSON.stringify({ role: newRole }) },
      );
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? mapApiUser(updated) : u)),
      );
      const optimisticEntry: ActivityEventDto = {
        id: Date.now(),
        projectId: null,
        actor: "local",
        action: "change_role",
        objectType: "user",
        objectId: userId,
        at: new Date().toISOString(),
        detail: { role: newRole },
      };
      setActivityLog((prev) => [optimisticEntry, ...prev]);
      setConfirmRequest(null);
      notify({ tone: "success", title: `Role changed to ${roleLabel(newRole)}.` });
    } catch {
      setConfirmRequest(null);
      notify({ tone: "error", title: "Failed to change role. Try again." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDeactivate(userId: string) {
    setIsSaving(true);
    try {
      const updated = await apiFetch<AdminUserApiResponse>(
        `/api/v1/admin/users/${userId}/status`,
        { method: "PATCH", body: JSON.stringify({ status: "SUSPENDED" }) },
      );
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? mapApiUser(updated) : u)),
      );
      setConfirmRequest(null);
      notify({ tone: "success", title: "User deactivated." });
    } catch {
      setConfirmRequest(null);
      notify({ tone: "error", title: "Failed to deactivate user. Try again." });
    } finally {
      setIsSaving(false);
    }
  }

  async function handleActivate(userId: string) {
    setSavingUserId(userId);
    try {
      const updated = await apiFetch<AdminUserApiResponse>(
        `/api/v1/admin/users/${userId}/status`,
        { method: "PATCH", body: JSON.stringify({ status: "ACTIVE" }) },
      );
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? mapApiUser(updated) : u)),
      );
      notify({ tone: "success", title: "User activated." });
    } catch {
      notify({ tone: "error", title: "Failed to activate user. Try again." });
    } finally {
      setSavingUserId(null);
    }
  }

  const columns: TableColumn<UserRow>[] = [
    {
      id: "name",
      header: "User",
      sortable: true,
      sortValue: (u) => u.name,
      cell: (u) => (
        <div>
          <p className="font-medium text-shell-ink">{u.name}</p>
          <p className="text-xs text-shell-muted">{u.email}</p>
        </div>
      ),
    },
    {
      id: "role",
      header: "Role",
      sortable: true,
      sortValue: (u) => u.role,
      cell: (u) => <StatusBadge label={roleLabel(u.role)} tone={roleBadgeTone(u.role)} />,
    },
    {
      id: "status",
      header: "Status",
      sortable: true,
      sortValue: (u) => u.status,
      cell: (u) => (
        <StatusBadge label={statusLabel(u.status)} tone={statusBadgeTone(u.status)} />
      ),
    },
    {
      id: "lastActive",
      header: "Last active",
      cell: (u) => <span className="text-shell-muted">{u.lastActive}</span>,
    },
  ];

  const rowActions = access.isAdmin
    ? (u: UserRow): TableRowAction<UserRow>[] => [
        {
          label: u.role === "admin" ? "Make User" : "Make Admin",
          onClick: () => {
            if (u.role === "admin" && isLastActiveAdmin(u.id)) {
              notify({
                tone: "warning",
                title: "Cannot change role.",
                message: "At least one Admin must remain active.",
              });
              return;
            }
            setConfirmRequest({
              kind: "role-change",
              userId: u.id,
              newRole: u.role === "admin" ? "user" : "admin",
            });
          },
        },
        u.status === "active"
          ? {
              label: "Deactivate",
              tone: "danger" as const,
              onClick: () => {
                if (u.role === "admin" && isLastActiveAdmin(u.id)) {
                  notify({
                    tone: "warning",
                    title: "Cannot deactivate.",
                    message: "At least one Admin must remain active.",
                  });
                  return;
                }
                setConfirmRequest({ kind: "deactivate", userId: u.id });
              },
            }
          : {
              label: savingUserId === u.id ? "Saving…" : "Activate",
              onClick: () => handleActivate(u.id),
            },
      ]
    : undefined;

  const filters: TableFilterControl[] = [
    {
      id: "role",
      label: "Role",
      value: roleFilter,
      options: [
        { label: "All roles", value: "all" },
        { label: "Admin", value: "admin" },
        { label: "User", value: "user" },
      ],
      onChange: (v) => setRoleFilter(v as RoleFilter),
    },
    {
      id: "status",
      label: "Status",
      value: statusFilter,
      options: [
        { label: "All statuses", value: "all" },
        { label: "Active", value: "active" },
        { label: "Inactive", value: "inactive" },
      ],
      onChange: (v) => setStatusFilter(v as StatusFilter),
    },
  ];

  const activeFilters: ActiveTableFilter[] = [
    ...(roleFilter !== "all"
      ? [
          {
            id: "role",
            label: "Role",
            value: roleLabel(roleFilter as "admin" | "user"),
            onClear: () => setRoleFilter("all"),
          },
        ]
      : []),
    ...(statusFilter !== "all"
      ? [
          {
            id: "status",
            label: "Status",
            value: statusLabel(statusFilter as "active" | "inactive"),
            onClear: () => setStatusFilter("all"),
          },
        ]
      : []),
  ];

  const confirmingUser =
    confirmRequest !== null
      ? users.find((u) => u.id === confirmRequest.userId)
      : null;

  if (access.isSharedUser) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="User and role management is restricted to Admins. Contact your project administrator to request changes."
            state="locked"
            title="Admin access is required."
          />
        </section>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="mb-5">
          <h2 className="text-2xl font-semibold text-shell-ink">Users</h2>
          <p className="mt-2 text-sm text-shell-muted">
            Manage who has access to this project and their roles.{" "}
            {users.length} user{users.length !== 1 ? "s" : ""} in this project.
          </p>
        </div>

        {loadError ? (
          <SharedStatePanel state="error" message={loadError} />
        ) : isLoading ? (
          <SharedStatePanel state="loading" message="Loading users…" />
        ) : (
          <>
            <TableToolbar
              searchValue={searchValue}
              searchPlaceholder="Search by name or email"
              onSearchChange={setSearchValue}
              filters={filters}
              activeFilters={activeFilters}
              onClearAll={() => {
                setSearchValue("");
                setRoleFilter("all");
                setStatusFilter("all");
              }}
              resultLabel={`${filtered.length} result${filtered.length !== 1 ? "s" : ""}`}
            />

            <div className="mt-5">
              <OperationalTable
                columns={columns}
                rows={filtered}
                rowKey={(u) => u.id}
                rowActions={rowActions}
                sortState={sortState}
                onSortChange={setSortState}
                hasQueryState={hasQueryState}
                emptyTitle="No users yet."
                emptyMessage="Users will appear here once they log in for the first time."
                noResultsTitle="No users match the active filters."
                noResultsMessage="Try adjusting the search or filters to find who you're looking for."
              />
            </div>
          </>
        )}
      </section>

      <RoleChangeActivityPanel entries={activityLog} />

      {confirmRequest?.kind === "role-change" && confirmingUser ? (
        <ConfirmationDialog
          open
          tone="warning"
          title={`Change role for ${confirmingUser.name}?`}
          message={`This will change their role from ${roleLabel(confirmingUser.role)} to ${roleLabel(confirmRequest.newRole)}. Their access level will update immediately.`}
          objectLabel={confirmingUser.email}
          reversibilityLabel="Role can be changed again at any time."
          confirmLabel="Change role"
          isProcessing={isSaving}
          onConfirm={() => handleRoleChange(confirmRequest.userId, confirmRequest.newRole)}
          onClose={() => { if (!isSaving) setConfirmRequest(null); }}
        />
      ) : null}

      {confirmRequest?.kind === "deactivate" && confirmingUser ? (
        <ConfirmationDialog
          open
          tone="warning"
          title={`Deactivate ${confirmingUser.name}?`}
          message="They will no longer be able to access this project. You can reactivate them at any time."
          objectLabel={confirmingUser.email}
          reversibilityLabel="Can be reactivated from the users list."
          confirmLabel="Deactivate"
          isProcessing={isSaving}
          onConfirm={() => handleDeactivate(confirmRequest.userId)}
          onClose={() => { if (!isSaving) setConfirmRequest(null); }}
        />
      ) : null}
    </div>
  );
}
