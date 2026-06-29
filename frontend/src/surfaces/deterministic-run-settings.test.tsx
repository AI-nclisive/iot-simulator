import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { DeterministicRunSettings } from "./deterministic-run-settings";

describe("DeterministicRunSettings", () => {
  it("shows explanation text when the toggle is off", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    expect(
      screen.getByText(
        "Replay uses live timing and original value order. Results may vary between runs.",
      ),
    ).toBeTruthy();
  });

  it("does not show expanded settings when the toggle is off", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    expect(screen.queryByText("MODE")).toBeNull();
    expect(screen.queryByText("ORDERING MODE")).toBeNull();
  });

  it("shows expanded settings after enabling the toggle", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    expect(screen.getByText("Mode")).toBeTruthy();
    expect(screen.getByText("Ordering mode")).toBeTruthy();
  });

  it("shows invalid seed error message for non-numeric input", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    const seedInput = screen.getByLabelText("Seed value");
    fireEvent.change(seedInput, { target: { value: "abc" } });

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Seed must be a number between 1 and 9,999,999.");
  });

  it("shows invalid seed error message when seed is out of range", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    const seedInput = screen.getByLabelText("Seed value");
    fireEvent.change(seedInput, { target: { value: "10000000" } });

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Seed must be a number between 1 and 9,999,999.");
  });

  it("does not show seed error for a valid seed value", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    const seedInput = screen.getByLabelText("Seed value");
    fireEvent.change(seedInput, { target: { value: "12345" } });

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("evidence traceability checkbox is visible when enabled", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    expect(
      screen.getByRole("checkbox", { name: /record seed and ordering in evidence export/i }),
    ).toBeTruthy();
  });

  it("evidence traceability checkbox is checked by default", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    const traceCheckbox = screen.getByRole("checkbox", {
      name: /record seed and ordering in evidence export/i,
    }) as HTMLInputElement;

    expect(traceCheckbox.checked).toBe(true);
  });

  it("shows repeatability scope notice when enabled", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    expect(screen.getByText("Repeatability scope")).toBeTruthy();
    expect(
      screen.getByText(
        /Repeatability applies to value sequencing and timing within the simulator/,
      ),
    ).toBeTruthy();
  });

  it("shows incompatible warning when preset mode and alphabetical ordering are both selected", () => {
    render(<DeterministicRunSettings onChange={vi.fn()} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);

    const presetRadio = screen.getByRole("radio", { name: /named preset/i });
    fireEvent.click(presetRadio);

    const alphabeticalRadio = screen.getByRole("radio", { name: /alphabetical by parameter path/i });
    fireEvent.click(alphabeticalRadio);

    expect(
      screen.getByText(
        "Named presets use original capture order. Switch to alphabetical ordering with a custom seed.",
      ),
    ).toBeTruthy();
  });

  it("calls onChange with null when the toggle is turned off", () => {
    const onChange = vi.fn();
    render(<DeterministicRunSettings onChange={onChange} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);
    onChange.mockClear();

    fireEvent.click(toggle);

    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("calls onChange with valid settings when a valid seed is entered", () => {
    const onChange = vi.fn();
    render(<DeterministicRunSettings onChange={onChange} />);

    const toggle = screen.getByRole("checkbox", { name: /deterministic replay/i });
    fireEvent.click(toggle);
    onChange.mockClear();

    const seedInput = screen.getByLabelText("Seed value");
    fireEvent.change(seedInput, { target: { value: "42" } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: "seed",
        seed: "42",
        ordering: "original",
        traceInEvidence: true,
      }),
    );
  });
});
