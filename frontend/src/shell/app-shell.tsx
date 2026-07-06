import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { ToastRegion } from "../ui/notification-pattern";
import { useNotificationStore } from "./notification-store";
import { useProjectsStore } from "./projects-store";
import { useShellStore } from "./shell-store";
import { resolveAccess } from "./access-policy";

const topLevelNav = [
  { to: "/overview", label: "Overview" },
  { to: "/data-sources", label: "Data Sources" },
  { to: "/recordings", label: "Recordings" },
  { to: "/scenarios", label: "Scenarios" },
  { to: "/evidence", label: "Evidence" },
  { to: "/settings", label: "Settings" },
  { to: "/admin", label: "Admin" },
] as const;

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const accessMode = useShellStore((state) => state.accessMode);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const setCurrentProjectId = useShellStore((state) => state.setCurrentProjectId);
  const sharedRole = useShellStore((state) => state.sharedRole);

  const toasts = useNotificationStore((state) => state.toasts);
  const dismissNotification = useNotificationStore((state) => state.dismiss);
  const projects = useProjectsStore((state) => state.projects);
  const loadProjects = useProjectsStore((state) => state.loadProjects);

  // Mobile nav: collapsed by default on narrow screens, always visible on lg+
  const [navOpen, setNavOpen] = useState(false);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    if (!currentProjectId && location.pathname !== "/projects") {
      navigate("/projects", { replace: true });
    }
  }, [currentProjectId, location.pathname, navigate]);

  const currentProject = projects.find((p) => p.id === currentProjectId) ?? null;
  const access = resolveAccess(accessMode, sharedRole);

  return (
    <>
    <div className="min-h-screen px-3 py-3 text-shell-ink sm:px-4 lg:px-5">
      <div className="mx-auto flex min-h-[calc(100vh-1.5rem)] max-w-[1680px] flex-col gap-3">
        {/* Top bar — product identity + mobile nav toggle */}
        <header className="shell-panel flex items-center justify-between px-4 py-3 lg:px-5">
          <h1 className="text-lg font-semibold text-shell-ink">IoT Simulator</h1>
          {/* Hamburger button — only visible below lg breakpoint (1024 px) */}
          <button
            aria-controls="project-rail"
            aria-expanded={navOpen}
            aria-label={navOpen ? "Close navigation" : "Open navigation"}
            className="inline-flex items-center justify-center rounded-md border border-shell-line bg-white p-2 text-shell-ink transition hover:border-shell-accent hover:text-shell-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-shell-accent/40 lg:hidden"
            type="button"
            onClick={() => setNavOpen((open) => !open)}
          >
            {/* Three-line hamburger icon rendered with spans */}
            <span aria-hidden="true" className="flex h-5 w-5 flex-col items-center justify-center gap-[4px]">
              <span className="block h-[2px] w-full rounded-sm bg-current" />
              <span className="block h-[2px] w-full rounded-sm bg-current" />
              <span className="block h-[2px] w-full rounded-sm bg-current" />
            </span>
          </button>
        </header>

        <div className="flex min-h-0 flex-1 flex-col gap-3 lg:flex-row">
          <aside
            id="project-rail"
            aria-label="Project navigation"
            className={`shell-panel w-full shrink-0 lg:block lg:w-[280px] ${navOpen ? "block" : "hidden"}`}
          >
            <div className="flex h-full flex-col">
              <div className="border-b border-shell-line px-4 py-4">
                <p className="text-sm font-semibold uppercase tracking-[0.08em] text-shell-muted">Project</p>
                <p className="mt-2 text-sm text-shell-ink">{currentProject?.name ?? "—"}</p>
              </div>

              <div className="border-b border-shell-line px-4 py-4">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Switch project
                  <select
                    aria-label="Current project"
                    className="rounded-md border border-shell-line bg-white px-3 py-2 text-shell-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-shell-accent/40"
                    value={currentProjectId}
                    onChange={(event) => setCurrentProjectId(event.target.value)}
                  >
                    <option value="" disabled>— Select a project —</option>
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
                {topLevelNav.map((item) => {
                  if (item.to === "/admin" && !access.canManageAdmin) return null;
                  return (
                    <NavLink
                      key={item.to}
                      className={({ isActive }) =>
                        `shell-nav-item ${isActive ? "shell-nav-item-active" : ""}`
                      }
                      to={item.to}
                      onClick={() => setNavOpen(false)}
                    >
                      <span>{item.label}</span>
                    </NavLink>
                  );
                })}
              </nav>
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
