export type ProjectSummary = {
  id: string;
  name: string;
  configuredSources: number;
  runningSources: number;
  reusableArtifacts: number;
  lastActivity: string;
};

export type RunSource = "manual" | "automation";
export type RunState = "queued" | "running" | "failed" | "completed" | "stopped";

export type ActiveRun = {
  id: string;
  label: string;
  initiator: string;
  runSource: RunSource;
  runState: RunState;
  startedAt: string;
  processType?: "Recording" | "Replay" | "Scenario";
  parameterCount: number;
  previewParameters: string[];
  previewOverflowCount: number;
  relatedLabel: string;
  relatedPath: string;
  evidence: "Ready" | "Assembling" | "Retry needed";
  evidencePath?: string;
};

export const projects: ProjectSummary[] = [
  {
    id: "assembly-line-a",
    name: "Assembly Line A",
    configuredSources: 12,
    runningSources: 3,
    reusableArtifacts: 28,
    lastActivity: "Replay resumed 4 min ago",
  },
  {
    id: "packaging-regression",
    name: "Packaging Regression",
    configuredSources: 8,
    runningSources: 2,
    reusableArtifacts: 16,
    lastActivity: "Capture closed 9 min ago",
  },
  {
    id: "field-replay-lab",
    name: "Field Replay Lab",
    configuredSources: 5,
    runningSources: 1,
    reusableArtifacts: 11,
    lastActivity: "Evidence retry pending",
  },
];

export const activeRuns: ActiveRun[] = [
  {
    id: "run-1",
    label: "Batch replay",
    initiator: "Alex M.",
    runSource: "manual",
    runState: "running",
    startedAt: "14:31",
    processType: "Replay",
    parameterCount: 2480,
    previewParameters: [
      "oven.zone[1].temp",
      "line.speed",
      "alarm.state",
    ],
    previewOverflowCount: 2477,
    relatedLabel: "Line A telemetry",
    relatedPath: "/data-sources/src-01",
    evidence: "Assembling",
    evidencePath: "/evidence",
  },
  {
    id: "run-2",
    label: "Nightly regression replay",
    initiator: "CI pipeline",
    runSource: "automation",
    runState: "running",
    startedAt: "14:27",
    processType: "Replay",
    parameterCount: 640,
    previewParameters: [
      "register[120].state",
      "line.counter",
      "jam.warning",
    ],
    previewOverflowCount: 637,
    relatedLabel: "Packaging cell stream",
    relatedPath: "/data-sources/src-02",
    evidence: "Ready",
    evidencePath: "/evidence",
  },
  {
    id: "run-3",
    label: "Live capture",
    initiator: "Jordan K.",
    runSource: "manual",
    runState: "running",
    startedAt: "14:14",
    processType: "Recording",
    parameterCount: 3120,
    previewParameters: [
      "pump[2].rpm",
      "batch.temp.avg",
      "quality.flag",
    ],
    previewOverflowCount: 3117,
    relatedLabel: "Field capture telemetry",
    relatedPath: "/data-sources/src-03",
    evidence: "Retry needed",
    evidencePath: "/evidence",
  },
  {
    id: "run-4",
    label: "Smoke test replay",
    initiator: "Scheduled job",
    runSource: "automation",
    runState: "queued",
    startedAt: "14:45",
    processType: "Replay",
    parameterCount: 2480,
    previewParameters: ["oven.zone[1].temp", "line.speed"],
    previewOverflowCount: 2478,
    relatedLabel: "Line A telemetry",
    relatedPath: "/data-sources/src-01",
    evidence: "Assembling",
  },
  {
    id: "run-5",
    label: "Validation replay",
    initiator: "CI pipeline",
    runSource: "automation",
    runState: "failed",
    startedAt: "13:55",
    processType: "Replay",
    parameterCount: 640,
    previewParameters: ["register[120].state"],
    previewOverflowCount: 639,
    relatedLabel: "Packaging cell stream",
    relatedPath: "/data-sources/src-02",
    evidence: "Retry needed",
  },
];

export const mockExportShouldFail = false;

export const sourceListStale = false;

export const sourceListError = false;

export const mockSourceLock: "unlocked" | "locked-by-self" | "locked-by-other" | "stale" =
  "unlocked";

export const topLevelNav = [
  { to: "/overview", label: "Overview" },
  { to: "/data-sources", label: "Data Sources" },
  { to: "/recordings", label: "Recordings & Samples" },
  { to: "/scenarios", label: "Scenarios" },
  { to: "/evidence", label: "Evidence" },
  { to: "/activity", label: "Activity" },
  { to: "/settings", label: "Settings" },
  { to: "/admin", label: "Admin" },
];
