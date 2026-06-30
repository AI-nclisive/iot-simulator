import type { EvidenceItem } from "./evidence-types";
import { evidenceExportStateLabel, evidenceKindLabel, evidenceStatusLabel } from "./evidence-types";

export type EvidenceListFilters = {
  initiatorFilter: string;
  scenarioFilter: string;
  searchValue: string;
  stateFilter: string;
};

export function filterEvidenceItems(
  items: EvidenceItem[],
  {
    initiatorFilter,
    scenarioFilter,
    searchValue,
    stateFilter,
  }: EvidenceListFilters,
) {
  const normalizedSearch = searchValue.trim().toLowerCase();

  return items.filter((item) => {
    const statusLabel = evidenceStatusLabel(item.status);
    const exportState = evidenceExportStateLabel(item.exported, item.status);
    const kindLabel = evidenceKindLabel(item.kind);

    const searchMatches =
      normalizedSearch.length === 0 ||
      [
        item.runId,
        kindLabel,
        item.initiator,
        statusLabel,
        exportState,
        item.createdBy,
        ...(item.sourceIds ?? []),
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedSearch);

    const initiatorMatches = initiatorFilter === "all" || item.initiator === initiatorFilter;
    const stateMatches = stateFilter === "all" || statusLabel === stateFilter;
    const scenarioMatches =
      scenarioFilter === "all" ||
      (scenarioFilter === "scenario" ? item.scenarioId !== null : item.scenarioId === null);

    return searchMatches && initiatorMatches && stateMatches && scenarioMatches;
  });
}

export function canExportEvidenceItem(item: EvidenceItem): boolean {
  return item.status !== "CAPTURING";
}
