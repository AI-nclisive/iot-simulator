import { useMemo, useState } from "react";
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
import { mockUserSaveShouldFail, mockUsers, type UserRow } from "./mock-users";

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

function mockSaveUser(): Promise<void> {
  return new Promise((resolve, reject) =>
    setTimeout(() => {
      if (mockUserSaveShouldFail) reject(new Error("Service unavailable"));
      else resolve();
    }, 700),
  );
}

export function AdminUsersPage() {
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);
  const notify = useNotificationStore((s) => s.push);

  const [users, setUsers] = useState<UserRow[]>(mockUsers);
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
    setIsSaving(true);
    try {
      await mockSaveUser();
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, role: newRole } : u)),
      );
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
      await mockSaveUser();
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, status: "inactive" } : u)),
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
      await mockSaveUser();
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, status: "active" } : u)),
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
            emptyMessage="Users will appear here once they are added to this project."
            noResultsTitle="No users match the active filters."
            noResultsMessage="Try adjusting the search or filters to find who you're looking for."
          />
        </div>
      </section>

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
