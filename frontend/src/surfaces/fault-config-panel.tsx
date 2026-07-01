/**
 * fault-config-panel.tsx — fault step configuration (UI-063).
 *
 * Rendered by the step editor when a step is a fault. Beyond the target + kind
 * that the generic editor sets, this panel makes the fault understandable
 * before it is added: kind-specific parameters, timing (start delay + duration),
 * and a plain-language description of the resulting behavior.
 */

import {
  FAULT_PARAM_SPECS,
  describeFault,
  type FaultKind,
} from "./scenario-faults";

interface FaultConfigPanelProps {
  config: Record<string, unknown>;
  canEdit: boolean;
  onChange: (key: string, value: unknown) => void;
}

export function FaultConfigPanel({ config, canEdit, onChange }: FaultConfigPanelProps) {
  const kind = config.kind as FaultKind | undefined;

  // Kind is chosen via the generic editor's select; until then there is nothing
  // kind-specific to configure.
  if (!kind) {
    return (
      <p className="text-sm text-shell-muted">
        Choose a fault kind above to configure its parameters and timing.
      </p>
    );
  }

  const paramSpecs = FAULT_PARAM_SPECS[kind];

  function numberValue(key: string): number | "" {
    const v = config[key];
    return typeof v === "number" ? v : "";
  }

  return (
    <div className="space-y-4 rounded-md border border-shell-line bg-shell-base/40 px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-shell-muted">
        Fault parameters
      </p>

      {/* Kind-specific parameters */}
      {paramSpecs.map((param) => {
        const fieldId = `fault-${param.key}`;
        const value = config[param.key];
        const showError = param.required && !isFilled(value);
        return (
          <div key={param.key}>
            <label
              htmlFor={fieldId}
              className="mb-1 block text-xs uppercase tracking-wide text-shell-muted"
            >
              {param.label}
              {param.unit ? ` (${param.unit})` : ""}
              {param.required ? <span className="text-shell-danger"> *</span> : null}
            </label>
            {param.kind === "number" ? (
              <input
                id={fieldId}
                className="shell-field w-full"
                type="number"
                min={0}
                disabled={!canEdit}
                value={numberValue(param.key)}
                onChange={(e) =>
                  onChange(
                    param.key,
                    e.target.value === "" ? undefined : Math.max(0, Number(e.target.value)),
                  )
                }
              />
            ) : (
              <select
                id={fieldId}
                className="shell-field w-full"
                disabled={!canEdit}
                value={(value as string) ?? ""}
                onChange={(e) => onChange(param.key, e.target.value || undefined)}
              >
                <option value="">Select…</option>
                {(param.options ?? []).map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            )}
            {param.hint ? <p className="mt-1 text-xs text-shell-muted">{param.hint}</p> : null}
            {showError ? (
              <p className="mt-1 text-xs text-shell-warning">This field is required.</p>
            ) : null}
          </div>
        );
      })}

      {/* Shared timing */}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label
            htmlFor="fault-startAfterSeconds"
            className="mb-1 block text-xs uppercase tracking-wide text-shell-muted"
          >
            Start after (s)
          </label>
          <input
            id="fault-startAfterSeconds"
            className="shell-field w-full"
            type="number"
            min={0}
            disabled={!canEdit}
            value={numberValue("startAfterSeconds")}
            onChange={(e) =>
              onChange(
                "startAfterSeconds",
                e.target.value === "" ? undefined : Math.max(0, Number(e.target.value)),
              )
            }
          />
        </div>
        <div>
          <label
            htmlFor="fault-durationSeconds"
            className="mb-1 block text-xs uppercase tracking-wide text-shell-muted"
          >
            Duration (s)
          </label>
          <input
            id="fault-durationSeconds"
            className="shell-field w-full"
            type="number"
            min={0}
            disabled={!canEdit}
            value={numberValue("durationSeconds")}
            onChange={(e) =>
              onChange(
                "durationSeconds",
                e.target.value === "" ? undefined : Math.max(0, Number(e.target.value)),
              )
            }
          />
          <p className="mt-1 text-xs text-shell-muted">Leave empty to run until cleared.</p>
        </div>
      </div>

      {/* Plain-language behavior */}
      <div className="rounded-md border border-shell-accent/25 bg-shell-accent/5 px-3 py-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-shell-muted">
          What this does
        </p>
        <p className="mt-1 text-sm text-shell-ink">{describeFault(config)}</p>
      </div>
    </div>
  );
}

function isFilled(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === "string") return v.trim().length > 0;
  if (typeof v === "number") return !Number.isNaN(v);
  return true;
}
