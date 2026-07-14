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
  it("returns true when Ready", () => {
    assert.equal(isEvidenceExportAvailable("Ready", false), true);
  });

  it("returns true when Export failed (retry is possible)", () => {
    assert.equal(isEvidenceExportAvailable("Export failed", false), true);
  });

  it("returns false when In progress and run has not ended", () => {
    assert.equal(isEvidenceExportAvailable("In progress", false), false);
  });

  it("returns true when In progress but run has ended", () => {
    assert.equal(isEvidenceExportAvailable("In progress", true), true);
  });
});
