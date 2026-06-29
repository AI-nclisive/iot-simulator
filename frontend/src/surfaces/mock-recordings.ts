export type ArtifactOrigin = "captured" | "imported";

export type RecordingRow = {
  id: string;
  sourceId: string;
  origin: ArtifactOrigin;
  valueCount: number;
  capturedAt: string;
  capturedBy: string;
};

export const mockRecordings: RecordingRow[] = [
  {
    id: "rec-001",
    sourceId: "src-01",
    origin: "captured",
    valueCount: 18400,
    capturedAt: "2026-06-24 09:15",
    capturedBy: "Jordan K.",
  },
  {
    id: "rec-002",
    sourceId: "src-02",
    origin: "imported",
    valueCount: 420,
    capturedAt: "2026-06-23 22:00",
    capturedBy: "CI pipeline",
  },
  {
    id: "rec-003",
    sourceId: "src-03",
    origin: "captured",
    valueCount: 8900,
    capturedAt: "2026-06-25 14:00",
    capturedBy: "Jordan K.",
  },
  {
    id: "rec-004",
    sourceId: "src-01",
    origin: "captured",
    valueCount: 220,
    capturedAt: "2026-06-20 11:00",
    capturedBy: "Alex M.",
  },
];
