import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingExportDialog } from "./recording-export-dialog";
import type { RecordingRow } from "./mock-recordings";

const mockRecording: RecordingRow = {
  id: "rec-001",
  origin: "captured",
  sourceId: "src-01",
  valueCount: 18400,
  capturedAt: "2026-06-24 09:15",
  capturedBy: "Jordan K.",
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RecordingExportDialog — format options", () => {
  it("renders all three format options when open", () => {
    render(
      <RecordingExportDialog
        open
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText("IoT Simulator package (.iotsim)")).toBeTruthy();
    expect(screen.getByText("Raw values (JSON)")).toBeTruthy();
    expect(screen.getByText("CSV summary")).toBeTruthy();
  });

  it("renders the static dialog header", () => {
    render(
      <RecordingExportDialog
        open
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText("Export recording")).toBeTruthy();
  });
});

describe("RecordingExportDialog — secret exclusion notice", () => {
  it("always shows the secret exclusion notice when open", () => {
    render(
      <RecordingExportDialog
        open
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    const notice = screen.getByTestId("secret-exclusion-notice");
    expect(notice).toBeTruthy();
    expect(notice.textContent).toContain(
      "Credential fields are always excluded from exports.",
    );
  });

  it("does not render the dialog when open is false", () => {
    render(
      <RecordingExportDialog
        open={false}
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    expect(
      screen.queryByText("IoT Simulator package (.iotsim)"),
    ).toBeNull();
    expect(screen.queryByTestId("secret-exclusion-notice")).toBeNull();
  });
});

describe("RecordingExportDialog — CSV format disables include-schema", () => {
  it("disables Include schema definition checkbox when CSV format is selected", async () => {
    const user = userEvent.setup();
    render(
      <RecordingExportDialog
        open
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    const csvRadio = screen.getByDisplayValue("csv");
    await user.click(csvRadio);

    const checkbox = screen.getByRole("checkbox", {
      name: /Include schema definition/i,
    }) as HTMLInputElement;
    expect(checkbox.disabled).toBeTruthy();
  });
});
