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

export function isEvidenceExportAvailable(
  status: EvidenceStatusLabel,
  runEnded: boolean,
): boolean {
  // CAPTURING ("In progress") is the pre-export state — allow export once the
  // run has a final endedAt so the user can assemble the bundle.
  return status !== "In progress" || runEnded;
}
