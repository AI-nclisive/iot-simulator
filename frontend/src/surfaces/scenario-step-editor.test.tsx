/**
 * Tests for ScenarioStepEditor (UI-062)
 *
 * Covers:
 * - Renders the right fields per step type (source, recording, number, select, text)
 * - Editing a field commits config + recomputes configured + derives label
 * - Required-field validation surfaces and clears
 * - Read-only when canEdit is false
 * - wait/marker minimal fields
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScenarioStepEditor } from "./scenario-step-editor";
import type { ScenarioStep, ScenarioStepType } from "./scenario-steps";
import { sourceRows } from "./mock-data-sources";

afterEach(cleanup);

function makeStep(type: ScenarioStepType, config: Record<string, unknown> = {}): ScenarioStep {
  return { id: "s1", type, label: "", config, configured: false };
}

describe("ScenarioStepEditor", () => {
  it("renders a required source field for a start step", () => {
    render(<ScenarioStepEditor step={makeStep("start")} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    // Unconfigured → prompt for the required field.
    expect(screen.getByText(/required field/)).toBeTruthy();
  });

  it("commits config, marks configured, and derives a label on select", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ScenarioStepEditor step={makeStep("start")} canEdit onChange={onChange} />);
    const src = sourceRows[0];
    await user.selectOptions(screen.getByLabelText(/Target source/), src.id);

    expect(onChange).toHaveBeenCalled();
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.sourceId).toBe(src.id);
    expect(last.configured).toBe(true);
    expect(last.label).toBe(src.name);
  });

  it("replay step needs both source and recording", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ScenarioStepEditor step={makeStep("replay")} canEdit onChange={onChange} />);
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    expect(screen.getByLabelText(/Recording/)).toBeTruthy();

    // Only source set → still not configured.
    await user.selectOptions(screen.getByLabelText(/Target source/), sourceRows[0].id);
    expect(onChange.mock.calls.at(-1)![0].configured).toBe(false);
  });

  it("wait step has a numeric duration field", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ScenarioStepEditor step={makeStep("wait")} canEdit onChange={onChange} />);
    const input = screen.getByLabelText(/Duration/);
    await user.type(input, "30");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.seconds).toBe(30);
    expect(last.configured).toBe(true);
  });

  it("clamps a negative duration to 0 (min guard, not just advisory)", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ScenarioStepEditor step={makeStep("wait")} canEdit onChange={onChange} />);
    const input = screen.getByLabelText(/Duration/);
    // Typing a negative value must be clamped to 0, not accepted as -5.
    await user.type(input, "-5");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.seconds).toBe(0);
  });

  it("marker step has a text note field", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ScenarioStepEditor step={makeStep("marker")} canEdit onChange={onChange} />);
    await user.type(screen.getByLabelText(/Marker note/), "Checkpoint");
    const last = onChange.mock.calls.at(-1)![0];
    expect(last.config.note).toBe("Checkpoint");
    expect(last.label).toBe("Checkpoint");
  });

  it("fault step exposes target + kind (details deferred to UI-063)", () => {
    render(<ScenarioStepEditor step={makeStep("fault")} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Target source/)).toBeTruthy();
    expect(screen.getByLabelText(/Fault kind/)).toBeTruthy();
    expect(screen.getByText(/configured in the fault step/)).toBeTruthy();
  });

  it("is read-only when canEdit is false", () => {
    render(
      <ScenarioStepEditor
        step={makeStep("start", { sourceId: sourceRows[0].id })}
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
        step={makeStep("start", { sourceId: sourceRows[0].id })}
        canEdit
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/fully configured/)).toBeTruthy();
  });
});
