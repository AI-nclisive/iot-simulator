// Live API types for Evidence — replaces mock EvidenceArtifact.
// Fields only available in the ZIP download bundle (timeline, clients,
// valueCount, clientCount, sizeLabel) are intentionally absent here.

export type EvidenceStatus = "CAPTURING" | "READY" | "PARTIAL" | "EXPORT_FAILED";

export type EvidenceKind = "REPLAY" | "SYNTHETIC" | "SCENARIO" | "RECORDING";

export type EvidenceCompleteness = "COMPLETE" | "PARTIAL" | "FAILED";

/** Display-friendly status label */
export type EvidenceStatusLabel = "In progress" | "Ready" | "Incomplete" | "Export failed";

/** Display-friendly export state label */
export type EvidenceExportStateLabel =
  | "Exported"
  | "Export failed"
  | "Not ready"
  | "Not exported";

/** Flattened shape used by both list and detail pages */
export interface EvidenceItem {
  id: string;
  runId: string;
  status: EvidenceStatus;
  kind: EvidenceKind;
  initiator: string;
  startedAt: string;
  endedAt: string | null;
  completeness: EvidenceCompleteness;
  sourceIds: string[] | undefined;
  scenarioId: string | null;
  recordingId: string | null;
  createdAt: string;
  createdBy: string;
  exported: boolean;
}

/** Raw DTO from the backend (manifest is nested) */
export interface EvidenceResponseDto {
  id: string;
  runId: string;
  status: EvidenceStatus;
  manifest: {
    formatVersion: string;
    runId: string;
    kind: EvidenceKind;
    trigger: string;
    initiator: string;
    startedAt: string;
    endedAt: string | null;
    completeness: EvidenceCompleteness;
    sourceIds: string[];
    scenarioId: string | null;
    recordingId: string | null;
  };
  createdAt: string;
  createdBy: string;
  exported: boolean;
}

/** Paginated list response */
export interface EvidenceListResponse {
  items: EvidenceResponseDto[];
  nextCursor: string | null;
}

/** Flatten the nested manifest into an EvidenceItem */
export function mapEvidenceDto(dto: EvidenceResponseDto): EvidenceItem {
  return {
    id: dto.id,
    runId: dto.runId,
    status: dto.status,
    kind: dto.manifest.kind,
    initiator: dto.manifest.initiator,
    startedAt: dto.manifest.startedAt,
    endedAt: dto.manifest.endedAt,
    completeness: dto.manifest.completeness,
    sourceIds: dto.manifest.sourceIds,
    scenarioId: dto.manifest.scenarioId,
    recordingId: dto.manifest.recordingId,
    createdAt: dto.createdAt,
    createdBy: dto.createdBy,
    exported: dto.exported,
  };
}

/** Human-readable status label */
export function evidenceStatusLabel(status: EvidenceStatus): EvidenceStatusLabel {
  switch (status) {
    case "CAPTURING":
      return "In progress";
    case "READY":
      return "Ready";
    case "PARTIAL":
      return "Incomplete";
    case "EXPORT_FAILED":
      return "Export failed";
  }
}

/** Human-readable export state label */
export function evidenceExportStateLabel(
  exported: boolean,
  status: EvidenceStatus,
): EvidenceExportStateLabel {
  if (status === "EXPORT_FAILED") return "Export failed";
  if (status === "CAPTURING") return "Not ready";
  if (exported) return "Exported";
  return "Not exported";
}

/** Human-readable kind label */
export function evidenceKindLabel(kind: EvidenceKind): string {
  switch (kind) {
    case "REPLAY":
      return "Replay";
    case "SYNTHETIC":
      return "Synthetic";
    case "SCENARIO":
      return "Scenario";
    case "RECORDING":
      return "Recording";
  }
}


/** Human-readable completeness label */
export function evidenceCompletenessLabel(completeness: EvidenceCompleteness): string {
  switch (completeness) {
    case "COMPLETE":
      return "Complete";
    case "PARTIAL":
      return "Partial";
    case "FAILED":
      return "Failed";
  }
}

/** Derive a display title from kind + runId */
export function evidenceTitle(kind: EvidenceKind, runId: string): string {
  return `${evidenceKindLabel(kind)} run · ${runId}`;
}

