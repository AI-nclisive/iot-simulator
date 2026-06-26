import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildExportScopeLabel,
  isEvidenceExportAvailable,
} from "../../build/frontend-test/surfaces/evidence-detail-helpers.js";

const baseArtifact = {
  id: "ev-1",
  title: "Test",
  projectName: "Proj",
  sourceName: "Source",
  runId: "run-1",
  runType: "Replay",
  initiator: "Alex",
  startedAt: "2026-06-01T10:00:00Z",
  duration: "2m",
  completeness: "Complete",
  valueCount: 100,
  clientCount: 1,
  sizeLabel: "1 MB",
  formats: ["JSON"],
  timeline: [],
  clients: [],
  issues: [],
};

describe("isEvidenceExportAvailable — export dialog availability", () => {
  it("allows export for Ready artifacts", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...baseArtifact, status: "Ready", exportState: "Not exported" }),
      true,
    );
  });

  it("allows export for Exported artifacts (re-export)", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...baseArtifact, status: "Exported", exportState: "Exported" }),
      true,
    );
  });

  it("blocks export when still Capturing", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...baseArtifact, status: "Capturing", exportState: "Not ready" }),
      false,
    );
  });

  it("blocks export when exportState is Not ready regardless of status", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...baseArtifact, status: "Partial", exportState: "Not ready" }),
      false,
    );
  });
});

describe("buildExportScopeLabel", () => {
  it("returns all four sections when everything is included", () => {
    assert.deepEqual(
      buildExportScopeLabel({
        includeSummary: true,
        includeTimeline: true,
        includeClients: true,
        includeIssues: true,
      }),
      ["Summary", "Timeline", "Clients", "Faults and errors"],
    );
  });

  it("returns empty array when nothing is selected", () => {
    assert.deepEqual(
      buildExportScopeLabel({
        includeSummary: false,
        includeTimeline: false,
        includeClients: false,
        includeIssues: false,
      }),
      [],
    );
  });

  it("returns only selected sections", () => {
    assert.deepEqual(
      buildExportScopeLabel({
        includeSummary: true,
        includeTimeline: false,
        includeClients: false,
        includeIssues: true,
      }),
      ["Summary", "Faults and errors"],
    );
  });

  it("preserves order regardless of selection combination", () => {
    const result = buildExportScopeLabel({
      includeSummary: false,
      includeTimeline: true,
      includeClients: true,
      includeIssues: false,
    });
    assert.deepEqual(result, ["Timeline", "Clients"]);
  });
});
