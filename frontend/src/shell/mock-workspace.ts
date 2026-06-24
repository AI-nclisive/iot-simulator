export type ProjectSummary = {
  id: string;
  name: string;
  configuredSources: number;
  runningSources: number;
  reusableArtifacts: number;
  lastActivity: string;
};

export type ActiveRun = {
  id: string;
  label: string;
  initiator: string;
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
    label: "OPC UA replay / Line A telemetry",
    initiator: "Alex M.",
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
    label: "Modbus source / Packaging cell stream",
    initiator: "Automation",
    startedAt: "14:27",
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
    label: "Record session / Field capture telemetry",
    initiator: "Jordan K.",
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
];

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
