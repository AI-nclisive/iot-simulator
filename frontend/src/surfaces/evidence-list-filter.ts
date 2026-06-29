import type { EvidenceArtifact } from "./mock-evidence";

export type EvidenceListFilters = {
  initiatorFilter: string;
  projectFilter: string;
  scenarioFilter: string;
  searchValue: string;
  sourceFilter: string;
  stateFilter: string;
};

export function filterEvidenceArtifacts(
  artifacts: EvidenceArtifact[],
  {
    initiatorFilter,
    projectFilter,
    scenarioFilter,
    searchValue,
    sourceFilter,
    stateFilter,
  }: EvidenceListFilters,
) {
  const normalizedSearch = searchValue.trim().toLowerCase();

  return artifacts.filter((artifact) => {
    const searchMatches =
      normalizedSearch.length === 0 ||
      [
        artifact.title,
        artifact.projectName,
        artifact.sourceName,
        artifact.scenarioName ?? "",
        artifact.runId,
        artifact.runType,
        artifact.initiator,
        artifact.status,
        artifact.exportState,
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedSearch);

    const projectMatches = projectFilter === "all" || artifact.projectName === projectFilter;
    const sourceMatches = sourceFilter === "all" || artifact.sourceName === sourceFilter;
    const initiatorMatches =
      initiatorFilter === "all" || artifact.initiator === initiatorFilter;
    const stateMatches = stateFilter === "all" || artifact.status === stateFilter;
    const scenarioMatches =
      scenarioFilter === "all" ||
      (scenarioFilter === "scenario" ? artifact.scenarioName : !artifact.scenarioName);

    return (
      searchMatches &&
      projectMatches &&
      sourceMatches &&
      initiatorMatches &&
      stateMatches &&
      scenarioMatches
    );
  });
}

export function canExportEvidenceArtifact(artifact: EvidenceArtifact) {
  return artifact.status !== "In progress" && artifact.exportState !== "Not ready";
}
