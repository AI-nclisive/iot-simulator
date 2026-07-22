/**
 * synthetic-profile-step.tsx — the "Configure profile" step of the create-source wizard's
 * Synthetic basis (IS-145).
 *
 * Pick an existing source's schema, then assign a synthetic pattern to each of its
 * measurements. The measurement list + types come from the picked schema (reused verbatim
 * by the backend via `schemaFromSourceId`), so the synthetic twin mirrors a real device.
 * Emits an assembled `SyntheticConfig` + `schemaFromSourceId` + validity to the parent.
 *
 * "Prefill from recording" (IS-146) calls `POST /recordings/{id}/derive-synthetic` for the
 * chosen recording, applies each measurement's recommended stats-derived pattern (matched to
 * the picked schema by nodeId), and remembers every pattern type's suggestion so switching a
 * measurement's pattern re-applies that type's ranges.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api";
import { useManualSchemasStore } from "../shell/manual-schemas-store";
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

/**
 * UI-483: the Pattern select shows only these 3 plain-language options by default —
 * the signal-processing names (Sine, Random walk vs Random uniform, ...) weren't
 * meaningful to a non-technical user. "Show more patterns" reveals ADVANCED_PATTERN_TYPES.
 */
const SIMPLE_PATTERN_TYPES: { value: PatternType; label: string }[] = [
  { value: "CONSTANT", label: "Fixed value" },
  { value: "SINE", label: "Smooth (rises & falls)" },
  { value: "RANDOM_UNIFORM", label: "Random" },
];

const ADVANCED_PATTERN_TYPES: { value: PatternType; label: string }[] = [
  { value: "RAMP", label: "Rising ramp (resets)" },
  { value: "SQUARE", label: "Alternating (on/off)" },
  { value: "RANDOM_WALK", label: "Random (drifting)" },
];

const PATTERN_TYPES = [...SIMPLE_PATTERN_TYPES, ...ADVANCED_PATTERN_TYPES];

/**
 * IS-168: structural/identifier types have no natural dynamic-signal semantics —
 * a real device wouldn't vary a NodeId reference or a GUID over time either,
 * they're structural OPC UA plumbing, not measured values. The backend only
 * accepts a CONSTANT for these; the wizard locks the pattern choice to match
 * instead of letting the user pick a pattern that will always be rejected.
 */
const CONSTANT_ONLY_TYPES = new Set([
  "GUID",
  "STATUS_CODE",
  "QUALIFIED_NAME",
  "NODE_ID",
  "EXPANDED_NODE_ID",
  "XML_ELEMENT",
  "BYTES",
  "DATETIME",
]);

/** Whether this data type's CONSTANT value is free-text (not a plain number). */
function isTextConstantType(dataType: string | null): boolean {
  return (
    dataType === "GUID" ||
    dataType === "QUALIFIED_NAME" ||
    dataType === "NODE_ID" ||
    dataType === "EXPANDED_NODE_ID" ||
    dataType === "XML_ELEMENT"
  );
}

/** A type-appropriate starting value for a CONSTANT-only node — not seeded from a
 * real device's captured value (that plumbing doesn't exist in the schema-reuse
 * path today), just something valid to start editing from. */
function defaultConstantValue(dataType: string | null): string {
  switch (dataType) {
    case "GUID":
      return typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : "00000000-0000-0000-0000-000000000000";
    case "NODE_ID":
    case "EXPANDED_NODE_ID":
      return "ns=0;i=0";
    case "QUALIFIED_NAME":
    case "XML_ELEMENT":
      return "";
    case "STATUS_CODE":
      return "0";
    case "DATETIME":
      return String(Date.now());
    case "BYTES":
      return "";
    default:
      return "0";
  }
}

export type NodeDraft = {
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
  manualSchemaId: string | null;
  config: SyntheticConfig | null;
  valid: boolean;
  measurementCount: number;
};

