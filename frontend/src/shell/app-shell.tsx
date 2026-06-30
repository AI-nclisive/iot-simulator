import { Link, NavLink, Outlet } from "react-router-dom";
import { ToastRegion } from "../ui/notification-pattern";
import { useNotificationStore } from "./notification-store";
import { useProjectsStore } from "./projects-store";
import { useShellStore } from "./shell-store";
import { resolveAccess } from "./access-policy";

const topLevelNav = [
  { to: "/overview", label: "Overview" },
  { to: "/data-sources", label: "Data Sources" },
  { to: "/recordings", label: "Recordings & Samples" },
  { to: "/scenarios", label: "Scenarios" },
  { to: "/evidence", label: "Evidence" },
  { to: "/activity", label: "Activity" },
  { to: "/settings", label: "Settings" },
  { to: "/admin", label: "Admin" },
] as const;

function navCompactLabel(label: string) {
  const words = label.split(" ");
  if (words.length === 1) {
    return label.slice(0, 2).toUpperCase();
  }

  return words
    .slice(0, 2)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

function projectCompactLabel(name: string) {
  return name
    .split(" ")
    .slice(0, 3)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

export function AppShell() {
  const accessMode = useShellStore((state) => state.accessMode);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const projectRailCollapsed = useShellStore((state) => state.projectRailCollapsed);
  const setCurrentProjectId = useShellStore((state) => state.setCurrentProjectId);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const toggleProjectRail = useShellStore((state) => state.toggleProjectRail);

  const toasts = useNotificationStore((state) => state.toasts);
  const dismissNotification = useNotificationStore((state) => state.dismiss);
  const projects = useProjectsStore((state) => state.projects);

  const currentProject = projects.find((p) => p.id === currentProjectId) ?? null;
  const access = resolveAccess(accessMode, sharedRole);

  return (
    <>
    <div className="min-h-screen px-3 py-3 text-shell-ink sm:px-4 lg:px-5">
      <div className="mx-auto flex min-h-[calc(100vh-1.5rem)] max-w-[1680px] flex-col gap-3">
        <header className="shell-panel px-4 py-3 lg:px-5">
          <h1 className="text-lg font-semibold text-shell-ink">IoT Simulator</h1>
        </header>

        <div className="flex min-h-0 flex-1 flex-col gap-3 lg:flex-row">
          <aside
            aria-label="Project navigation"
            className={`shell-panel shrink-0 overflow-hidden transition-[width] duration-200 ${
              projectRailCollapsed ? "lg:w-[84px]" : "w-full lg:w-[280px]"
            }`}
          >
            <div className="flex h-full flex-col">
              <div className="flex items-start justify-between border-b border-shell-line px-4 py-4">
                {projectRailCollapsed ? (
                  <div className="hidden space-y-2 lg:block">
                    <span className="shell-chip border-shell-line bg-white text-shell-muted">
                      {currentProject ? projectCompactLabel(currentProject.name) : "—"}
                    </span>
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                      Project
                    </p>
                  </div>
                ) : (
                  <div>
                    <p className="text-sm font-semibold uppercase tracking-[0.08em] text-shell-muted">Project</p>
                    <p className="mt-2 text-sm text-shell-ink">{currentProject?.name ?? "—"}</p>
                  </div>
                )}

                <button
                  aria-expanded={!projectRailCollapsed}
                  aria-label={projectRailCollapsed ? "Expand project rail" : "Collapse project rail"}
                  className="shell-action px-2.5"
                  type="button"
                  onClick={toggleProjectRail}
                >
                  {projectRailCollapsed ? "Open" : "Hide"}
                </button>
              </div>

              {projectRailCollapsed ? (
                  <div className="hidden flex-1 px-3 py-4 lg:block">
                    <div className="space-y-2">
                      {topLevelNav.map((item) => (
                        access.canManageAdmin || item.to !== "/admin" ? (
                          <NavLink
                            key={item.to}
                            aria-label={item.label}
                            title={item.label}
                            className={({ isActive }) =>
                              `shell-nav-item-compact ${
                                isActive ? "shell-nav-item-compact-active" : ""
                              }`
                            }
                            to={item.to}
                          >
                            <span aria-hidden="true">{navCompactLabel(item.label)}</span>
                          </NavLink>
                        ) : (
                          <span
                            key={item.to}
                            aria-label={`${item.label} — Admin only`}
                            className="shell-nav-item-compact shell-nav-item-compact-disabled"
                          >
                            <span aria-hidden="true">{navCompactLabel(item.label)}</span>
                          </span>
                        )
                      ))}
                    </div>
                  </div>
              ) : (
                <>
                  <div className="border-b border-shell-line px-4 py-4">
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Switch project
                      <select
                        aria-label="Current project"
                        className="rounded-md border border-shell-line bg-white px-3 py-2 text-shell-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-shell-accent/40"
                        value={currentProject?.id ?? ""}
                        onChange={(event) => setCurrentProjectId(event.target.value)}
                      >
                        {projects.map((project) => (
                          <option key={project.id} value={project.id}>
                            {project.name}
                          </option>
                        ))}
                      </select>
                    </label>

                    <div className="mt-3">
                      <Link className="shell-text-action" to="/projects">
                        Project list
                      </Link>
                    </div>
                  </div>

                  <nav className="flex-1 space-y-1 px-3 py-3" aria-label="Primary">
                    {topLevelNav.map((item) => (
                      access.canManageAdmin || item.to !== "/admin" ? (
                        <NavLink
                          key={item.to}
                          className={({ isActive }) =>
                            `shell-nav-item ${isActive ? "shell-nav-item-active" : ""}`
                          }
                          to={item.to}
                        >
                          <span>{item.label}</span>
                        </NavLink>
                      ) : (
                        <span key={item.to} className="shell-nav-item shell-nav-item-disabled">
                          <span>{item.label}</span>
                          <span className="text-xs text-shell-muted">Admin only</span>
                        </span>
                      )
                    ))}
                  </nav>
                </>
              )}
            </div>
          </aside>

          <div className="flex min-h-0 min-w-0 flex-1 flex-col">
            <main className="min-h-0 min-w-0 flex-1">
              <Outlet />
            </main>
          </div>
        </div>
      </div>
    </div>

    {/* Global toast notifications — rendered via portal into document.body */}
    <ToastRegion toasts={toasts} onDismiss={dismissNotification} />
  </>
  );
}
