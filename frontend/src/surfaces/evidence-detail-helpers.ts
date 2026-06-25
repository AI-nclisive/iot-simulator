import type { StatusTone } from "../ui/status-badge";
import type { EvidenceArtifact, EvidenceStatus } from "./mock-evidence";

export function evidenceStatusTone(status: EvidenceStatus): StatusTone {
  if (status === "Export failed") return "danger";
  if (status === "Capturing" || status === "Partial") return "warning";
  if (status === "Ready" || status === "Exported") return "accent";
  return "neutral";
}

export function evidenceExportStateTone(
  exportState: EvidenceArtifact["exportState"],
): StatusTone {
  if (exportState === "Export failed") return "danger";
  if (exportState === "Not ready") return "warning";
  if (exportState === "Exported") return "accent";
  return "neutral";
}

export function evidenceIssueTone(severity: "Warning" | "Error"): StatusTone {
  return severity === "Error" ? "danger" : "warning";
}

export function evidenceTimelineTone(
  tone: "neutral" | "warning" | "danger" = "neutral",
): StatusTone {
  if (tone === "danger") return "danger";
  if (tone === "warning") return "warning";
  return "neutral";
}

export function evidenceDeliveryTone(
  delivery: "Complete" | "Partial" | "Disconnected",
): StatusTone {
  if (delivery === "Disconnected") return "danger";
  if (delivery === "Partial") return "warning";
  return "accent";
}

export function isEvidenceExportAvailable(evidence: EvidenceArtifact): boolean {
  return evidence.status !== "Capturing" && evidence.exportState !== "Not ready";
}

export type ExportScope = {
  includeSummary: boolean;
  includeTimeline: boolean;
  includeClients: boolean;
  includeIssues: boolean;
};

export function buildExportScopeLabel(scope: ExportScope): string[] {
  return [
    scope.includeSummary ? "Summary" : null,
    scope.includeTimeline ? "Timeline" : null,
    scope.includeClients ? "Clients" : null,
    scope.includeIssues ? "Faults and errors" : null,
  ].filter((s): s is string => s !== null);
}
