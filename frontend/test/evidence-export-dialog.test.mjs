import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildExportScopeLabel,
  isEvidenceExportAvailable,
} from "../../build/frontend-test/surfaces/evidence-detail-helpers.js";

describe("isEvidenceExportAvailable — export dialog availability", () => {
  it("allows export for Ready artifacts", () => {
    assert.equal(isEvidenceExportAvailable("Ready", false), true);
  });

  it("allows re-export after Export failed", () => {
    assert.equal(isEvidenceExportAvailable("Export failed", false), true);
  });

  it("blocks export while In progress and run has not ended", () => {
    assert.equal(isEvidenceExportAvailable("In progress", false), false);
  });

  it("allows export while In progress once the run has ended", () => {
    assert.equal(isEvidenceExportAvailable("In progress", true), true);
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
