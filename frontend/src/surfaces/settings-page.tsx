import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useProjectsStore } from "../shell/projects-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type ExportScope = "full" | "config-only";
type ExportState =
  | { phase: "idle" }
  | { phase: "exporting" }
  | { phase: "done"; fileName: string }
  | { phase: "failed"; reason: string };

type SaveState = "idle" | "saving" | "saved" | "error";

function ProjectNameField({
  initialName,
  canEdit,
  onSave,
}: {
  initialName: string;
  canEdit: boolean;
  onSave: (name: string) => Promise<void>;
}) {
  const [name, setName] = useState(initialName);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [validationError, setValidationError] = useState("");
  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (savedTimerRef.current) clearTimeout(savedTimerRef.current);
    };
  }, []);

  async function handleSave() {
    if (!canEdit) return;
    const trimmed = name.trim();
    if (!trimmed) {
      setValidationError("Project name cannot be empty.");
      return;
    }
    setValidationError("");
    setSaveState("saving");
    try {
      await onSave(trimmed);
      setSaveState("saved");
      savedTimerRef.current = setTimeout(() => setSaveState("idle"), 2500);
    } catch {
      setSaveState("error");
    }
  }

  if (!canEdit) {
    return (
      <div className="flex flex-col gap-1">
        <dt className="text-sm text-shell-muted">Project name</dt>
        <dd className="text-sm font-medium text-shell-ink">{initialName}</dd>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-2 text-sm text-shell-muted">
        Project name
        <input
          className="shell-field"
          disabled={saveState === "saving"}
          maxLength={80}
          type="text"
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setValidationError("");
            if (saveState === "saved") setSaveState("idle");
          }}
        />
      </label>

      {validationError ? (
        <p className="text-sm text-red-600" role="alert">
          {validationError}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-3">
        <button
          className="shell-action"
          disabled={saveState === "saving" || name.trim() === initialName}
          type="button"
          onClick={handleSave}
        >
          {saveState === "saving" ? "Saving…" : "Save name"}
        </button>

        {saveState === "saved" ? (
          <StatusBadge label="Saved" tone="accent" />
        ) : null}

        {saveState === "error" ? (
          <span className="text-sm text-red-600" role="alert">
            Save failed. Try again.
          </span>
        ) : null}
      </div>
    </div>
  );
}

function ExportProjectSection({
  projectName,
  canExport,
}: {
  projectName: string;
  canExport: boolean;
}) {
  const [scope, setScope] = useState<ExportScope>("full");
  const [exportState, setExportState] = useState<ExportState>({ phase: "idle" });

  function startExport() {
    if (!canExport) return;
    setExportState({ phase: "exporting" });

    setTimeout(() => {
      const scopeSuffix = scope === "config-only" ? "-config" : "-full";
      const safeName = projectName.toLowerCase().replace(/\s+/g, "-");
      setExportState({ phase: "done", fileName: `${safeName}${scopeSuffix}.iotsim` });
    }, 1000);
  }

  const isExporting = exportState.phase === "exporting";

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-sm font-semibold text-shell-ink">Export project</h3>
        <p className="mt-1 text-sm text-shell-muted">
          Export this project as an <code>.iotsim</code> archive for backup or migration.
        </p>
      </div>

      <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        Credentials, private keys, and secrets are never included in exported archives.
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium text-shell-ink">Export scope</p>
        <div className="flex flex-col gap-2">
          <label className="flex cursor-pointer items-center gap-3">
            <input
              checked={scope === "full"}
              className="h-4 w-4 accent-shell-accent"
              disabled={!canExport || isExporting}
              name="export-scope"
              type="radio"
              value="full"
              onChange={() => {
                setScope("full");
                setExportState({ phase: "idle" });
              }}
            />
            <span className="text-sm text-shell-ink">
              Full project — includes sources, recordings, samples, and configuration
            </span>
          </label>
          <label className="flex cursor-pointer items-center gap-3">
            <input
              checked={scope === "config-only"}
              className="h-4 w-4 accent-shell-accent"
              disabled={!canExport || isExporting}
              name="export-scope"
              type="radio"
              value="config-only"
              onChange={() => {
                setScope("config-only");
                setExportState({ phase: "idle" });
              }}
            />
            <span className="text-sm text-shell-ink">
              Configuration only — structure and settings without recorded data
            </span>
          </label>
        </div>
      </div>

      {exportState.phase === "done" ? (
        <div className="flex items-center gap-3 rounded-md border border-shell-line bg-shell-base/50 px-4 py-3">
          <StatusBadge label="Saved" tone="accent" />
          <span className="text-sm text-shell-ink">{exportState.fileName}</span>
          <button
            className="shell-text-action ml-auto"
            type="button"
            onClick={() => setExportState({ phase: "idle" })}
          >
            Export again
          </button>
        </div>
      ) : null}

      {exportState.phase === "failed" ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          Export failed: {exportState.reason}
        </p>
      ) : null}

      {!canExport ? (
        <SharedStatePanel
          message="Only Admin can export the project. Contact your project administrator."
          state="locked"
          title="Export is restricted to Admin."
        />
      ) : (
        <button
          className="shell-action"
          disabled={isExporting}
          type="button"
          onClick={startExport}
        >
          {isExporting ? "Exporting…" : "Export project"}
        </button>
      )}
    </div>
  );
}

