/**
 * mock-scenarios.ts — in-memory scenario fixtures for the Scenarios landing
 * page (UI-060). The backend scenarios API is later-wave work; until then the
 * landing surface runs on this mock, mirroring the data-sources mock approach.
 */

export type ScenarioRunState = "Not running" | "Running" | "Stopped" | "Failed";

export interface ScenarioLastRun {
  /** ISO timestamp of the last run, or null if never run. */
  at: string | null;
  outcome: "Completed" | "Stopped" | "Failed" | null;
}

export interface ScenarioRow {
  id: string;
  name: string;
  description: string;
  /** Number of steps configured in the builder. */
  stepCount: number;
  runState: ScenarioRunState;
  lastRun: ScenarioLastRun;
  /** Owner (creator) display name. */
  owner: string;
  /** Display name of whoever currently holds the edit lock, or null. */
  lockedBy: string | null;
  updatedAt: string;
}

export const scenarioRows: ScenarioRow[] = [
  {
    id: "scn-01",
    name: "Morning ramp-up",
    description: "Starts Line A, replays the calibration recording, then holds.",
    stepCount: 6,
    runState: "Not running",
    lastRun: { at: "2026-06-29T07:14:00Z", outcome: "Completed" },
    owner: "Olena Ohii",
    lockedBy: null,
    updatedAt: "2026-06-29T07:20:00Z",
  },
  {
    id: "scn-02",
    name: "Packaging fault drill",
    description: "Injects a quality fault mid-replay to exercise alerting.",
    stepCount: 9,
    runState: "Running",
    lastRun: { at: "2026-06-30T09:02:00Z", outcome: null },
    owner: "Anna Kosol",
    lockedBy: "Anna Kosol",
    updatedAt: "2026-06-30T09:02:00Z",
  },
  {
    id: "scn-03",
    name: "Overnight soak",
    description: "Long synthetic generation run across all field sources.",
    stepCount: 4,
    runState: "Failed",
    lastRun: { at: "2026-06-28T23:40:00Z", outcome: "Failed" },
    owner: "Olena Ohii",
    lockedBy: null,
    updatedAt: "2026-06-29T00:05:00Z",
  },
  {
    id: "scn-04",
    name: "Empty template",
    description: "Starting point for a new packaging-line scenario.",
    stepCount: 0,
    runState: "Not running",
    lastRun: { at: null, outcome: null },
    owner: "Nikolai Pak",
    lockedBy: null,
    updatedAt: "2026-06-27T15:30:00Z",
  },
];

// Seed steps per scenario id for the builder shell (UI-061). Step config is left
// open/partial here — the typed field editors are UI-062/063. `configured`
// reflects whether required config is present so validation has something real
// to report.
export const stepsByScenario: Record<string, import("./scenario-steps").ScenarioStep[]> = {
  "scn-01": [
    { id: "st-1", type: "start", label: "Line A telemetry", config: { sourceId: "src-01" }, configured: true },
    { id: "st-2", type: "replay", label: "Calibration recording", config: { recordingId: "rec-12" }, configured: true },
    { id: "st-3", type: "wait", label: "Hold 30s", config: { seconds: 30 }, configured: true },
  ],
  "scn-02": [
    { id: "st-1", type: "start", label: "Packaging cell stream", config: { sourceId: "src-02" }, configured: true },
    { id: "st-2", type: "fault", label: "Quality fault", config: {}, configured: false },
  ],
  "scn-03": [
    { id: "st-1", type: "synthetic", label: "Field sources", config: { pattern: "ramp" }, configured: true },
  ],
  "scn-04": [],
};
