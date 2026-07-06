export type ArtifactOrigin = "captured" | "imported";

export type ReusableArtifact = {
  id: string;
  name?: string;
  sourceId?: string;
  origin?: ArtifactOrigin;
  createdAt: string;
  createdBy: string;
  valueCount: number;
};

export const reusableArtifacts: ReusableArtifact[] = [
  {
    id: "artifact-01",
    sourceId: "src-01",
    createdAt: "Jun 23, 2026 14:12",
    createdBy: "Alex M.",
    valueCount: 482,
  },
  {
    id: "artifact-02",
    sourceId: "src-02",
    createdAt: "Jun 23, 2026 13:48",
    createdBy: "Automation",
    valueCount: 91,
  },
  {
    id: "artifact-03",
    sourceId: "src-03",
    createdAt: "Jun 23, 2026 12:26",
    createdBy: "Jordan K.",
    valueCount: 143,
  },
];
