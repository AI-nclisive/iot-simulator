import { create } from "zustand";
import {
  reusableArtifacts,
  type ReusableArtifact,
} from "../surfaces/mock-artifacts";

type CreateRecordingInput = {
  createdBy: string;
  durationSeconds: number;
  name: string;
  protocol: ReusableArtifact["protocol"];
  sourceId?: string;
  sourceName: string;
  status: ReusableArtifact["status"];
  valueCount: number;
};

type ArtifactsState = {
  artifacts: ReusableArtifact[];
  createRecording: (input: CreateRecordingInput) => string;
};

function nextArtifactId(currentArtifacts: ReusableArtifact[]) {
  const highestId = currentArtifacts.reduce((highest, artifact) => {
    const numericId = Number(artifact.id.replace("artifact-", ""));
    if (Number.isNaN(numericId)) {
      return highest;
    }

    return Math.max(highest, numericId);
  }, 0);

  return `artifact-${String(highestId + 1).padStart(2, "0")}`;
}

function formatDurationLabel(durationSeconds: number) {
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function formatCreatedAt() {
  return new Intl.DateTimeFormat("en-US", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date());
}

export const useArtifactsStore = create<ArtifactsState>((set) => ({
  artifacts: reusableArtifacts,
  createRecording: ({
    createdBy,
    durationSeconds,
    name,
    protocol,
    sourceId,
    sourceName,
    status,
    valueCount,
  }) => {
    let createdId = "";

    set((state) => {
      createdId = nextArtifactId(state.artifacts);

      return {
        artifacts: [
          {
            createdAt: formatCreatedAt(),
            createdBy,
            durationLabel: formatDurationLabel(durationSeconds),
            id: createdId,
            name,
            protocol,
            sourceId,
            sourceName,
            status,
            type: "Recording",
            valueCount,
          },
          ...state.artifacts,
        ],
      };
    });

    return createdId;
  },
}));

export type { CreateRecordingInput };
