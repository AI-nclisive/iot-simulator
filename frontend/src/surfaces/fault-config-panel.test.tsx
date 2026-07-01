/**
 * Tests for FaultConfigPanel (UI-063).
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FaultConfigPanel } from "./fault-config-panel";

afterEach(cleanup);

describe("FaultConfigPanel", () => {
  it("prompts to choose a kind when none is set", () => {
    render(<FaultConfigPanel config={{}} canEdit onChange={() => {}} />);
    expect(screen.getByText(/Choose a fault kind above/)).toBeTruthy();
  });

  it("shows drop-rate param for a drop fault plus timing fields", () => {
    render(<FaultConfigPanel config={{ kind: "drop" }} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Drop rate/)).toBeTruthy();
    expect(screen.getByLabelText(/Start after/)).toBeTruthy();
    expect(screen.getByLabelText(/Duration/)).toBeTruthy();
  });

  it("shows a quality select for a quality fault", () => {
    render(<FaultConfigPanel config={{ kind: "quality" }} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Reported quality/)).toBeTruthy();
  });

  it("commits a numeric param and clamps negatives to 0", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<FaultConfigPanel config={{ kind: "delay" }} canEdit onChange={onChange} />);
    await user.type(screen.getByLabelText(/Added latency/), "-5");
    const last = onChange.mock.calls.at(-1)!;
    expect(last[0]).toBe("delayMs");
    expect(last[1]).toBe(0);
  });

  it("renders the plain-language behavior description", () => {
    render(
      <FaultConfigPanel
        config={{ kind: "drop", dropRate: 40, durationSeconds: 15 }}
        canEdit
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/What this does/)).toBeTruthy();
    expect(screen.getByText(/40%/)).toBeTruthy();
    expect(screen.getByText(/for 15s/)).toBeTruthy();
  });

  it("is read-only when canEdit is false", () => {
    render(<FaultConfigPanel config={{ kind: "drop" }} canEdit={false} onChange={() => {}} />);
    const input = screen.getByLabelText(/Drop rate/) as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });
});
