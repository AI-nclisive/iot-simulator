/**
 * Tests for recordings page preview panel (UI-052)
 *
 * Covers:
 * - FitnessWarning component renders correctly for warn level
 * - FitnessWarning component renders correctly for info level
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FitnessWarning } from "./recordings-page";

vi.mock("../shell/shell-store", () => ({
  useShellStore: vi.fn(
    (selector: (s: { accessMode: string; sharedRole: string; currentProjectId: string }) => unknown) =>
      selector({ accessMode: "local", sharedRole: "observer", currentProjectId: "" }),
  ),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: vi.fn(
    (selector: (s: {
      artifacts: never[];
      samples: never[];
      isLoading: boolean;
      isSamplesLoading: boolean;
      error: null;
      samplesError: null;
      loadRecordings: () => void;
      loadSamples: () => void;
      deleteSample: () => void;
    }) => unknown) =>
      selector({
        artifacts: [],
        samples: [],
        isLoading: false,
        isSamplesLoading: false,
        error: null,
        samplesError: null,
        loadRecordings: vi.fn(),
        loadSamples: vi.fn(),
        deleteSample: vi.fn(),
      }),
  ),
}));

afterEach(() => {
  cleanup();
});

// ─── FitnessWarning rendering tests ──────────────────────────────────────────

describe("FitnessWarning component — warn level", () => {
  it("renders Warning: label and message for warn level", () => {
    render(
      <FitnessWarning
        level="warn"
        message="This is a warning message."
      />,
    );
    expect(screen.getByText("Warning:")).toBeTruthy();
    expect(screen.getByText("This is a warning message.")).toBeTruthy();
  });
});

describe("FitnessWarning component — info level", () => {
  it("renders Note: label and message for info level", () => {
    render(
      <FitnessWarning
        level="info"
        message="This is an info message."
      />,
    );
    expect(screen.getByText("Note:")).toBeTruthy();
    expect(screen.getByText("This is an info message.")).toBeTruthy();
  });
});
