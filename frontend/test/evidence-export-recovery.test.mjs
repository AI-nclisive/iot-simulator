import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { willRetryFail } from "../../build/frontend-test/surfaces/evidence-detail-helpers.js";

describe("willRetryFail", () => {
  it("returns true when all failure conditions are met", () => {
    assert.equal(willRetryFail(true, "CSV bundle", true, 0), true);
  });

  it("returns false when not in recovery mode", () => {
    assert.equal(willRetryFail(false, "CSV bundle", true, 0), false);
  });

  it("returns false when format is not CSV bundle", () => {
    assert.equal(willRetryFail(true, "JSON", true, 0), false);
    assert.equal(willRetryFail(true, "PDF", true, 0), false);
  });

  it("returns false when clients are not included in scope", () => {
    assert.equal(willRetryFail(true, "CSV bundle", false, 0), false);
  });

  it("returns false when clientCount is greater than zero", () => {
    assert.equal(willRetryFail(true, "CSV bundle", true, 1), false);
    assert.equal(willRetryFail(true, "CSV bundle", true, 5), false);
  });

  it("is safe for normal export (not recovery)", () => {
    assert.equal(willRetryFail(false, "JSON", true, 0), false);
    assert.equal(willRetryFail(false, "PDF", false, 0), false);
  });
});
