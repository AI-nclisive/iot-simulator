import { useNavigate } from "react-router-dom";
import { projects } from "../shell/mock-workspace";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";

function summaryLabel(value: number, singular: string, plural: string) {
  return `${value} ${value === 1 ? singular : plural}`;
}

export function ProjectEntryPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const setCurrentProjectId = useShellStore((state) => state.setCurrentProjectId);
  const access = resolveAccess(accessMode, sharedRole);

  function openProject(projectId: string) {
    setCurrentProjectId(projectId);
    navigate("/overview");
  }

  return (
    <div className="min-h-screen px-3 py-3 text-shell-ink sm:px-4 lg:px-5">
      <div className="mx-auto flex min-h-[calc(100vh-1.5rem)] max-w-[1280px] flex-col gap-3">
        <header className="shell-panel px-4 py-4 lg:px-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <h1 className="text-lg font-semibold text-shell-ink">IoT Simulator</h1>
              <p className="mt-3 text-sm font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Projects
              </p>
              <p className="mt-2 text-sm leading-6 text-shell-muted">
                Choose a saved simulator setup and continue into the core scan,
                record, and replay flow.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {access.canImportProject ? (
                <button
                  className="shell-action"
                  type="button"
                  onClick={() => navigate("/projects/import")}
                >
                  Import project
                </button>
              ) : null}
              {access.canCreateProject ? (
                <button
                  className="shell-action"
                  type="button"
                  onClick={() => navigate("/projects/create")}
                >
                  Create project
                </button>
              ) : null}
            </div>
          </div>
        </header>

        {projects.length === 0 ? (
          <section className="shell-panel px-5 py-5">
            <SharedStatePanel
              actionLabel={access.canCreateProject ? "Create project" : undefined}
              message="Create or import a simulator project to start working with data-sources, recordings, and replay."
              onAction={
                access.canCreateProject ? () => navigate("/projects/create") : undefined
              }
              state="empty"
              title="No projects are available yet."
            />
          </section>
        ) : (
          <section className="grid gap-3 lg:grid-cols-2 xl:grid-cols-3">
            {projects.map((project) => (
              <article key={project.id} className="shell-panel px-5 py-5">
                <div className="flex h-full flex-col">
                  <div className="min-w-0">
                    <p className="text-base font-semibold text-shell-ink">{project.name}</p>
                    <p className="mt-2 text-sm leading-6 text-shell-muted">
                      {project.lastActivity}
                    </p>
                  </div>

                  <dl className="mt-4 grid gap-3 border-t border-shell-line pt-4 text-sm text-shell-muted sm:grid-cols-3">
                    <div>
                      <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                        Sources
                      </dt>
                      <dd className="mt-2 text-sm font-medium text-shell-ink">
                        {summaryLabel(project.configuredSources, "source", "sources")}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                        Running
                      </dt>
                      <dd className="mt-2 text-sm font-medium text-shell-ink">
                        {summaryLabel(project.runningSources, "source", "sources")}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                        Reusable data
                      </dt>
                      <dd className="mt-2 text-sm font-medium text-shell-ink">
                        {summaryLabel(project.reusableArtifacts, "artifact", "artifacts")}
                      </dd>
                    </div>
                  </dl>

                  <div className="mt-5 flex items-center justify-end">
                    <button
                      className="shell-action"
                      type="button"
                      onClick={() => openProject(project.id)}
                    >
                      Open project
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </section>
        )}
      </div>
    </div>
  );
}
