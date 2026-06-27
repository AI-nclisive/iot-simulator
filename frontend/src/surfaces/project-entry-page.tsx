import { createPortal } from "react-dom";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { type ProjectSummary } from "../shell/mock-workspace";
import { resolveAccess } from "../shell/access-policy";
import { useProjectsStore } from "../shell/projects-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";

function summaryLabel(value: number, singular: string, plural: string) {
  return `${value} ${value === 1 ? singular : plural}`;
}

type ImportState =
  | { phase: "idle" }
  | { phase: "ready"; fileName: string; compatible: boolean; overwriteTarget: string | null }
  | { phase: "importing"; fileName: string; overwriteTarget: string | null }
  | { phase: "failed"; reason: string; fileName: string; overwriteTarget: string | null }
  | { phase: "done"; projectName: string };

function ImportProjectDialog({
  onClose,
  projects,
}: {
  onClose: () => void;
  projects: ProjectSummary[];
}) {
  const [fileName, setFileName] = useState("");
  const [state, setState] = useState<ImportState>({ phase: "idle" });

  function handleFileInput(value: string) {
    setFileName(value);
    if (!value.trim()) {
      setState({ phase: "idle" });
      return;
    }

    const isCompatible = !value.toLowerCase().includes("old");
    const existingProject = projects.find((p) =>
      p.name.toLowerCase() === value.replace(/\.iotsim$/, "").toLowerCase()
    );

    setState({
      phase: "ready",
      fileName: value.trim(),
      compatible: isCompatible,
      overwriteTarget: existingProject ? existingProject.name : null,
    });
  }

  function handleImport() {
    if (state.phase !== "ready" || !state.compatible) return;

    const { fileName, overwriteTarget } = state;
    setState({ phase: "importing", fileName, overwriteTarget });

    setTimeout(() => {
      const shouldFail = fileName.toLowerCase().includes("broken");
      if (shouldFail) {
        setState({ phase: "failed", reason: "The archive is malformed or missing required files.", fileName, overwriteTarget });
      } else {
        setState({ phase: "done", projectName: fileName.replace(/\.iotsim$/, "") });
      }
    }, 1200);
  }

  const isImporting = state.phase === "importing";

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div
        className="w-full max-w-md rounded-lg border border-shell-line bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-dialog-title"
      >
        <div className="border-b border-shell-line px-5 py-4">
          <h2 id="import-dialog-title" className="text-base font-semibold text-shell-ink">
            Import project
          </h2>
        </div>

        <div className="space-y-4 px-5 py-5">
          {state.phase === "done" ? (
            <div className="space-y-4">
              <SharedStatePanel
                message={`"${state.projectName}" was imported successfully and is now available in the project list.`}
                state="empty"
                title="Import complete."
              />
            </div>
          ) : (
            <>
              <label className="flex flex-col gap-2 text-sm text-shell-muted">
                Project file name
                <input
                  className="shell-field"
                  disabled={isImporting}
                  placeholder="assembly-line-a.iotsim"
                  type="text"
                  value={fileName}
                  onChange={(event) => handleFileInput(event.target.value)}
                />
                <span className="text-xs text-shell-muted">
                  Accepted format: <code>.iotsim</code> archive
                </span>
              </label>

              {state.phase === "ready" || state.phase === "importing" || state.phase === "failed" ? (
                <div className="space-y-3 rounded-md border border-shell-line bg-shell-base/50 px-4 py-3 text-sm">
                  <div className="flex items-center justify-between">
                    <span className="text-shell-muted">Version compatibility</span>
                    <span
                      className={
                        state.phase === "ready" && !state.compatible
                          ? "font-medium text-shell-danger"
                          : "font-medium text-shell-ink"
                      }
                    >
                      {state.phase === "ready"
                        ? state.compatible
                          ? "Compatible"
                          : "Incompatible — cannot import"
                        : "Compatible"}
                    </span>
                  </div>

                  {(state.phase === "ready" || state.phase === "importing" || state.phase === "failed") && state.overwriteTarget ? (
                    <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                      A project named <span className="font-medium">"{state.overwriteTarget}"</span> already
                      exists. Importing will overwrite its saved configuration.
                    </div>
                  ) : null}

                  {state.phase === "failed" ? (
                    <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
                      Import failed: {state.reason}
                    </div>
                  ) : null}
                </div>
              ) : null}

              {isImporting ? (
                <p className="text-center text-sm text-shell-muted">Importing…</p>
              ) : null}
            </>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-shell-line px-5 py-4">
          <button className="shell-action" type="button" onClick={onClose}>
            {state.phase === "done" ? "Close" : "Cancel"}
          </button>
          {state.phase !== "done" ? (
            <button
              className="shell-action"
              disabled={
                isImporting ||
                state.phase === "idle" ||
                (state.phase === "ready" && !state.compatible)
              }
              type="button"
              onClick={
                state.phase === "failed"
                  ? () => handleFileInput(state.fileName)
                  : handleImport
              }
            >
              {state.phase === "failed" ? "Retry import" : "Import"}
            </button>
          ) : null}
        </div>
      </div>
    </div>,
    document.body,
  );
}

type LifecycleRequest =
  | { action: "rename"; projectId: string; currentName: string }
  | { action: "archive"; projectId: string; name: string; runningSources: number }
  | { action: "delete"; projectId: string; name: string; runningSources: number }
  | null;

function RenameProjectDialog({
  currentName,
  onClose,
  onConfirm,
}: {
  currentName: string;
  onClose: () => void;
  onConfirm: (name: string) => void;
}) {
  const [name, setName] = useState(currentName);
  const isValid = name.trim().length > 0 && name.trim() !== currentName;

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div
        className="w-full max-w-sm rounded-lg border border-shell-line bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="rename-dialog-title"
      >
        <div className="border-b border-shell-line px-5 py-4">
          <h2 id="rename-dialog-title" className="text-base font-semibold text-shell-ink">
            Rename project
          </h2>
        </div>
        <div className="px-5 py-5">
          <label className="flex flex-col gap-2 text-sm text-shell-muted">
            Project name
            <input
              autoFocus
              className="shell-field"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        </div>
        <div className="flex items-center justify-end gap-2 border-t border-shell-line px-5 py-4">
          <button className="shell-action" type="button" onClick={onClose}>
            Cancel
          </button>
          <button
            className="shell-action"
            disabled={!isValid}
            type="button"
            onClick={() => onConfirm(name.trim())}
          >
            Rename
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

export function ProjectEntryPage() {
  const navigate = useNavigate();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const setCurrentProjectId = useShellStore((state) => state.setCurrentProjectId);
  const projects = useProjectsStore((state) => state.projects);
  const renameProject = useProjectsStore((state) => state.renameProject);
  const duplicateProject = useProjectsStore((state) => state.duplicateProject);
  const archiveProject = useProjectsStore((state) => state.archiveProject);
  const deleteProject = useProjectsStore((state) => state.deleteProject);
  const access = resolveAccess(accessMode, sharedRole);
  const [importOpen, setImportOpen] = useState(false);
  const [lifecycleRequest, setLifecycleRequest] = useState<LifecycleRequest>(null);

  function openProject(projectId: string) {
    setCurrentProjectId(projectId);
    navigate("/overview");
  }

  const confirmationModel = (() => {
    if (!lifecycleRequest) return null;

    if (lifecycleRequest.action === "archive") {
      return {
        title: "Archive this project?",
        message: "Archiving removes the project from this view. Existing data is preserved.",
        objectLabel: lifecycleRequest.name,
        confirmLabel: "Archive project",
        reversibilityLabel: "Archived projects can be restored from the admin area.",
        tone: "warning" as const,
        impacts: [
          {
            label: "Active sources",
            value:
              lifecycleRequest.runningSources > 0
                ? `${lifecycleRequest.runningSources} source${lifecycleRequest.runningSources === 1 ? "" : "s"} are currently running. They will stop when the project is archived.`
                : "No sources are currently running.",
          },
        ],
      };
    }

    if (lifecycleRequest.action === "delete") {
      return {
        title: "Delete this project?",
        message:
          "Deleting a project removes all its saved configuration, sources, and data permanently.",
        objectLabel: lifecycleRequest.name,
        confirmLabel: "Delete project",
        reversibilityLabel: "This action is not reversible. All project data will be lost.",
        tone: "danger" as const,
        impacts: [
          {
            label: "Active sources",
            value:
              lifecycleRequest.runningSources > 0
                ? `${lifecycleRequest.runningSources} source${lifecycleRequest.runningSources === 1 ? "" : "s"} are currently running and will be stopped.`
                : "No sources are currently running.",
          },
          {
            label: "Shared impact",
            value:
              "Anyone working in this project will lose access immediately. This cannot be undone.",
          },
        ],
      };
    }

    return null;
  })();

  function confirmLifecycleAction() {
    if (!lifecycleRequest) return;

    if (lifecycleRequest.action === "archive") {
      archiveProject(lifecycleRequest.projectId);
    } else if (lifecycleRequest.action === "delete") {
      deleteProject(lifecycleRequest.projectId);
    }

    setLifecycleRequest(null);
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
                  onClick={() => setImportOpen(true)}
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

                  <div className="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-shell-line pt-4">
                    <div className="flex flex-wrap gap-2">
                      {access.isAdmin ? (
                        <>
                          <button
                            className="shell-text-action"
                            type="button"
                            onClick={() =>
                              setLifecycleRequest({
                                action: "rename",
                                projectId: project.id,
                                currentName: project.name,
                              })
                            }
                          >
                            Rename
                          </button>
                          <button
                            className="shell-text-action"
                            type="button"
                            onClick={() => duplicateProject(project.id)}
                          >
                            Duplicate
                          </button>
                          <button
                            className="shell-text-action"
                            type="button"
                            onClick={() =>
                              setLifecycleRequest({
                                action: "archive",
                                projectId: project.id,
                                name: project.name,
                                runningSources: project.runningSources,
                              })
                            }
                          >
                            Archive
                          </button>
                          <button
                            className="shell-text-action-danger"
                            type="button"
                            onClick={() =>
                              setLifecycleRequest({
                                action: "delete",
                                projectId: project.id,
                                name: project.name,
                                runningSources: project.runningSources,
                              })
                            }
                          >
                            Delete
                          </button>
                        </>
                      ) : null}
                    </div>
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

      {importOpen ? (
        <ImportProjectDialog projects={projects} onClose={() => setImportOpen(false)} />
      ) : null}

      {lifecycleRequest?.action === "rename" ? (
        <RenameProjectDialog
          currentName={lifecycleRequest.currentName}
          onClose={() => setLifecycleRequest(null)}
          onConfirm={(name) => {
            renameProject(lifecycleRequest.projectId, name);
            setLifecycleRequest(null);
          }}
        />
      ) : null}

      {confirmationModel ? (
        <ConfirmationDialog
          confirmLabel={confirmationModel.confirmLabel}
          impacts={confirmationModel.impacts}
          message={confirmationModel.message}
          objectLabel={confirmationModel.objectLabel}
          open={Boolean(confirmationModel)}
          reversibilityLabel={confirmationModel.reversibilityLabel}
          title={confirmationModel.title}
          tone={confirmationModel.tone}
          onClose={() => setLifecycleRequest(null)}
          onConfirm={confirmLifecycleAction}
        />
      ) : null}
    </div>
  );
}
