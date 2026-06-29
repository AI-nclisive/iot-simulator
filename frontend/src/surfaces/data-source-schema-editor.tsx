import { useEffect, useState } from "react";
import { EditLockBanner, EditLockState } from "../ui/edit-lock-banner";
import { StatusBadge } from "../ui/status-badge";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { mockSourceLock } from "../shell/mock-workspace";
import { getParametersForSource, SchemaParameter } from "./mock-schema-parameters";
import type { DataSourceRow } from "./mock-data-sources";

type EditBuffer = {
  description: string;
  unit: string;
};

function bufferFromParam(param: SchemaParameter): EditBuffer {
  return {
    description: param.description,
    unit: param.unit,
  };
}

export function detectDependencyWarnings(
  _param: SchemaParameter,
  buffer: EditBuffer,
  original: EditBuffer,
): string[] {
  const warnings: string[] = [];

  // Identifier rename — proxy: description changed
  if (buffer.description !== original.description) {
    warnings.push(
      "Renaming this parameter may break existing recordings, replay configurations, or scenarios that reference it by its current identifier.",
    );
  }

  return warnings;
}

export function DataSourceSchemaEditor({
  source,
  onUnsavedChanges,
}: {
  source: DataSourceRow;
  onUnsavedChanges?: (has: boolean) => void;
}) {
  const [selectedParam, setSelectedParam] = useState<SchemaParameter | null>(null);
  const [originalBuffer, setOriginalBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [editBuffer, setEditBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
  });
  const [saving, setSaving] = useState(false);
  const [confirmSaveOpen, setConfirmSaveOpen] = useState(false);

  useEffect(() => {
    onUnsavedChanges?.(hasUnsavedChanges);
  }, [hasUnsavedChanges, onUnsavedChanges]);

  const lockState: EditLockState =
    mockSourceLock === "locked-by-other"
      ? { kind: "locked-by-other", owner: "Jordan K.", since: "14:31" }
      : mockSourceLock === "stale"
        ? { kind: "stale", owner: "Jordan K.", since: "14:31", onTake: () => {} }
        : mockSourceLock === "locked-by-self"
          ? { kind: "locked-by-self", since: "14:31", onRelease: () => {} }
          : { kind: "unlocked" };
  const isLockedByOther =
    lockState.kind === "locked-by-other" || lockState.kind === "stale";

  const allParams = getParametersForSource(source.id);
  const filteredParams = allParams.filter(
    (p) =>
      searchQuery === "" ||
      p.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.name.toLowerCase().includes(searchQuery.toLowerCase()),
  );

  const dependencyWarnings: string[] =
    hasUnsavedChanges && selectedParam
      ? detectDependencyWarnings(selectedParam, editBuffer, originalBuffer)
      : [];

  function handleSelectParam(param: SchemaParameter) {
    setSelectedParam(param);
    const buf = bufferFromParam(param);
    setEditBuffer(buf);
    setOriginalBuffer(buf);
    setHasUnsavedChanges(false);
  }

  function updateEditBuffer(patch: Partial<EditBuffer>) {
    setEditBuffer((curr) => ({ ...curr, ...patch }));
  }

  function executeSave() {
    setSaving(true);
    setTimeout(() => {
      setSaving(false);
      setHasUnsavedChanges(false);
      setOriginalBuffer(editBuffer);
    }, 800);
  }

  function handleSave() {
    if (dependencyWarnings.length > 0) {
      setConfirmSaveOpen(true);
    } else {
      executeSave();
    }
  }

  function handleConfirmSave() {
    setConfirmSaveOpen(false);
    executeSave();
  }

  function handleDiscard() {
    if (selectedParam) {
      setEditBuffer(originalBuffer);
    }
    setHasUnsavedChanges(false);
  }

  return (
    <div className="space-y-4">
      {lockState.kind !== "unlocked" ? <EditLockBanner lock={lockState} /> : null}

      {source.status === "Active" ? (
        <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-700">
            This source is currently active.
          </p>
          <p className="mt-1 text-sm leading-6 text-shell-muted">
            Schema changes will not affect the running session. Restart the source to
            apply edits.
          </p>
        </section>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        {hasUnsavedChanges ? (
          <StatusBadge label="Unsaved changes" tone="warning" />
        ) : null}
        <button
          className="shell-action"
          disabled={!hasUnsavedChanges || isLockedByOther || saving}
          type="button"
          onClick={handleSave}
        >
          Save schema
        </button>
        <button
          className="shell-action"
          disabled={!hasUnsavedChanges || isLockedByOther}
          type="button"
          onClick={handleDiscard}
        >
          Discard changes
        </button>
        {saving ? <StatusBadge label="Saving…" tone="accent" /> : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(18rem,1fr)]">
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <input
              className="shell-field min-w-0 flex-1"
              placeholder="Search parameters…"
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <span className="whitespace-nowrap text-sm text-shell-muted">
              {filteredParams.length} of {allParams.length}
            </span>
          </div>
          <div className="overflow-hidden rounded-md border border-shell-line bg-white max-h-[32rem] overflow-y-auto">
            {filteredParams.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-shell-muted">
                No parameters match the search.
              </p>
            ) : (
              <ul className="divide-y divide-shell-line">
                {filteredParams.map((param) => (
                  <li key={param.id}>
                    <button
                      className={`w-full px-4 py-3 text-left transition ${
                        selectedParam?.id === param.id
                          ? "bg-shell-accent/5"
                          : "hover:bg-shell-base/50"
                      }`}
                      type="button"
                      onClick={() => handleSelectParam(param)}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="min-w-0 flex-1 truncate font-mono text-sm text-shell-ink">
                          {param.path}
                        </span>
                        <span className="shrink-0 text-xs text-shell-muted">
                          {param.type.charAt(0).toUpperCase() + param.type.slice(1)}
                        </span>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {selectedParam ? (
          <div className="space-y-4">
            <section className="rounded-md border border-shell-line bg-white px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Parameter details
              </p>
              <p className="mt-2 font-mono text-sm text-shell-ink">
                {selectedParam.path}
              </p>
              <div className="mt-4 space-y-3">
                <label className="flex flex-col gap-2 text-sm text-shell-muted">
                  Description
                  <input
                    className="shell-field"
                    disabled={isLockedByOther}
                    type="text"
                    value={editBuffer.description}
                    onChange={(e) => {
                      updateEditBuffer({ description: e.target.value });
                      setHasUnsavedChanges(true);
                    }}
                  />
                </label>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="flex flex-col gap-2 text-sm text-shell-muted">
                    Unit
                    <input
                      className="shell-field"
                      disabled={isLockedByOther}
                      type="text"
                      value={editBuffer.unit}
                      onChange={(e) => {
                        updateEditBuffer({ unit: e.target.value });
                        setHasUnsavedChanges(true);
                      }}
                    />
                  </label>
                  <div className="flex flex-col gap-2 text-sm text-shell-muted">
                    Type
                    <div className="shell-field cursor-not-allowed bg-shell-base/30 text-shell-ink">
                      {selectedParam.type.charAt(0).toUpperCase() + selectedParam.type.slice(1)}
                    </div>
                  </div>
                </div>

                {dependencyWarnings.length > 0 ? (
                  <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-amber-700">
                      Dependency impact
                    </p>
                    <ul className="mt-2 space-y-2">
                      {dependencyWarnings.map((warning, i) => (
                        <li key={i} className="flex items-start gap-2 text-sm text-shell-muted">
                          <span className="shrink-0 text-amber-600" aria-hidden="true">⚠</span>
                          <span>{warning}</span>
                        </li>
                      ))}
                    </ul>
                  </section>
                ) : null}
              </div>
            </section>
          </div>
        ) : (
          <section className="rounded-md border border-shell-line bg-white px-4 py-6">
            <p className="text-center text-sm text-shell-muted">
              Select a parameter from the list to inspect or edit its details.
            </p>
          </section>
        )}
      </div>

      <p className="text-xs text-shell-muted">
        Schema changes take effect after the source is restarted. Type changes may
        break dependent parameters.
      </p>

      <ConfirmationDialog
        open={confirmSaveOpen}
        tone="warning"
        title="Save with dependency impact"
        message="This change may affect dependent artifacts. Confirm to save anyway."
        reversibilityLabel="Schema changes take effect after the source is restarted. They can be reverted by restoring previous values."
        confirmLabel="Confirm"
        cancelLabel="Cancel"
        onConfirm={handleConfirmSave}
        onClose={() => setConfirmSaveOpen(false)}
      />
    </div>
  );
}
