import { useState } from "react";

export type DeterministicMode = "off" | "seed" | "preset";
export type OrderingMode = "original" | "alphabetical";

export type DeterministicSettings = {
  mode: "seed" | "preset";
  seed?: string;
  preset?: string;
  ordering: OrderingMode;
  traceInEvidence: boolean;
};

export type DeterministicRunSettingsProps = {
  onChange: (settings: DeterministicSettings | null) => void;
};

const PRESET_OPTIONS = [
  { value: "stable-1", label: "Stable-1 (QA standard)" },
  { value: "stable-2", label: "Stable-2 (regression)" },
  { value: "ci-baseline", label: "CI baseline" },
] as const;

function validateSeed(seed: string): string | null {
  if (!seed.trim()) {
    return "Seed must be a number between 1 and 9,999,999.";
  }

  const numeric = Number(seed);

  if (!Number.isInteger(numeric) || isNaN(numeric) || !/^\d+$/.test(seed.trim())) {
    return "Seed must be a number between 1 and 9,999,999.";
  }

  if (numeric < 1 || numeric > 9_999_999) {
    return "Seed must be a number between 1 and 9,999,999.";
  }

  return null;
}

export function DeterministicRunSettings({ onChange }: DeterministicRunSettingsProps) {
  const [enabled, setEnabled] = useState(false);
  const [mode, setMode] = useState<"seed" | "preset">("seed");
  const [seed, setSeed] = useState("");
  const [preset, setPreset] = useState<string>(PRESET_OPTIONS[0].value);
  const [ordering, setOrdering] = useState<OrderingMode>("original");
  const [traceInEvidence, setTraceInEvidence] = useState(true);

  const seedError = mode === "seed" ? validateSeed(seed) : null;
  const incompatibleWarning =
    mode === "preset" && ordering === "alphabetical"
      ? "Named presets use original capture order. Switch to alphabetical ordering with a custom seed."
      : null;

  function handleToggle(checked: boolean) {
    setEnabled(checked);

    if (!checked) {
      onChange(null);
      return;
    }

    emitChange({ currentMode: mode, currentSeed: seed, currentPreset: preset, currentOrdering: ordering, currentTrace: traceInEvidence });
  }

  function emitChange(options: {
    currentMode: "seed" | "preset";
    currentSeed: string;
    currentPreset: string;
    currentOrdering: OrderingMode;
    currentTrace: boolean;
  }) {
    const { currentMode, currentSeed, currentPreset, currentOrdering, currentTrace } = options;

    if (currentMode === "seed") {
      const error = validateSeed(currentSeed);

      if (error) {
        onChange(null);
        return;
      }

      onChange({
        mode: "seed",
        seed: currentSeed.trim(),
        ordering: currentOrdering,
        traceInEvidence: currentTrace,
      });
    } else {
      onChange({
        mode: "preset",
        preset: currentPreset,
        ordering: currentOrdering,
        traceInEvidence: currentTrace,
      });
    }
  }

  function handleModeChange(newMode: "seed" | "preset") {
    setMode(newMode);

    if (!enabled) return;

    emitChange({ currentMode: newMode, currentSeed: seed, currentPreset: preset, currentOrdering: ordering, currentTrace: traceInEvidence });
  }

  function handleSeedChange(value: string) {
    setSeed(value);

    if (!enabled) return;

    emitChange({ currentMode: "seed", currentSeed: value, currentPreset: preset, currentOrdering: ordering, currentTrace: traceInEvidence });
  }

  function handlePresetChange(value: string) {
    setPreset(value);

    if (!enabled) return;

    emitChange({ currentMode: "preset", currentSeed: seed, currentPreset: value, currentOrdering: ordering, currentTrace: traceInEvidence });
  }

  function handleOrderingChange(value: OrderingMode) {
    setOrdering(value);

    if (!enabled) return;

    emitChange({ currentMode: mode, currentSeed: seed, currentPreset: preset, currentOrdering: value, currentTrace: traceInEvidence });
  }

  function handleTraceChange(checked: boolean) {
    setTraceInEvidence(checked);

    if (!enabled) return;

    emitChange({ currentMode: mode, currentSeed: seed, currentPreset: preset, currentOrdering: ordering, currentTrace: checked });
  }

  return (
    <div className="space-y-4">
      <label className="flex items-center gap-3 text-sm text-shell-ink">
        <input
          checked={enabled}
          type="checkbox"
          onChange={(event) => handleToggle(event.target.checked)}
        />
        <span className="font-medium">Deterministic replay</span>
      </label>

      {!enabled ? (
        <p className="text-sm leading-6 text-shell-muted">
          Replay uses live timing and original value order. Results may vary between runs.
        </p>
      ) : (
        <div className="space-y-5 rounded-md border border-shell-line bg-white px-4 py-4">
          {/* Mode */}
          <fieldset className="space-y-2">
            <legend className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Mode
            </legend>

            <div className="space-y-2 pt-1">
              <label className="flex items-center gap-2 text-sm text-shell-muted">
                <input
                  checked={mode === "seed"}
                  name="deterministic-mode"
                  type="radio"
                  value="seed"
                  onChange={() => handleModeChange("seed")}
                />
                Custom seed
              </label>

              {mode === "seed" ? (
                <div className="ml-5 space-y-1">
                  <input
                    aria-label="Seed value"
                    className="shell-field"
                    inputMode="numeric"
                    placeholder="e.g. 42"
                    type="text"
                    value={seed}
                    onChange={(event) => handleSeedChange(event.target.value)}
                  />

                  {seedError ? (
                    <p className="text-xs text-shell-danger" role="alert">
                      {seedError}
                    </p>
                  ) : null}
                </div>
              ) : null}

              <label className="flex items-center gap-2 text-sm text-shell-muted">
                <input
                  checked={mode === "preset"}
                  name="deterministic-mode"
                  type="radio"
                  value="preset"
                  onChange={() => handleModeChange("preset")}
                />
                Named preset
              </label>

              {mode === "preset" ? (
                <div className="ml-5">
                  <select
                    aria-label="Named preset"
                    className="shell-field"
                    value={preset}
                    onChange={(event) => handlePresetChange(event.target.value)}
                  >
                    {PRESET_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
              ) : null}
            </div>
          </fieldset>

          {/* Ordering */}
          <fieldset className="space-y-2">
            <legend className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Ordering mode
            </legend>

            <div className="space-y-2 pt-1">
              <label className="flex items-center gap-2 text-sm text-shell-muted">
                <input
                  checked={ordering === "original"}
                  name="deterministic-ordering"
                  type="radio"
                  value="original"
                  onChange={() => handleOrderingChange("original")}
                />
                Original capture order
              </label>

              <label className="flex items-center gap-2 text-sm text-shell-muted">
                <input
                  checked={ordering === "alphabetical"}
                  name="deterministic-ordering"
                  type="radio"
                  value="alphabetical"
                  onChange={() => handleOrderingChange("alphabetical")}
                />
                Alphabetical by parameter path
              </label>
            </div>
          </fieldset>

          {incompatibleWarning ? (
            <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-3">
              <p className="text-xs text-amber-700">{incompatibleWarning}</p>
            </div>
          ) : null}

          {/* Evidence traceability */}
          <div className="space-y-1">
            <label className="flex items-center gap-2 text-sm text-shell-muted">
              <input
                checked={traceInEvidence}
                type="checkbox"
                onChange={(event) => handleTraceChange(event.target.checked)}
              />
              Record seed and ordering in evidence export
            </label>

            <p className="ml-5 text-xs leading-5 text-shell-muted">
              When enabled, the seed and ordering configuration are included in evidence artifacts so results can be reproduced.
            </p>
          </div>

          {/* Repeatability scope notice */}
          <div className="rounded-md border border-shell-line bg-shell-base/55 px-3 py-3">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Repeatability scope
            </p>
            <p className="mt-2 text-xs leading-5 text-shell-muted">
              Repeatability applies to value sequencing and timing within the simulator. Client delivery order depends on network and connection state and is not guaranteed.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
