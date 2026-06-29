export type EvidenceStatus = "In progress" | "Ready" | "Incomplete" | "Exported" | "Export failed";

export type EvidenceFormat = "PDF" | "JSON" | "CSV bundle";

export type EvidenceTimelineEvent = {
  id: string;
  time: string;
  title: string;
  description: string;
  tone?: "neutral" | "warning" | "danger";
};

export type EvidenceClient = {
  id: string;
  name: string;
  protocol: "OPC UA" | "Modbus TCP";
  delivery: "Complete" | "Partial" | "Disconnected";
};

export type EvidenceIssue = {
  id: string;
  label: string;
  description: string;
  severity: "Warning" | "Error";
};

export type EvidenceArtifact = {
  id: string;
  title: string;
  projectName: string;
  sourceId?: string;
  sourceName: string;
  sourcePath?: string;
  scenarioName?: string;
  runId: string;
  runType: "Recording" | "Replay" | "Scenario";
  initiator: string;
  startedAt: string;
  completedAt?: string;
  duration: string;
  status: EvidenceStatus;
  exportState: "Not exported" | "Exported" | "Export failed" | "Not ready";
  completeness: string;
  valueCount: number;
  clientCount: number;
  sizeLabel: string;
  formats: EvidenceFormat[];
  lastExportedAt?: string;
  exportFailureReason?: string;
  exportNextAction?: string;
  timeline: EvidenceTimelineEvent[];
  clients: EvidenceClient[];
  issues: EvidenceIssue[];
};

