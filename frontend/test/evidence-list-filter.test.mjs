import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  canExportEvidenceArtifact,
  filterEvidenceArtifacts,
} from "../../build/frontend-test/surfaces/evidence-list-filter.js";

const baseFilters = {
  initiatorFilter: "all",
  projectFilter: "all",
  scenarioFilter: "all",
  searchValue: "",
  sourceFilter: "all",
  stateFilter: "all",
};

const artifacts = [
  {
    exportState: "Not exported",
    id: "ev-a",
    initiator: "Alex M.",
    projectName: "Assembly Line A",
    runId: "run-1",
    runType: "Replay",
    scenarioName: undefined,
    sourceName: "Line A telemetry",
    status: "Ready",
    title: "Line A replay verification",
  },
  {
    exportState: "Export failed",
    id: "ev-b",
    initiator: "Jordan K.",
    projectName: "Field Replay Lab",
    runId: "run-3",
    runType: "Recording",
    scenarioName: undefined,
    sourceName: "Field capture telemetry",
    status: "Export failed",
    title: "Field capture partial take",
  },
  {
    exportState: "Not exported",
    id: "ev-c",
    initiator: "Priya S.",
    projectName: "Assembly Line A",
    runId: "scenario-run-08",
    runType: "Scenario",
    scenarioName: "Line A load scenario",
    sourceName: "Multiple sources",
    status: "Partial",
    title: "Line A load scenario",
  },
  {
    exportState: "Not ready",
    id: "ev-d",
    initiator: "Alex M.",
    projectName: "Assembly Line A",
    runId: "run-5",
    runType: "Recording",
    scenarioName: undefined,
    sourceName: "Backup feeder stream",
    status: "Capturing",
    title: "Backup feeder restart capture",
  },
];

function filterWith(overrides) {
  return filterEvidenceArtifacts(artifacts, { ...baseFilters, ...overrides }).map(
    (artifact) => artifact.id,
  );
}

describe("filterEvidenceArtifacts", () => {
  it("searches across title, project, source, run, initiator, state, and export state", () => {
    assert.deepEqual(filterWith({ searchValue: "verification" }), ["ev-a"]);
    assert.deepEqual(filterWith({ searchValue: "field replay lab" }), ["ev-b"]);
    assert.deepEqual(filterWith({ searchValue: "backup feeder" }), ["ev-d"]);
    assert.deepEqual(filterWith({ searchValue: "scenario-run-08" }), ["ev-c"]);
    assert.deepEqual(filterWith({ searchValue: "Jordan" }), ["ev-b"]);
    assert.deepEqual(filterWith({ searchValue: "capturing" }), ["ev-d"]);
    assert.deepEqual(filterWith({ searchValue: "export failed" }), ["ev-b"]);
  });

  it("keeps only scenario rows when the scenario filter is selected", () => {
    assert.deepEqual(filterWith({ scenarioFilter: "scenario" }), ["ev-c"]);
  });

  it("keeps only source rows when the source filter is selected", () => {
    assert.deepEqual(filterWith({ scenarioFilter: "source" }), ["ev-a", "ev-b", "ev-d"]);
  });

  it("combines filters with AND semantics", () => {
    assert.deepEqual(
      filterWith({
        projectFilter: "Assembly Line A",
        stateFilter: "Ready",
      }),
      ["ev-a"],
    );
    assert.deepEqual(
      filterWith({
        projectFilter: "Assembly Line A",
        stateFilter: "Export failed",
      }),
      [],
    );
  });
});

describe("canExportEvidenceArtifact", () => {
  it("uses export readiness as well as capture state", () => {
    assert.equal(canExportEvidenceArtifact(artifacts[0]), true);
    assert.equal(canExportEvidenceArtifact(artifacts[1]), true);
    assert.equal(canExportEvidenceArtifact(artifacts[3]), false);
    assert.equal(
      canExportEvidenceArtifact({
        ...artifacts[0],
        exportState: "Not ready",
        status: "Ready",
      }),
      false,
    );
  });
});
