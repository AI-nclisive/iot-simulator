/**
 * synthetic-profile-step.tsx — the "Configure profile" step of the create-source wizard's
 * Synthetic basis (IS-145).
 *
 * Pick an existing source's schema, then assign a synthetic pattern to each of its
 * measurements. The measurement list + types come from the picked schema (reused verbatim
 * by the backend via `schemaFromSourceId`), so the synthetic twin mirrors a real device.
 * Emits an assembled `SyntheticConfig` + `schemaFromSourceId` + validity to the parent.
 *
 * The "Prefill from recording" button is a deliberate mock for now (fills placeholder
 * patterns, no backend call) — real statistics-derived profiles are a follow-up.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api";
import { useNotificationStore } from "../shell/notification-store";
import type { DataSourceRow } from "../shell/data-sources-store";
import type {
  SyntheticConfig,
  SyntheticPatternSpec,
  SyntheticVariableConfig,
} from "../shell/data-sources-store";

type SchemaNodeDto = {
  nodeId: string;
  path: string;
  name: string;
  kind: string;
  dataType: string | null;
  unit: string | null;
};
type SchemaResponse = { id: string; dataSourceId: string; version: number; nodes: SchemaNodeDto[] };

type PatternType = SyntheticPatternSpec["type"];

const PATTERN_TYPES: { value: PatternType; label: string }[] = [
  { value: "SINE", label: "Sine wave" },
  { value: "RANDOM_WALK", label: "Random walk" },
  { value: "RAMP", label: "Ramp" },
  { value: "SQUARE", label: "Square wave" },
  { value: "RANDOM_UNIFORM", label: "Random (uniform)" },
  { value: "CONSTANT", label: "Constant" },
];

type NodeDraft = {
  enabled: boolean;
  pattern: PatternType;
  min: string;
  max: string;
  periodMs: string;
  volatility: string;
  value: string;
  updateRateMs: string;
};

export type SyntheticProfileValue = {
  schemaFromSourceId: string | null;
  config: SyntheticConfig | null;
  valid: boolean;
  measurementCount: number;
};

function defaultDraft(): NodeDraft {
  return {
    enabled: true,
    pattern: "SINE",
    min: "0",
    max: "100",
    periodMs: "10000",
    volatility: "1",
    value: "0",
    updateRateMs: "1000",
  };
}

function num(s: string): number | null {
  const n = Number(s);
  return s.trim() === "" || Number.isNaN(n) ? null : n;
}

/** Build a serialized pattern from a draft, or null when the draft is incomplete/invalid. */
function toPattern(d: NodeDraft): SyntheticPatternSpec | null {
  switch (d.pattern) {
    case "CONSTANT": {
      const v = num(d.value);
      return v == null ? null : { type: "CONSTANT", value: v };
    }
    case "RANDOM_UNIFORM": {
      const mn = num(d.min);
      const mx = num(d.max);
      return mn == null || mx == null || mn > mx ? null : { type: "RANDOM_UNIFORM", min: mn, max: mx };
    }
    case "RANDOM_WALK": {
      const mn = num(d.min);
      const mx = num(d.max);
      const vol = num(d.volatility);
      return mn == null || mx == null || vol == null || mn > mx || vol < 0
        ? null
        : { type: "RANDOM_WALK", min: mn, max: mx, volatility: vol };
    }
    default: {
      // RAMP / SINE / SQUARE — need min ≤ max and a positive period.
      const mn = num(d.min);
      const mx = num(d.max);
      const p = num(d.periodMs);
      return mn == null || mx == null || p == null || mn > mx || p <= 0
        ? null
        : { type: d.pattern, min: mn, max: mx, periodMs: p };
    }
  }
}

