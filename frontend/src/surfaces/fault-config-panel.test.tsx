/**
 * Tests for FaultConfigPanel (UI-063 / UI-130).
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

  it("shows delayMs param for DELAY plus timing fields", () => {
    render(<FaultConfigPanel config={{ kind: "DELAY" }} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Added latency/)).toBeTruthy();
    expect(screen.getByLabelText(/Start after/)).toBeTruthy();
    expect(screen.getByLabelText(/Duration/)).toBeTruthy();
  });

  it("shows no kind-specific params for BAD_VALUE (only timing)", () => {
    render(<FaultConfigPanel config={{ kind: "BAD_VALUE" }} canEdit onChange={() => {}} />);
    // No kind-specific params — only timing and behavior description present.
    expect(screen.getByLabelText(/Start after/)).toBeTruthy();
    expect(screen.getByText(/What this does/)).toBeTruthy();
  });

  it("shows no kind-specific params for CONNECTION_DROP", () => {
    render(<FaultConfigPanel config={{ kind: "CONNECTION_DROP" }} canEdit onChange={() => {}} />);
    expect(screen.getByLabelText(/Start after/)).toBeTruthy();
  });

  it("commits a numeric param and clamps negatives to 0", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<FaultConfigPanel config={{ kind: "DELAY" }} canEdit onChange={onChange} />);
    await user.type(screen.getByLabelText(/Added latency/), "-5");
    const last = onChange.mock.calls.at(-1)!;
    expect(last[0]).toBe("delayMs");
    expect(last[1]).toBe(0);
  });

  it("renders the plain-language behavior description for MISSING_VALUE", () => {
    render(
      <FaultConfigPanel
        config={{ kind: "MISSING_VALUE", durationSeconds: 15 }}
        canEdit
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/What this does/)).toBeTruthy();
    expect(screen.getByText(/for 15s/)).toBeTruthy();
  });

  it("renders the plain-language description for DELAY with latency", () => {
    render(
      <FaultConfigPanel
        config={{ kind: "DELAY", delayMs: 40 }}
        canEdit
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/40 ms/)).toBeTruthy();
  });

  it("is read-only when canEdit is false", () => {
    render(<FaultConfigPanel config={{ kind: "DELAY" }} canEdit={false} onChange={() => {}} />);
    const input = screen.getByLabelText(/Added latency/) as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });
});
