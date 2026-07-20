/**
 * Tests for ScheduleDatePicker (UI-477)
 *
 * Covers:
 * - Renders with a value parsed from the existing YYYY-MM-DDTHH:mm form format
 * - Calls onChange with a value in that same format when a date is picked
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScheduleDatePicker } from "./schedule-date-picker";

// react-datepicker's time list observes its own height via ResizeObserver,
// absent in jsdom.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
if (typeof globalThis.ResizeObserver === "undefined") {
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}

afterEach(() => {
  cleanup();
});

describe("ScheduleDatePicker (UI-477)", () => {
  it("renders the existing value", () => {
    render(<ScheduleDatePicker value="2026-03-05T14:30" onChange={vi.fn()} />);

    const input = screen.getByRole("textbox") as HTMLInputElement;
    expect(input.value).toContain("Mar 5, 2026");
  });

  it("renders an empty field when value is empty", () => {
    render(<ScheduleDatePicker value="" onChange={vi.fn()} placeholder="Select date and time" />);

    const input = screen.getByPlaceholderText("Select date and time") as HTMLInputElement;
    expect(input.value).toBe("");
  });

  it("calls onChange with a YYYY-MM-DDTHH:mm value when a day is picked", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<ScheduleDatePicker value="2026-03-05T14:30" onChange={onChange} />);

    await user.click(screen.getByRole("textbox"));
    // Pick day "10" of the currently-open (March 2026) month grid.
    await user.click(screen.getByRole("gridcell", { name: /March 10th, 2026/i }));

    expect(onChange).toHaveBeenCalled();
    const value = onChange.mock.calls[0][0] as string;
    expect(value).toMatch(/^2026-03-10T\d{2}:\d{2}$/);
  });
});
