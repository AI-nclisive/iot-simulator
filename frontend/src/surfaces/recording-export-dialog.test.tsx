import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingExportDialog } from "./recording-export-dialog";
import type { RecordingRow } from "./mock-recordings";

const mockRecording: RecordingRow = {
  id: "rec-001",
  name: "line-a-baseline",
  type: "recording",
  origin: "captured",
  sourceId: "src-01",
  sourceName: "Line A controller",
  protocol: "OPC UA",
  parameterCount: 2480,
  duration: "4h 12m",
  capturedAt: "2026-06-24 09:15",
  capturedBy: "Jordan K.",
  tags: ["baseline", "line-a"],
  lastUsedAt: "2026-06-25 14:31",
  sizeKb: 18400,
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

  it("renders the artifact name in the dialog header", () => {
    render(
      <RecordingExportDialog
        open
        recording={mockRecording}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByText(`Export ${mockRecording.name}`)).toBeTruthy();
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