export function SettingsPage() {
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const projects = useProjectsStore((state) => state.projects);
  const renameProject = useProjectsStore((state) => state.renameProject);
  const access = resolveAccess(accessMode, sharedRole);

  const currentProject = projects.find((p) => p.id === currentProjectId) ?? projects[0] ?? null;
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080 (dev proxy)";

  return (
    <div className="flex h-full flex-col gap-5">
      {/* Project settings */}
      <section className="shell-panel px-5 py-5">
        <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-shell-ink">Project settings</h2>
            <p className="mt-1 text-sm text-shell-muted">
              Configuration scoped to{" "}
              <span className="font-medium">{currentProject?.name ?? "—"}</span>.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={access.modeLabel} tone="neutral" />
            <StatusBadge label={access.effectiveRoleLabel} tone="neutral" />
          </div>
        </div>

        <dl className="grid gap-6 lg:grid-cols-2">
          <ProjectNameField
            key={currentProjectId}
            canEdit={access.isAdmin}
            initialName={currentProject?.name ?? ""}
            onSave={(name) => renameProject(currentProjectId, name)}
          />
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">Project ID</dt>
            <dd className="font-mono text-sm text-shell-ink">{currentProject?.id ?? "—"}</dd>
          </div>
        </dl>

        {!access.isAdmin ? (
          <div className="mt-5">
            <SharedStatePanel
              message="Project settings are read-only in the current role. Contact Admin to make changes."
              state="locked"
              title="Settings are read-only."
            />
          </div>
        ) : null}

        {access.canImportProject ? (
          <div className="mt-5 border-t border-shell-line pt-5">
            <h3 className="text-sm font-semibold text-shell-ink">Import project</h3>
            <p className="mt-1 text-sm text-shell-muted">
              Replace or merge this project from an <code>.iotsim</code> archive.
            </p>
            <div className="mt-3">
              <Link className="shell-action inline-block" to="/projects/import">
                Import project
              </Link>
            </div>
          </div>
        ) : null}
      </section>

      {/* Export */}
      <section className="shell-panel px-5 py-5">
        <ExportProjectSection
          canExport={access.isAdmin}
          projectName={currentProject?.name ?? ""}
        />
      </section>

      {/* Environment settings */}
      <section className="shell-panel px-5 py-5">
        <div className="mb-5">
          <h2 className="text-lg font-semibold text-shell-ink">Environment settings</h2>
          <p className="mt-1 text-sm text-shell-muted">
            System-level configuration. These values are set at deployment time and
            cannot be changed here.
          </p>
        </div>

        <dl className="grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">API endpoint</dt>
            <dd className="font-mono text-sm text-shell-ink">{apiBaseUrl}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">Environment mode</dt>
            <dd className="text-sm font-medium text-shell-ink">{access.modeLabel}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">App version</dt>
            <dd className="font-mono text-sm text-shell-ink">0.1.0</dd>
          </div>
          {access.isShared ? (
            <div className="flex flex-col gap-1">
              <dt className="text-sm text-shell-muted">Your role</dt>
              <dd className="text-sm font-medium text-shell-ink">{access.sharedRoleLabel}</dd>
            </div>
          ) : null}
        </dl>
      </section>
    </div>
  );
}