export const evidenceArtifacts: EvidenceArtifact[] = [
  {
    id: "ev-001",
    title: "Line A replay verification",
    projectName: "Assembly Line A",
    sourceId: "src-01",
    sourceName: "Line A telemetry",
    sourcePath: "/data-sources/src-01",
    runId: "run-1",
    runType: "Replay",
    initiator: "Alex M.",
    startedAt: "Jun 25, 2026 14:31",
    completedAt: "Jun 25, 2026 14:36",
    duration: "05:14",
    status: "Ready",
    exportState: "Not exported",
    completeness: "Complete replay output with client delivery summary.",
    valueCount: 12480,
    clientCount: 2,
    sizeLabel: "18.4 MB",
    formats: ["PDF", "JSON", "CSV bundle"],
    timeline: [
      {
        id: "ev-001-t1",
        time: "14:31:00",
        title: "Replay started",
        description: "Line A baseline replay was assigned to the source.",
      },
      {
        id: "ev-001-t2",
        time: "14:32:18",
        title: "Clients subscribed",
        description: "Two clients received live values from the replay output.",
      },
      {
        id: "ev-001-t3",
        time: "14:36:14",
        title: "Evidence ready",
        description: "Replay completed and all expected evidence sections assembled.",
      },
    ],
    clients: [
      {
        id: "ev-001-c1",
        name: "QA validation client",
        protocol: "OPC UA",
        delivery: "Complete",
      },
      {
        id: "ev-001-c2",
        name: "Line dashboard",
        protocol: "OPC UA",
        delivery: "Complete",
      },
    ],
    issues: [],
  },
  {
    id: "ev-002",
    title: "Packaging regression sample replay",
    projectName: "Packaging Regression",
    sourceId: "src-02",
    sourceName: "Packaging cell stream",
    sourcePath: "/data-sources/src-02",
    runId: "run-2",
    runType: "Replay",
    initiator: "Automation",
    startedAt: "Jun 25, 2026 14:27",
    completedAt: "Jun 25, 2026 14:28",
    duration: "01:02",
    status: "Exported",
    exportState: "Exported",
    completeness: "Complete artifact already exported for regression review.",
    valueCount: 3840,
    clientCount: 1,
    sizeLabel: "6.2 MB",
    formats: ["PDF", "JSON"],
    lastExportedAt: "Jun 25, 2026 14:29",
    timeline: [
      {
        id: "ev-002-t1",
        time: "14:27:00",
        title: "Replay started",
        description: "Prepared sample was replayed against the packaging source.",
      },
      {
        id: "ev-002-t2",
        time: "14:28:02",
        title: "Run completed",
        description: "Replay completed without data gaps.",
      },
      {
        id: "ev-002-t3",
        time: "14:29:11",
        title: "Export completed",
        description: "PDF and JSON evidence were exported without secrets.",
      },
    ],
    clients: [
      {
        id: "ev-002-c1",
        name: "Regression harness",
        protocol: "Modbus TCP",
        delivery: "Complete",
      },
    ],
    issues: [],
  },
  {
    id: "ev-003",
    title: "Field capture partial take",
    projectName: "Field Replay Lab",
    sourceId: "src-03",
    sourceName: "Field capture telemetry",
    sourcePath: "/data-sources/src-03",
    runId: "run-3",
    runType: "Recording",
    initiator: "Jordan K.",
    startedAt: "Jun 25, 2026 14:14",
    completedAt: "Jun 25, 2026 14:16",
    duration: "01:37",
    status: "Export failed",
    exportState: "Export failed",
    completeness: "Partial recording. Values are usable, but client delivery is incomplete.",
    valueCount: 3120,
    clientCount: 0,
    sizeLabel: "9.8 MB",
    formats: ["PDF", "JSON", "CSV bundle"],
    exportFailureReason: "CSV bundle failed because the partial client section is missing.",
    exportNextAction: "Retry export after excluding unavailable client delivery details.",
    timeline: [
      {
        id: "ev-003-t1",
        time: "14:14:00",
        title: "Recording started",
        description: "Field source recording began from live OPC UA values.",
      },
      {
        id: "ev-003-t2",
        time: "14:15:22",
        title: "Source warning",
        description: "Slow responses were detected while recording continued.",
        tone: "warning",
      },
      {
        id: "ev-003-t3",
        time: "14:16:37",
        title: "Export failed",
        description: "Evidence summary is available, but one export section needs retry.",
        tone: "danger",
      },
    ],
    clients: [],
    issues: [
      {
        id: "ev-003-i1",
        label: "Partial client delivery",
        description: "No connected clients were present when the recording ended.",
        severity: "Warning",
      },
      {
        id: "ev-003-i2",
        label: "Export section missing",
        description: "CSV bundle could not include client delivery details.",
        severity: "Error",
      },
    ],
  },
  {
    id: "ev-004",
    title: "Line A load scenario",
    projectName: "Assembly Line A",
    sourceName: "Multiple sources",
    scenarioName: "Line A load scenario",
    runId: "scenario-run-08",
    runType: "Scenario",
    initiator: "Priya S.",
    startedAt: "Jun 25, 2026 13:58",
    completedAt: "Jun 25, 2026 14:04",
    duration: "06:09",
    status: "Incomplete",
    exportState: "Not exported",
    completeness: "Scenario evidence is partial because one fault step ended early.",
    valueCount: 18840,
    clientCount: 3,
    sizeLabel: "26.1 MB",
    formats: ["PDF", "JSON"],
    timeline: [
      {
        id: "ev-004-t1",
        time: "13:58:00",
        title: "Scenario started",
        description: "Load scenario began across replay and synthetic steps.",
      },
      {
        id: "ev-004-t2",
        time: "14:01:44",
        title: "Fault ended early",
        description: "Network delay fault ended before the configured duration.",
        tone: "warning",
      },
      {
        id: "ev-004-t3",
        time: "14:04:09",
        title: "Partial evidence ready",
        description: "Scenario summary and available client delivery are ready.",
      },
    ],
    clients: [
      {
        id: "ev-004-c1",
        name: "Scenario observer",
        protocol: "OPC UA",
        delivery: "Complete",
      },
      {
        id: "ev-004-c2",
        name: "Fault monitor",
        protocol: "Modbus TCP",
        delivery: "Partial",
      },
      {
        id: "ev-004-c3",
        name: "Audit listener",
        protocol: "OPC UA",
        delivery: "Complete",
      },
    ],
    issues: [
      {
        id: "ev-004-i1",
        label: "Early fault stop",
        description: "The delay fault stopped 22 seconds before the planned end.",
        severity: "Warning",
      },
    ],
  },
  {
    id: "ev-005",
    title: "Backup feeder restart capture",
    projectName: "Assembly Line A",
    sourceId: "src-04",
    sourceName: "Backup feeder stream",
    sourcePath: "/data-sources/src-04",
    runId: "run-5",
    runType: "Recording",
    initiator: "Alex M.",
    startedAt: "Jun 25, 2026 14:40",
    duration: "Running",
    status: "In progress",
    exportState: "Not ready",
    completeness: "Evidence is still assembling while capture continues.",
    valueCount: 860,
    clientCount: 0,
    sizeLabel: "Assembling",
    formats: ["PDF", "JSON"],
    timeline: [
      {
        id: "ev-005-t1",
        time: "14:40:00",
        title: "Recording started",
        description: "Backup feeder source restarted and capture began.",
      },
      {
        id: "ev-005-t2",
        time: "14:41:18",
        title: "Evidence assembling",
        description: "Runtime summary is collecting values and source state.",
      },
    ],
    clients: [],
    issues: [],
  },
];
