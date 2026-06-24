import type { DataSourceRow } from "./mock-data-sources";

export type ReusableArtifact = {
  id: string;
  name: string;
  type: "Recording" | "Sample";
  protocol: DataSourceRow["protocol"];
  sourceId?: string;
  sourceName: string;
  createdAt: string;
  createdBy: string;
  durationLabel: string;
  valueCount: number;
  status: "Ready" | "Partial";
};

export const reusableArtifacts: ReusableArtifact[] = [
  {
    id: "artifact-01",
    name: "Line A baseline replay",
    type: "Recording",
    protocol: "OPC UA",
    sourceId: "src-01",
    sourceName: "Line A telemetry",
    createdAt: "Jun 23, 2026 14:12",
    createdBy: "Alex M.",
    durationLabel: "03:22",
    valueCount: 482,
    status: "Ready",
  },
  {
    id: "artifact-02",
    name: "Packaging cell short sample",
    type: "Sample",
    protocol: "Modbus TCP",
    sourceId: "src-02",
    sourceName: "Packaging cell stream",
    createdAt: "Jun 23, 2026 13:48",
    createdBy: "Automation",
    durationLabel: "00:48",
    valueCount: 91,
    status: "Ready",
  },
  {
    id: "artifact-03",
    name: "Field capture partial take",
    type: "Recording",
    protocol: "OPC UA",
    sourceId: "src-03",
    sourceName: "Field capture telemetry",
    createdAt: "Jun 23, 2026 12:26",
    createdBy: "Jordan K.",
    durationLabel: "01:37",
    valueCount: 143,
    status: "Partial",
  },
];