export function defaultDraft(dataType: string | null = null): NodeDraft {
  if (CONSTANT_ONLY_TYPES.has(dataType ?? "")) {
    return {
      enabled: true,
      pattern: "CONSTANT",
      min: "0",
      max: "100",
      periodMs: "10000",
      volatility: "1",
      value: defaultConstantValue(dataType),
      updateRateMs: "1000",
    };
  }
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

/** Plain hex string ("0a1f", case-insensitive, no separators) → standard Base64, or null if invalid. */
function hexToBase64(hex: string): string | null {
  const trimmed = hex.trim();
  if (trimmed === "") return "";
  if (!/^[0-9a-fA-F]+$/.test(trimmed) || trimmed.length % 2 !== 0) return null;
  const bytes = new Uint8Array(trimmed.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = Number.parseInt(trimmed.slice(i * 2, i * 2 + 2), 16);
  }
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

/** Inverse of `hexToBase64` — standard Base64 → plain lowercase hex, for displaying a
 * previously-derived/prefilled BYTES constant back in the (hex) Value input. */
function base64ToHex(b64: string): string {
  if (b64 === "") return "";
  const binary = atob(b64);
  let hex = "";
  for (let i = 0; i < binary.length; i++) {
    hex += binary.charCodeAt(i).toString(16).padStart(2, "0");
  }
  return hex;
}

/**
 * Build a serialized pattern from a draft, or null when the draft is incomplete/invalid.
 * `dataType` decides how a CONSTANT's value is shaped on the wire (IS-168): a plain
 * number for ordinary measurement types, `stringValue` for identifier/text
 * structural types, `bytesValueBase64` for BYTES.
 */
export function toPattern(d: NodeDraft, dataType: string | null = null): SyntheticPatternSpec | null {
  switch (d.pattern) {
    case "CONSTANT": {
      if (isTextConstantType(dataType)) {
        const trimmed = d.value.trim();
        return trimmed === "" && dataType !== "QUALIFIED_NAME" && dataType !== "XML_ELEMENT"
          ? null
          : { type: "CONSTANT", stringValue: trimmed };
      }
      if (dataType === "BYTES") {
        const b64 = hexToBase64(d.value);
        return b64 == null ? null : { type: "CONSTANT", bytesValueBase64: b64 };
      }
      // STATUS_CODE / DATETIME / ordinary numeric measurement types.
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

/** Map a derived pattern (from POST /recordings/{id}/derive-synthetic) back onto editable draft fields. */
export function draftFromPattern(pattern: SyntheticPatternSpec, updateRateMs: number): Partial<NodeDraft> {
  const rate = { updateRateMs: String(updateRateMs) };
  switch (pattern.type) {
    case "CONSTANT": {
      const value =
        pattern.stringValue ??
        (pattern.bytesValueBase64 != null ? base64ToHex(pattern.bytesValueBase64) : null) ??
        String(pattern.value ?? 0);
      return { ...rate, pattern: "CONSTANT", value };
    }
    case "RANDOM_WALK":
      return {
        ...rate,
        pattern: "RANDOM_WALK",
        min: String(pattern.min ?? 0),
        max: String(pattern.max ?? 0),
        volatility: String(pattern.volatility ?? 1),
      };
    case "RANDOM_UNIFORM":
      return { ...rate, pattern: "RANDOM_UNIFORM", min: String(pattern.min ?? 0), max: String(pattern.max ?? 0) };
    default:
      // SINE / RAMP / SQUARE
      return {
        ...rate,
        pattern: pattern.type,
        min: String(pattern.min ?? 0),
        max: String(pattern.max ?? 0),
        periodMs: String(pattern.periodMs ?? 10000),
      };
  }
}

type RecordingOption = { id: string; name: string | null; valueCount: number };

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
  // A schema source is either an existing data source's schema (schemaFromSourceId) or a
  // standalone Manual Schema (manualSchemaId) — a schema is required to get any parameters
  // to drive, so exactly one of these two pickers is active at a time (IS-173/UI-491).
  const [schemaKind, setSchemaKind] = useState<"source" | "manual">("source");
  const [sourceId, setSourceId] = useState<string>("");
  const manualSchemas = useManualSchemasStore((s) => s.schemas);
  const [manualSchemaId, setManualSchemaId] = useState<string>("");
  const [nodes, setNodes] = useState<SchemaNodeDto[]>([]);
  const [drafts, setDrafts] = useState<Record<string, NodeDraft>>({});
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [recordings, setRecordings] = useState<RecordingOption[]>([]);
  const [selectedRecordingId, setSelectedRecordingId] = useState("");
  const [prefilling, setPrefilling] = useState(false);
  // Per-node pattern suggestions from the last recording profile, so switching a measurement's
  // pattern type re-applies that type's stats-derived params (IS-146).
  const [suggestions, setSuggestions] = useState<
    Record<string, { updateRateMs: number; byType: Record<string, SyntheticPatternSpec> }>
  >({});
  // UI-483: nodeIds whose Pattern select has been expanded to show the "advanced" options
  // (Ramp/Square/Random walk), via "Show more patterns" or because the loaded pattern is one of them.
  const [expandedPatternRows, setExpandedPatternRows] = useState<Set<string>>(new Set());
  // UI-484: nodeIds actually updated by the last successful prefill, so those rows can be
  // visually marked — a recording rarely covers every schema measurement, and without this
  // a user scrolling to an unmatched row could reasonably conclude prefill did nothing.
  const [prefilledNodeIds, setPrefilledNodeIds] = useState<Set<string>>(new Set());
  const rowRefs = useRef<Record<string, HTMLLIElement | null>>({});

  const variableNodes = useMemo(() => nodes.filter((n) => n.kind === "VARIABLE"), [nodes]);

  // Load recordings that carry captured values, for the "Prefill from recording" control.
  useEffect(() => {
    if (!projectId) return;
    let cancelled = false;
    void (async () => {
      try {
        const res = await apiFetch<{ items: RecordingOption[] }>(
          `/api/v1/projects/${projectId}/recordings`,
        );
        if (!cancelled) setRecordings((res.items ?? []).filter((r) => r.valueCount > 0));
      } catch {
        if (!cancelled) setRecordings([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  function applyNodes(schemaNodes: SchemaNodeDto[], emptyMessage: string) {
    const vars = schemaNodes.filter((n) => n.kind === "VARIABLE");
    if (vars.length === 0) {
      setLoadError(emptyMessage);
    }
    setNodes(schemaNodes);
    setDrafts(Object.fromEntries(vars.map((n) => [n.nodeId, defaultDraft(n.dataType)])));
  }

  async function pickSource(id: string) {
    setManualSchemaId("");
    setSourceId(id);
    setNodes([]);
    setDrafts({});
    setLoadError(null);
    setPrefilledNodeIds(new Set());
    if (!id) return;
    setLoading(true);
    try {
      const schema = await apiFetch<SchemaResponse>(
        `/api/v1/projects/${projectId}/data-sources/${id}/schema`,
      );
      applyNodes(schema.nodes, "This source's schema has no measurements to drive.");
    } catch {
      setLoadError("This source has no schema yet. Pick a scanned, imported, or edited source.");
    } finally {
      setLoading(false);
    }
  }

  function pickManualSchema(id: string) {
    setSourceId("");
    setManualSchemaId(id);
    setNodes([]);
    setDrafts({});
    setLoadError(null);
    setPrefilledNodeIds(new Set());
    if (!id) return;
    const schema = manualSchemas.find((s) => s.id === id);
    if (!schema) return;
    applyNodes(schema.nodes, "This manual schema has no variables to drive.");
  }

  function patchDraft(nodeId: string, patch: Partial<NodeDraft>) {
    setDrafts((cur) => ({ ...cur, [nodeId]: { ...cur[nodeId], ...patch } }));
  }

  // Changing a measurement's pattern re-applies that pattern's suggested params when we have a
  // recording profile for it, so the ranges stay stats-derived across pattern types.
  function changePattern(nodeId: string, type: PatternType) {
    const sug = suggestions[nodeId];
    if (sug && sug.byType[type]) {
      patchDraft(nodeId, { pattern: type, ...draftFromPattern(sug.byType[type], sug.updateRateMs) });
    } else {
      patchDraft(nodeId, { pattern: type });
    }
  }

  // Select-all/deselect-all: toggles every measurement's "enabled" checkbox at once,
  // instead of clicking through each row.
  function setAllEnabled(enabled: boolean) {
    setDrafts((cur) => {
      const next = { ...cur };
      for (const node of variableNodes) {
        next[node.nodeId] = { ...(next[node.nodeId] ?? defaultDraft(node.dataType)), enabled };
      }
      return next;
    });
  }

  // Bulk-apply a pattern to every currently-selected (enabled) row in one action, e.g.
  // "set Random for all". Constant-only types (IS-168) can't take another pattern, so they're
  // left untouched. Reuses changePattern's per-node suggestion re-apply logic per row.
  function setPatternForSelected(type: PatternType) {
    for (const node of variableNodes) {
      if (!drafts[node.nodeId]?.enabled) continue;
      if (CONSTANT_ONLY_TYPES.has(node.dataType ?? "")) continue;
      changePattern(node.nodeId, type);
    }
  }

  // Derive patterns from a recording's captured values (POST /recordings/{id}/derive-synthetic)
  // and map them onto the matching measurement rows by nodeId. Rows whose nodeId isn't in the
  // recording are left untouched (the recording must share this source's schema to match).
  async function prefillFromRecording() {
    if (!selectedRecordingId) return;
    setPrefilling(true);
    try {
      const profile = await apiFetch<{
        measurements: {
          nodeId: string;
          dataType: string;
          updateRateMs: number;
          recommended: string;
          suggestions: Record<string, SyntheticPatternSpec>;
        }[];
      }>(`/api/v1/projects/${projectId}/recordings/${selectedRecordingId}/derive-synthetic`, {
        method: "POST",
        body: JSON.stringify({}),
      });
      const matched = profile.measurements.filter((m) => drafts[m.nodeId]).length;
      // Remember every measurement's per-type suggestions so changePattern can reuse them.
      setSuggestions((prev) => {
        const next = { ...prev };
        for (const m of profile.measurements) {
          next[m.nodeId] = { updateRateMs: m.updateRateMs, byType: m.suggestions };
        }
        return next;
      });
      // Computed from the `drafts` closure (not the setDrafts updater's `cur`) so it's ready
      // to use immediately below — the updater callback only runs when React processes the
      // state update, which is too late to read synchronously here.
      const updatedNodeIds = profile.measurements
        .filter((m) => drafts[m.nodeId] && m.suggestions[m.recommended])
        .map((m) => m.nodeId);
      setDrafts((cur) => {
        const next = { ...cur };
        for (const m of profile.measurements) {
          if (!next[m.nodeId]) continue;
          const recommended = m.suggestions[m.recommended];
          if (!recommended) continue;
          next[m.nodeId] = {
            ...next[m.nodeId],
            enabled: true,
            ...draftFromPattern(recommended, m.updateRateMs),
          };
        }
        return next;
      });
      // UI-484: mark which rows actually changed and scroll the first one into view — the
      // recording may only cover a fraction of the schema's measurements, and a long list
      // otherwise leaves no way to tell prefill worked from an unmatched row elsewhere.
      setPrefilledNodeIds(new Set(updatedNodeIds));
      if (updatedNodeIds.length > 0) {
        rowRefs.current[updatedNodeIds[0]]?.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      push(
        matched > 0
          ? {
              tone: "success",
              title: `Prefilled ${matched} measurement${matched === 1 ? "" : "s"} from the recording.`,
              message: "Prefilled measurements are marked below. Switch a pattern to see that type's suggested ranges.",
            }
          : {
              tone: "warning",
              title: "No measurements matched this recording.",
              message: "Reuse the schema of the source this recording was captured from.",
            },
      );
    } catch {
      push({ tone: "error", title: "Could not derive a profile from that recording." });
    } finally {
      setPrefilling(false);
    }
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
      const pattern = toPattern(d, node.dataType);
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
    const hasSchema = !!sourceId || !!manualSchemaId;
    valid = valid && anyEnabled && hasSchema;
    const seedNum = num(seed);
    onChange({
      schemaFromSourceId: sourceId || null,
      manualSchemaId: manualSchemaId || null,
      config: hasSchema ? { seed: seedNum, variables } : null,
      valid,
      measurementCount: variables.length,
    });
  }, [drafts, variableNodes, sourceId, manualSchemaId, seed, onChange]);

  useEffect(() => {
    emit();
  }, [emit]);

  const enabledCount = variableNodes.filter((n) => drafts[n.nodeId]?.enabled).length;
  const allEnabled = variableNodes.length > 0 && enabledCount === variableNodes.length;
  const someEnabled = enabledCount > 0 && !allEnabled;

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-2 text-sm text-shell-muted">
          <span>Parameter source</span>
          <div className="flex gap-4 text-sm text-shell-ink">
            <label className="flex items-center gap-1.5">
              <input
                checked={schemaKind === "source"}
                name="schema-kind"
                type="radio"
                onChange={() => {
                  setSchemaKind("source");
                  pickManualSchema("");
                }}
              />
              Existing source
            </label>
            <label className="flex items-center gap-1.5">
              <input
                checked={schemaKind === "manual"}
                name="schema-kind"
                type="radio"
                onChange={() => {
                  setSchemaKind("manual");
                  void pickSource("");
                }}
              />
              Manual schema
            </label>
          </div>
          {schemaKind === "source" ? (
            <label className="flex flex-col gap-2">
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
          ) : (
            <label className="flex flex-col gap-2">
              Reuse a manual schema
              <select
                className="shell-field"
                value={manualSchemaId}
                onChange={(e) => pickManualSchema(e.target.value)}
              >
                <option value="">
                  {manualSchemas.length === 0 ? "No manual schemas in this project" : "Select a manual schema…"}
                </option>
                {manualSchemas.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
              <span className="text-xs text-shell-muted">
                The synthetic device copies this manual schema's structure, names, and units.
              </span>
            </label>
          )}
        </div>
        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Seed (optional)
          <input
            className="shell-field"
            inputMode="numeric"
            placeholder="Random each run"
            step="any"
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
            {schemaKind === "source" ? (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <select
                    aria-label="Prefill from recording"
                    className="shell-field"
                    value={selectedRecordingId}
                    onChange={(e) => setSelectedRecordingId(e.target.value)}
                    disabled={recordings.length === 0}
                  >
                    <option value="">
                      {recordings.length === 0 ? "No recordings with data" : "Prefill from recording…"}
                    </option>
                    {recordings.map((r) => (
                      <option key={r.id} value={r.id}>
                        {(r.name && r.name.trim()) || r.id.slice(0, 8)} · {r.valueCount} values
                      </option>
                    ))}
                  </select>
                  <button
                    className="shell-action"
                    type="button"
                    disabled={!selectedRecordingId || prefilling}
                    onClick={() => void prefillFromRecording()}
                  >
                    {prefilling ? "Prefilling…" : "Prefill"}
                  </button>
                </div>
                <p className="text-xs text-shell-muted">
                  Copies realistic ranges and a suggested pattern from a real recording into any
                  measurement it has captured data for — measurements the recording doesn't cover keep
                  their current settings.
                </p>
              </div>
            ) : null}
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm text-shell-ink">
              <input
                checked={allEnabled}
                ref={(el) => {
                  if (el) el.indeterminate = someEnabled;
                }}
                type="checkbox"
                onChange={(e) => setAllEnabled(e.target.checked)}
              />
              Select all
            </label>
            <select
              aria-label="Set pattern for selected"
              className="shell-field"
              disabled={enabledCount === 0}
              value=""
              onChange={(e) => {
                const type = e.target.value as PatternType;
                if (type) setPatternForSelected(type);
                e.target.value = "";
              }}
            >
              <option value="">Set pattern for selected…</option>
              {PATTERN_TYPES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>

          <ul className="space-y-2">
            {variableNodes.map((node) => {
              const d = drafts[node.nodeId] ?? defaultDraft(node.dataType);
              const constantOnly = CONSTANT_ONLY_TYPES.has(node.dataType ?? "");
              const showRange = d.pattern !== "CONSTANT";
              const showPeriod = d.pattern === "SINE" || d.pattern === "RAMP" || d.pattern === "SQUARE";
              const isBytes = node.dataType === "BYTES";
              const valueInputType = isTextConstantType(node.dataType) || isBytes ? "text" : "number";
              const isAdvancedPattern = ADVANCED_PATTERN_TYPES.some((p) => p.value === d.pattern);
              const showAdvancedPatterns = isAdvancedPattern || expandedPatternRows.has(node.nodeId);
              const visiblePatternTypes = showAdvancedPatterns ? PATTERN_TYPES : SIMPLE_PATTERN_TYPES;
              const wasPrefilled = prefilledNodeIds.has(node.nodeId);
              return (
                <li
                  key={node.nodeId}
                  ref={(el) => {
                    rowRefs.current[node.nodeId] = el;
                  }}
                  className={
                    "rounded-md border bg-white px-4 py-3 " +
                    (wasPrefilled ? "border-shell-accent" : "border-shell-line")
                  }
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
                      {wasPrefilled ? (
                        <span className="rounded-full bg-shell-accent/10 px-2 py-0.5 text-xs font-normal normal-case text-shell-accent">
                          Prefilled
                        </span>
                      ) : null}
                    </label>
                  </div>

                  {d.enabled ? (
                    <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                        Pattern
                        {constantOnly ? (
                          <select className="shell-field" value="CONSTANT" disabled>
                            <option value="CONSTANT">Constant (required for {node.dataType})</option>
                          </select>
                        ) : (
                          <>
                            <select
                              className="shell-field"
                              value={d.pattern}
                              onChange={(e) => changePattern(node.nodeId, e.target.value as PatternType)}
                            >
                              {visiblePatternTypes.map((p) => (
                                <option key={p.value} value={p.value}>
                                  {p.label}
                                </option>
                              ))}
                            </select>
                            {!showAdvancedPatterns ? (
                              <button
                                className="text-left text-xs font-normal normal-case text-shell-accent underline"
                                type="button"
                                onClick={() =>
                                  setExpandedPatternRows((prev) => new Set(prev).add(node.nodeId))
                                }
                              >
                                Show more patterns
                              </button>
                            ) : null}
                          </>
                        )}
                      </label>

                      {d.pattern === "CONSTANT" ? (
                        <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                          Value
                          <input
                            className="shell-field"
                            type={valueInputType}
                            placeholder={isBytes ? "Hex, e.g. 0a1f" : undefined}
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
                              step="any"
                              type="number"
                              value={d.min}
                              onChange={(e) => patchDraft(node.nodeId, { min: e.target.value })}
                            />
                          </label>
                          <label className="flex flex-col gap-1 text-xs uppercase tracking-wide text-shell-muted">
                            Max
                            <input
                              className="shell-field"
                              step="any"
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
                            step="any"
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
                            step="any"
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
                          step="any"
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
