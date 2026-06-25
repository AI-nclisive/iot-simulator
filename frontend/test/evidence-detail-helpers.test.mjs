import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  evidenceDeliveryTone,
  evidenceExportStateTone,
  evidenceIssueTone,
  evidenceStatusTone,
  evidenceTimelineTone,
  isEvidenceExportAvailable,
} from "../../build/frontend-test/surfaces/evidence-detail-helpers.js";

describe("evidenceStatusTone", () => {
  it("maps danger for Export failed", () => {
    assert.equal(evidenceStatusTone("Export failed"), "danger");
  });

  it("maps warning for Capturing and Partial", () => {
    assert.equal(evidenceStatusTone("Capturing"), "warning");
    assert.equal(evidenceStatusTone("Partial"), "warning");
  });

  it("maps accent for Ready and Exported", () => {
    assert.equal(evidenceStatusTone("Ready"), "accent");
    assert.equal(evidenceStatusTone("Exported"), "accent");
  });
});

describe("evidenceExportStateTone", () => {
  it("maps danger for Export failed", () => {
    assert.equal(evidenceExportStateTone("Export failed"), "danger");
  });

  it("maps warning for Not ready", () => {
    assert.equal(evidenceExportStateTone("Not ready"), "warning");
  });

  it("maps accent for Exported", () => {
    assert.equal(evidenceExportStateTone("Exported"), "accent");
  });

  it("maps neutral for Not exported", () => {
    assert.equal(evidenceExportStateTone("Not exported"), "neutral");
  });
});

describe("evidenceIssueTone", () => {
  it("maps danger for Error severity", () => {
    assert.equal(evidenceIssueTone("Error"), "danger");
  });

  it("maps warning for Warning severity", () => {
    assert.equal(evidenceIssueTone("Warning"), "warning");
  });
});

describe("evidenceTimelineTone", () => {
  it("maps correctly for each tone", () => {
    assert.equal(evidenceTimelineTone("danger"), "danger");
    assert.equal(evidenceTimelineTone("warning"), "warning");
    assert.equal(evidenceTimelineTone("neutral"), "neutral");
    assert.equal(evidenceTimelineTone(undefined), "neutral");
  });
});

describe("evidenceDeliveryTone", () => {
  it("maps danger for Disconnected", () => {
    assert.equal(evidenceDeliveryTone("Disconnected"), "danger");
  });

  it("maps warning for Partial", () => {
    assert.equal(evidenceDeliveryTone("Partial"), "warning");
  });

  it("maps accent for Complete", () => {
    assert.equal(evidenceDeliveryTone("Complete"), "accent");
  });
});

describe("isEvidenceExportAvailable", () => {
  const base = {
    id: "ev-1",
    title: "Test",
    projectName: "Proj",
    sourceName: "Source",
    runId: "run-1",
    runType: "Replay",
    initiator: "Alex",
    startedAt: "2026-06-01T10:00:00Z",
    duration: "2m 10s",
    completeness: "Complete",
    valueCount: 100,
    clientCount: 1,
    sizeLabel: "1.2 MB",
    formats: ["JSON"],
    timeline: [],
    clients: [],
    issues: [],
  };

  it("returns true when Ready and Not exported", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...base, status: "Ready", exportState: "Not exported" }),
      true,
    );
  });

  it("returns true when Export failed (retry is possible)", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...base, status: "Export failed", exportState: "Export failed" }),
      true,
    );
  });

  it("returns false when Capturing", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...base, status: "Capturing", exportState: "Not ready" }),
      false,
    );
  });

  it("returns false when exportState is Not ready even if status is Ready", () => {
    assert.equal(
      isEvidenceExportAvailable({ ...base, status: "Ready", exportState: "Not ready" }),
      false,
    );
  });
});
