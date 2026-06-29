import { useState } from "react";
import { resolveAccess } from "../shell/access-policy";
import { mockExportShouldFail, projects } from "../shell/mock-workspace";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

type ExportScope = "full" | "config-only";
type ExportState =
  | { phase: "idle" }
  | { phase: "exporting" }
  | { phase: "done"; fileName: string }
  | { phase: "failed"; reason: string };

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
      const shouldFail = mockExportShouldFail;
      if (shouldFail) {
        setExportState({ phase: "failed", reason: "Export service is temporarily unavailable." });
      } else {
        const scopeSuffix = scope === "config-only" ? "-config" : "-full";
        const safeName = projectName.toLowerCase().replace(/\s+/g, "-");
        setExportState({
          phase: "done",
          fileName: `${safeName}${scopeSuffix}.iotsim`,
        });
      }
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
  const access = resolveAccess(accessMode, sharedRole);

  const currentProject = projects.find((p) => p.id === currentProjectId) ?? projects[0];

  return (
    <div className="flex h-full flex-col gap-5">
      <section className="shell-panel px-5 py-5">
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-shell-ink">Project settings</h2>
          <p className="mt-1 text-sm text-shell-muted">
            Configuration for <span className="font-medium">{currentProject.name}</span>.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge label={access.modeLabel} tone="neutral" />
          <StatusBadge label={access.effectiveRoleLabel} tone="neutral" />
        </div>

        <dl className="mt-5 grid gap-4 xl:grid-cols-2">
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">Project name</dt>
            <dd className="text-sm font-medium text-shell-ink">{currentProject.name}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-sm text-shell-muted">Environment mode</dt>
            <dd className="text-sm font-medium text-shell-ink">{access.modeLabel}</dd>
          </div>
        </dl>

        {!access.isAdmin ? (
          <p className="mt-4 text-sm text-shell-muted">
            Project settings are read-only in the current role.
          </p>
        ) : null}
      </section>

      <section className="shell-panel px-5 py-5">
        <ExportProjectSection
          canExport={access.isAdmin}
          projectName={currentProject.name}
        />
      </section>
    </div>
  );
}
