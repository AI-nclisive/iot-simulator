/**
 * Tests for ScenarioStepEditor (UI-062, UI-128)
 *
 * Covers:
 * - Renders the right fields per step type (source, recording, number, select, text, checkbox)
 * - Editing a field commits config + recomputes configured + derives label
 * - Required-field validation surfaces and clears
 * - Read-only when canEdit is false
 * - wait/marker minimal fields
 * - Store-backed source and recording pickers (UI-128)
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScenarioStepEditor } from "./scenario-step-editor";
import type { ScenarioStep, ScenarioStepType } from "./scenario-steps";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockSources = [
  { id: "src-01", name: "Line A telemetry" },
  { id: "src-02", name: "Packaging cell stream" },
];

const mockArtifacts = [
  { id: "rec-01", sourceId: "src-01" },
  { id: "rec-02", sourceId: "src-02" },
];

const mockLoadDataSources = vi.fn();

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: vi.fn(
    (
      selector: (s: {
        dataSources: typeof mockSources;
        loadDataSources: typeof mockLoadDataSources;
      }) => unknown,
    ) => selector({ dataSources: mockSources, loadDataSources: mockLoadDataSources }),
  ),
}));

const mockLoadRecordings = vi.fn();

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: vi.fn(
    (
      selector: (s: {
        artifacts: typeof mockArtifacts;
        loadRecordings: typeof mockLoadRecordings;
      }) => unknown,
    ) => selector({ artifacts: mockArtifacts, loadRecordings: mockLoadRecordings }),
  ),
}));

function makeStep(type: ScenarioStepType, config: Record<string, unknown> = {}): ScenarioStep {
  return { id: "s1", type, label: "", config, configured: false };
}

describe("ScenarioStepEditor", () => {
  it("renders a required source field for a start step", () => {
    render(
      <ScenarioStepEditor step={makeStep("start")} projectId="p1" canEdit onChange={() => {}} />,
    );
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    // Unconfigured → prompt for the required field.
    expect(screen.getByText(/required field/)).toBeTruthy();
  });

  it("commits config, marks configured, and derives a label on select", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioStepEditor step={makeStep("start")} projectId="p1" canEdit onChange={onChange} />,
    );
    const src = mockSources[0];
    await user.selectOptions(screen.getByLabelText(/Target source/), src.id);

    expect(onChange).toHaveBeenCalled();
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.sourceId).toBe(src.id);
    expect(last.configured).toBe(true);
    expect(last.label).toBe(src.name);
  });

  // Regression for #700: the Target-source dropdown was always empty because
  // nothing in the scenario flow ever called loadDataSources — the store's
  // dataSources array only ever got populated by other pages (Data Sources
  // list, recording wizards, ...). Every step type with a "source" field must
  // trigger that fetch itself.
  it.each<ScenarioStepType>(["start", "stop", "synthetic", "fault"])(
    "fetches data sources for a %s step's Target-source dropdown",
    (type) => {
      render(
        <ScenarioStepEditor step={makeStep(type)} projectId="p1" canEdit onChange={() => {}} />,
      );
      expect(mockLoadDataSources).toHaveBeenCalledWith("p1");

      // The dropdown's options must include the project's data sources, matching
      // what the Data Sources list page and the Recording dropdown already show.
      const select = screen.getByLabelText(/Target source/) as HTMLSelectElement;
      const optionLabels = Array.from(select.options).map((o) => o.textContent);
      expect(optionLabels).toEqual(
        expect.arrayContaining(mockSources.map((s) => s.name)),
      );
    },
  );

  it.each<ScenarioStepType>(["wait", "marker"])(
    "does not fetch data sources for step types with no source field (%s)",
    (type) => {
      render(<ScenarioStepEditor step={makeStep(type)} projectId="p1" canEdit onChange={() => {}} />);
      expect(mockLoadDataSources).not.toHaveBeenCalled();
    },
  );

  // Locks in the exact two-dropdown Replay-recording case from the bug report:
  // Recording was already populated correctly, Target source was not.
  it("replay step fetches both data sources and recordings, and both dropdowns are populated", () => {
    render(
      <ScenarioStepEditor step={makeStep("replay")} projectId="p1" canEdit onChange={() => {}} />,
    );

    expect(mockLoadDataSources).toHaveBeenCalledWith("p1");
    expect(mockLoadRecordings).toHaveBeenCalledWith("p1");

    const sourceSelect = screen.getByLabelText(/Target source/) as HTMLSelectElement;
    const sourceOptionLabels = Array.from(sourceSelect.options).map((o) => o.textContent);
    expect(sourceOptionLabels).toEqual(expect.arrayContaining(mockSources.map((s) => s.name)));

    const recordingSelect = screen.getByLabelText(/Recording/) as HTMLSelectElement;
    const recordingOptionLabels = Array.from(recordingSelect.options).map((o) => o.textContent ?? "");
    for (const artifact of mockArtifacts) {
      expect(recordingOptionLabels.some((label) => label.includes(artifact.id))).toBe(true);
    }
  });

  it("replay step needs both source and recording", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioStepEditor step={makeStep("replay")} projectId="p1" canEdit onChange={onChange} />,
    );
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    expect(screen.getByLabelText(/Recording/)).toBeTruthy();

    // Only source set → still not configured (recording still missing).
    await user.selectOptions(screen.getByLabelText(/Target source/), mockSources[0].id);
    expect(onChange.mock.calls.at(-1)![0].configured).toBe(false);
  });

  it("wait step has a numeric duration field", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioStepEditor step={makeStep("wait")} projectId="p1" canEdit onChange={onChange} />,
    );
    const input = screen.getByLabelText(/Duration/);
    await user.type(input, "30");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.seconds).toBe(30);
    expect(last.configured).toBe(true);
  });

  it("clamps a negative duration to 0 (min guard, not just advisory)", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioStepEditor step={makeStep("wait")} projectId="p1" canEdit onChange={onChange} />,
    );
    const input = screen.getByLabelText(/Duration/);
    // Typing a negative value must be clamped to 0, not accepted as -5.
    await user.type(input, "-5");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.seconds).toBe(0);
  });

  it("marker step has a text label field", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioStepEditor step={makeStep("marker")} projectId="p1" canEdit onChange={onChange} />,
    );
    await user.type(screen.getByLabelText(/Marker label/), "Checkpoint");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.label).toBe("Checkpoint");
    expect(last.label).toBe("Checkpoint");
  });

  it("fault step exposes target + kind and prompts to choose a kind", () => {
    render(
      <ScenarioStepEditor step={makeStep("fault")} projectId="p1" canEdit onChange={() => {}} />,
    );
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    expect(screen.getByLabelText(/Fault kind/)).toBeTruthy();
    // With no kind chosen yet, the fault panel prompts for one.
    expect(screen.getByText(/Choose a fault kind/)).toBeTruthy();
  });

  it("changing source on a replay step clears recordingId in the committed config", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    // Start with a recording already selected for src-01.
    render(
      <ScenarioStepEditor
        step={makeStep("replay", { sourceId: "src-01", recordingId: "rec-01" })}
        projectId="p1"
        canEdit
        onChange={onChange}
      />,
    );
    // Change to a different source — handleSourceChange must clear recordingId.
    await user.selectOptions(screen.getByLabelText(/Target source/), "src-02");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.sourceId).toBe("src-02");
    expect(last.config.recordingId).toBeUndefined();
  });

  it("compatibilityAck checkbox — exists, calls onUpdate with true, and reflects true value", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    // (a) Checkbox element exists on a replay step.
    render(
      <ScenarioStepEditor step={makeStep("replay")} projectId="p1" canEdit onChange={onChange} />,
    );
    const checkbox = screen.getByRole("checkbox") as HTMLInputElement;
    expect(checkbox).toBeTruthy();

    // (b) Clicking it calls onChange with compatibilityAck: true.
    await user.click(checkbox);
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.compatibilityAck).toBe(true);

    // (c) When the field value is already true, the checkbox is checked.
    cleanup();
    render(
      <ScenarioStepEditor
        step={makeStep("replay", { compatibilityAck: true })}
        projectId="p1"
        canEdit
        onChange={() => {}}
      />,
    );
    const checkedBox = screen.getByRole("checkbox") as HTMLInputElement;
    expect(checkedBox.checked).toBe(true);
  });

  it("serverValidationIssues — renders error messages for the step", () => {
    render(
      <ScenarioStepEditor
        step={makeStep("start", { sourceId: mockSources[0].id })}
        projectId="p1"
        canEdit
        onChange={() => {}}
        serverValidationIssues={["Source schema mismatch detected.", "Step ordinal out of range."]}
      />,
    );
    expect(screen.getByText("Source schema mismatch detected.")).toBeTruthy();
    expect(screen.getByText("Step ordinal out of range.")).toBeTruthy();
  });

  it("is read-only when canEdit is false", () => {
    render(
      <ScenarioStepEditor
        step={makeStep("start", { sourceId: mockSources[0].id })}
        projectId="p1"
        canEdit={false}
        onChange={() => {}}
      />,
    );
    const select = screen.getByLabelText(/Target source/) as HTMLSelectElement;
    expect(select.disabled).toBe(true);
  });

  it("shows fully-configured when required fields are present", () => {
    render(
      <ScenarioStepEditor
        step={makeStep("start", { sourceId: mockSources[0].id })}
        projectId="p1"
        canEdit
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/fully configured/)).toBeTruthy();
  });
});
