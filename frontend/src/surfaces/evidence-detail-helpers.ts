import type { StatusTone } from "../ui/status-badge";
import type {
  EvidenceExportStateLabel,
  EvidenceStatusLabel,
} from "./evidence-types";

export function evidenceStatusTone(status: EvidenceStatusLabel): StatusTone {
  if (status === "Export failed") return "danger";
  if (status === "In progress" || status === "Incomplete") return "warning";
  if (status === "Ready" || status === "Exported") return "accent";
  return "neutral";
}

export function evidenceExportStateTone(
  exportState: EvidenceExportStateLabel,
): StatusTone {
  if (exportState === "Export failed") return "danger";
  if (exportState === "Not ready") return "warning";
  if (exportState === "Exported") return "accent";
  return "neutral";
}

export function isEvidenceExportAvailable(status: EvidenceStatusLabel): boolean {
  return status !== "In progress";
}
