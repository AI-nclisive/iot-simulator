import { useState } from "react";
import { EditLockBanner, EditLockState } from "../ui/edit-lock-banner";
import { StatusBadge } from "../ui/status-badge";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { mockSourceLock } from "../shell/mock-workspace";
import { getParametersForSource, SchemaParameter } from "./mock-schema-parameters";
import type { DataSourceRow } from "./mock-data-sources";

type EditBuffer = {
  description: string;
  unit: string;
  min: string;
  max: string;
};

function bufferFromParam(param: SchemaParameter): EditBuffer {
  return {
    description: param.description,
    unit: param.unit,
    min: param.min !== null ? String(param.min) : "",
    max: param.max !== null ? String(param.max) : "",
  };
}

export function detectDependencyWarnings(
  param: SchemaParameter,
  buffer: EditBuffer,
  original: EditBuffer,
): string[] {
  const warnings: string[] = [];

  // 1. Identifier rename — proxy: description changed
  if (buffer.description !== original.description) {
    warnings.push(
      "Renaming this parameter may break existing recordings, replay configurations, or scenarios that reference it by its current identifier.",
    );
  }

  // 2. Type change impact — unit changed on a referenced parameter (hasDependent)
  if (buffer.unit !== original.unit && param.hasDependent) {
    warnings.push(
      "Changing the unit of a parameter referenced by active recordings or scenarios may cause replay mismatches.",
    );
  }

  // 3. Range narrowing — new min higher than old, or new max lower than old
  const origMinNum = original.min !== "" ? Number(original.min) : null;
  const origMaxNum = original.max !== "" ? Number(original.max) : null;
  const newMinNum = buffer.min !== "" ? Number(buffer.min) : null;
  const newMaxNum = buffer.max !== "" ? Number(buffer.max) : null;

  const minNarrowed =
    origMinNum !== null && newMinNum !== null && newMinNum > origMinNum;
  const maxNarrowed =
    origMaxNum !== null && newMaxNum !== null && newMaxNum < origMaxNum;

  if (minNarrowed || maxNarrowed) {
    warnings.push(
      "Narrowing the value range may exclude values present in existing recordings.",
    );
  }

  return warnings;
}

export function DataSourceSchemaEditor({ source }: { source: DataSourceRow }) {
  const [selectedParam, setSelectedParam] = useState<SchemaParameter | null>(null);
  const [originalBuffer, setOriginalBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
    min: "",
    max: "",
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [editBuffer, setEditBuffer] = useState<EditBuffer>({
    description: "",
    unit: "",
    min: "",
    max: "",
  });
  const [saving, setSaving] = useState(false);
  const [confirmSaveOpen, setConfirmSaveOpen] = useState(false);

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

  const validationIssues: string[] = [];
  if (hasUnsavedChanges) {
    const minNum = Number(editBuffer.min);
    const maxNum = Number(editBuffer.max);
    if (editBuffer.min && isNaN(minNum)) {
      validationIssues.push("Min value must be a number.");
    }
    if (editBuffer.max && isNaN(maxNum)) {
      validationIssues.push("Max value must be a number.");
    }
    if (editBuffer.min && editBuffer.max && !isNaN(minNum) && !isNaN(maxNum) && minNum > maxNum) {
      validationIssues.push(
        "Min value is greater than max value for " + selectedParam?.name,
      );
    }
  }

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
          disabled={!hasUnsavedChanges || isLockedByOther || validationIssues.length > 0 || saving}
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

      {validationIssues.length > 0 ? (
        <section className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-700">
            {validationIssues.length} validation issue
            {validationIssues.length !== 1 ? "s" : ""}
          </p>
          <ul className="mt-2 space-y-1">
            {validationIssues.map((issue, i) => (
              <li key={i} className="text-sm text-shell-muted">
                {issue}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

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
          <div className="overflow-hidden rounded-md border border-shell-line bg-white">
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
                          {param.type}
                        </span>
                      </div>
                      {param.hasDependent ? (
                        <span className="mt-1 text-xs text-amber-600">
                          Has dependents
                        </span>
                      ) : null}
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
                      {selectedParam.type}
                    </div>
                  </div>
                </div>
                {selectedParam.type === "float" || selectedParam.type === "int" ? (
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Min value
                      <input
                        className="shell-field"
                        disabled={isLockedByOther}
                        type="number"
                        value={editBuffer.min}
                        onChange={(e) => {
                          updateEditBuffer({ min: e.target.value });
                          setHasUnsavedChanges(true);
                        }}
                      />
                    </label>
                    <label className="flex flex-col gap-2 text-sm text-shell-muted">
                      Max value
                      <input
                        className="shell-field"
                        disabled={isLockedByOther}
                        type="number"
                        value={editBuffer.max}
                        onChange={(e) => {
                          updateEditBuffer({ max: e.target.value });
                          setHasUnsavedChanges(true);
                        }}
                      />
                    </label>
                  </div>
                ) : null}
                {selectedParam.hasDependent ? (
                  <p className="text-xs text-amber-600">
                    This parameter has dependents. Renaming or retyping it may break
                    downstream references.
                  </p>
                ) : null}

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