export function SyntheticProfileStep({
  projectId,
  sources,
  seed,
  onSeedChange,
  onChange,
}: {
  projectId: string;
  sources: DataSourceRow[];
  seed: string;
  onSeedChange: (seed: string) => void;
  onChange: (value: SyntheticProfileValue) => void;
}) {
  const push = useNotificationStore((s) => s.push);
  const [sourceId, setSourceId] = useState<string>("");
  const [nodes, setNodes] = useState<SchemaNodeDto[]>([]);
  const [drafts, setDrafts] = useState<Record<string, NodeDraft>>({});
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const variableNodes = useMemo(() => nodes.filter((n) => n.kind === "VARIABLE"), [nodes]);

  async function pickSource(id: string) {
    setSourceId(id);
    setNodes([]);
    setDrafts({});
    setLoadError(null);
    if (!id) return;
    setLoading(true);
    try {
      const schema = await apiFetch<SchemaResponse>(
        `/api/v1/projects/${projectId}/data-sources/${id}/schema`,
      );
      const vars = schema.nodes.filter((n) => n.kind === "VARIABLE");
      if (vars.length === 0) {
        setLoadError("This source's schema has no measurements to drive.");
      }
      setNodes(schema.nodes);
      setDrafts(Object.fromEntries(vars.map((n) => [n.nodeId, defaultDraft()])));
    } catch {
      setLoadError("This source has no schema yet. Pick a scanned, imported, or edited source.");
    } finally {
      setLoading(false);
    }
  }

  function patchDraft(nodeId: string, patch: Partial<NodeDraft>) {
    setDrafts((cur) => ({ ...cur, [nodeId]: { ...cur[nodeId], ...patch } }));
  }

  // Mock: fill every measurement with a default sine pattern. No backend call (real
  // statistics-derived profiles are a follow-up); we just reset the drafts and tell the user.
  function prefillFromRecordingMock() {
    setDrafts(Object.fromEntries(variableNodes.map((n) => [n.nodeId, defaultDraft()])));
    push({
      tone: "success",
      title: "Prefilled with placeholder patterns (mock).",
      message: "Deriving a profile from a recording's statistics is coming soon.",
    });
  }

  // Assemble the config and report validity up whenever anything changes.
  const emit = useCallback(() => {
    const variables: SyntheticVariableConfig[] = [];
    let valid = variableNodes.length > 0;
    let anyEnabled = false;
    for (const node of variableNodes) {
      const d = drafts[node.nodeId];
      if (!d || !d.enabled) continue;
      anyEnabled = true;
      const pattern = toPattern(d);
      const rate = num(d.updateRateMs);
      if (pattern == null || rate == null || rate <= 0) {
        valid = false;
        continue;
      }
      variables.push({
        nodeId: node.nodeId,
        dataType: node.dataType ?? "FLOAT64",
        pattern,
        updateRateMs: rate,
      });
    }
    valid = valid && anyEnabled && !!sourceId;
    const seedNum = num(seed);
    onChange({
      schemaFromSourceId: sourceId || null,
      config: sourceId ? { seed: seedNum, variables } : null,
      valid,
      measurementCount: variables.length,
    });
  }, [drafts, variableNodes, sourceId, seed, onChange]);

  useEffect(() => {
    emit();
  }, [emit]);

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Reuse schema from source
          <select
            className="shell-field"
            value={sourceId}
            onChange={(e) => void pickSource(e.target.value)}
          >
            <option value="">Select a source…</option>
            {sources.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <span className="text-xs text-shell-muted">
            The synthetic device copies this source's measurements, names, and units.
          </span>
        </label>
        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Seed (optional)
          <input
            className="shell-field"
            inputMode="numeric"
            placeholder="Random each run"
            type="number"
            value={seed}
            onChange={(e) => onSeedChange(e.target.value)}
          />
          <span className="text-xs text-shell-muted">
            Fix the seed for a reproducible value sequence.
          </span>
        </label>
      </div>

      {loading ? <p className="text-sm text-shell-muted">Loading schema…</p> : null}
      {loadError ? <p className="text-sm text-shell-danger">{loadError}</p> : null}

      {variableNodes.length > 0 ? (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-medium text-shell-ink">
              {variableNodes.length} measurement{variableNodes.length === 1 ? "" : "s"}
            </p>
            <button className="shell-text-action" type="button" onClick={prefillFromRecordingMock}>
              Prefill from recording (mock)
            </button>
          </div>

          <ul className="space-y-2">
            {variableNodes.map((node) => {
              const d = drafts[node.nodeId] ?? defaultDraft();
              const showRange = d.pattern !== "CONSTANT";
              const showPeriod = d.pattern === "SINE" || d.pattern === "RAMP" || d.pattern === "SQUARE";
              return (
                <li
                  key={node.nodeId}
                  className="rounded-md border border-shell-line bg-white px-4 py-3"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="flex items-center gap-2 text-sm font-medium text-shell-ink">
                      <input
                        checked={d.enabled}
                        type="checkbox"
                        onChange={(e) => patchDraft(node.nodeId, { enabled: e.target.checked })}
                      />
                      {node.name}
                      <span className="text-xs font-normal text-shell-muted">
                        {node.path} · {node.dataType ?? "—"}
                        {node.unit ? ` · ${node.unit}` : ""}
                      </span>
                    </label>
                  </div>

                  {d.enabled ? (
                    <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                        Pattern
                        <select
                          className="shell-field"
                          value={d.pattern}
                          onChange={(e) =>
                            patchDraft(node.nodeId, { pattern: e.target.value as PatternType })
                          }
                        >
                          {PATTERN_TYPES.map((p) => (
                            <option key={p.value} value={p.value}>
                              {p.label}
                            </option>
                          ))}
                        </select>
                      </label>

                      {d.pattern === "CONSTANT" ? (
                        <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                          Value
                          <input
                            className="shell-field"
                            type="number"
                            value={d.value}
                            onChange={(e) => patchDraft(node.nodeId, { value: e.target.value })}
                          />
                        </label>
                      ) : null}

                      {showRange ? (
                        <>
                          <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                            Min
                            <input
                              className="shell-field"
                              type="number"
                              value={d.min}
                              onChange={(e) => patchDraft(node.nodeId, { min: e.target.value })}
                            />
                          </label>
                          <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                            Max
                            <input
                              className="shell-field"
                              type="number"
                              value={d.max}
                              onChange={(e) => patchDraft(node.nodeId, { max: e.target.value })}
                            />
                          </label>
                        </>
                      ) : null}

                      {showPeriod ? (
                        <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                          Period (ms)
                          <input
                            className="shell-field"
                            type="number"
                            value={d.periodMs}
                            onChange={(e) => patchDraft(node.nodeId, { periodMs: e.target.value })}
                          />
                        </label>
                      ) : null}

                      {d.pattern === "RANDOM_WALK" ? (
                        <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                          Volatility
                          <input
                            className="shell-field"
                            type="number"
                            value={d.volatility}
                            onChange={(e) => patchDraft(node.nodeId, { volatility: e.target.value })}
                          />
                        </label>
                      ) : null}

                      <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                        Update rate (ms)
                        <input
                          className="shell-field"
                          type="number"
                          value={d.updateRateMs}
                          onChange={(e) => patchDraft(node.nodeId, { updateRateMs: e.target.value })}
                        />
                      </label>
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
