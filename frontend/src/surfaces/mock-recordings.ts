export type ArtifactType = "recording" | "sample";
export type ArtifactOrigin = "captured" | "imported" | "synthetic";

export type RecordingRow = {
  id: string;
  name: string;
  type: ArtifactType;
  origin: ArtifactOrigin;
  sourceId: string;
  sourceName: string;
  protocol: "OPC UA" | "Modbus TCP";
  parameterCount: number;
  duration: string;
  capturedAt: string;
  capturedBy: string;
  tags: string[];
  lastUsedAt: string | null;
  sizeKb: number;
};

export const mockRecordings: RecordingRow[] = [
  {
    id: "rec-001",
    name: "line-a-baseline",
    type: "recording",
    origin: "captured",
    sourceId: "src-01",
    sourceName: "Line A controller",
    protocol: "OPC UA",
    parameterCount: 2480,
    duration: "4h 12m",
    capturedAt: "2026-06-24 09:15",
    capturedBy: "Jordan K.",
    tags: ["baseline", "line-a"],
    lastUsedAt: "2026-06-25 14:31",
    sizeKb: 18400,
  },
  {
    id: "rec-002",
    name: "nightly-regression",
    type: "sample",
    origin: "imported",
    sourceId: "src-02",
    sourceName: "Modbus gateway",
    protocol: "Modbus TCP",
    parameterCount: 320,
    duration: "8m 05s",
    capturedAt: "2026-06-23 22:00",
    capturedBy: "CI pipeline",
    tags: ["regression", "ci"],
    lastUsedAt: "2026-06-26 14:27",
    sizeKb: 420,
  },
  {
    id: "rec-003",
    name: "field-capture-telemetry",
    type: "recording",
    origin: "captured",
    sourceId: "src-03",
    sourceName: "Field sensor array",
    protocol: "OPC UA",
    parameterCount: 1120,
    duration: "1h 44m",
    capturedAt: "2026-06-25 14:00",
    capturedBy: "Jordan K.",
    tags: ["field", "telemetry"],
    lastUsedAt: null,
    sizeKb: 8900,
  },
  {
    id: "rec-004",
    name: "pressure-steady-synthetic",
    type: "sample",
    origin: "synthetic",
    sourceId: "src-01",
    sourceName: "Line A controller",
    protocol: "OPC UA",
    parameterCount: 50,
    duration: "30m",
    capturedAt: "2026-06-20 11:00",
    capturedBy: "Alex M.",
    tags: ["synthetic", "pressure"],
    lastUsedAt: "2026-06-21 09:00",
    sizeKb: 220,
  },
];
