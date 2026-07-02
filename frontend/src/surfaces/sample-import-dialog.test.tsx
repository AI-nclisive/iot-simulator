/**
 * Tests for SampleImportDialog (UI-116)
 *
 * Covers:
 * - Dialog renders when open, hidden when closed
 * - Read-only mode shown when canImport=false
 * - acceptFile pre-fills name from filename (stripExtension)
 * - Unsupported file type shows error in drop zone
 * - Duplicate name validation blocks Next
 * - handleConfirm calls createSample with correct args and calls onImported
 * - API error during confirm shows error message
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SampleImportDialog, stripExtension } from "./sample-import-dialog";
import { useArtifactsStore } from "../shell/artifacts-store";

const mockCreateSample = vi.fn();

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: {
    getState: vi.fn(() => ({ createSample: mockCreateSample })),
  },
}));

const defaultProps = {
  open: true,
  canImport: true,
  existingNames: [],
  projectId: "proj-1",
  onClose: vi.fn(),
  onImported: vi.fn(),
};

function makeJsonFile(name = "data.json", content = '{"nodes":[]}') {
  return new File([content], name, { type: "application/json" });
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ─── stripExtension ───────────────────────────────────────────────────────────

describe("stripExtension", () => {
  it("removes .json extension", () => {
    expect(stripExtension("schema.json")).toBe("schema");
  });

  it("removes .iotsim extension", () => {
    expect(stripExtension("export.iotsim")).toBe("export");
  });

  it("handles filenames with dots in base name", () => {
    expect(stripExtension("my.schema.json")).toBe("my.schema");
  });

  it("returns name unchanged if no extension", () => {
    expect(stripExtension("noext")).toBe("noext");
  });
});

// ─── rendering ────────────────────────────────────────────────────────────────

describe("SampleImportDialog — rendering", () => {
  it("renders dialog title when open", () => {
    render(<SampleImportDialog {...defaultProps} />);
    expect(screen.getByText("Add sample — Select file")).toBeTruthy();
  });

  it("renders nothing when closed", () => {
    render(<SampleImportDialog {...defaultProps} open={false} />);
    expect(screen.queryByText("Add sample — Select file")).toBeNull();
  });

  it("shows read-only message when canImport=false", () => {
    render(<SampleImportDialog {...defaultProps} canImport={false} />);
    expect(screen.getByText(/Admins only/)).toBeTruthy();
  });
});

// ─── file selection ───────────────────────────────────────────────────────────

describe("SampleImportDialog — file selection", () => {
  it("pre-fills name from filename when file is selected", async () => {
    render(<SampleImportDialog {...defaultProps} />);

    const input = document.querySelector<HTMLInputElement>("input[type=file]")!;
    await userEvent.upload(input, makeJsonFile("my-recording.json"));

    const nameInput = screen.getByPlaceholderText(
      "Enter a name for this sample",
    ) as HTMLInputElement;
    expect(nameInput.value).toBe("my-recording");
  });

  it("shows error for unsupported file type", async () => {
    render(<SampleImportDialog {...defaultProps} />);

    const input = document.querySelector<HTMLInputElement>("input[type=file]")!;
    await userEvent.upload(
      input,
      new File(["data"], "report.csv", { type: "text/csv" }),
      { applyAccept: false },
    );

    expect(screen.getByText(/Unsupported file type/)).toBeTruthy();
  });
});

// ─── name validation ──────────────────────────────────────────────────────────

describe("SampleImportDialog — duplicate name validation", () => {
  it("shows error when name already exists", async () => {
    render(
      <SampleImportDialog
        {...defaultProps}
        existingNames={["Baseline"]}
      />,
    );

    const input = document.querySelector<HTMLInputElement>("input[type=file]")!;
    await userEvent.upload(input, makeJsonFile("Baseline.json"));

    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByText(/already exists/)).toBeTruthy();
  });
});

// ─── confirm — API call ───────────────────────────────────────────────────────

describe("SampleImportDialog — confirm step calls createSample", () => {
  beforeEach(() => {
    mockCreateSample.mockResolvedValue({
      id: "smp-backend-1",
      projectId: "proj-1",
      derivedFromRecordingId: "",
      name: "my-recording",
      selection: "full",
      tags: [],
      createdAt: "2026-07-02T00:00:00Z",
      createdBy: "Import",
      version: 0,
    });
  });

  async function reachConfirm() {
    render(<SampleImportDialog {...defaultProps} />);
    const input = document.querySelector<HTMLInputElement>("input[type=file]")!;
    await userEvent.upload(input, makeJsonFile("my-recording.json"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await screen.findByText("Add sample — Confirm");
  }

  it("calls createSample with name and projectId on confirm", async () => {
    await reachConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Add sample" }));

    expect(mockCreateSample).toHaveBeenCalledWith("proj-1", {
      name: "my-recording",
      derivedFromRecordingId: "",
      selection: "full",
      tags: [],
    });
  });

  it("calls onImported with the backend sample on success", async () => {
    const onImported = vi.fn();
    render(<SampleImportDialog {...defaultProps} onImported={onImported} />);
    const input = document.querySelector<HTMLInputElement>("input[type=file]")!;
    await userEvent.upload(input, makeJsonFile("my-recording.json"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await screen.findByText("Add sample — Confirm");
    await userEvent.click(screen.getByRole("button", { name: "Add sample" }));
    await vi.waitFor(() => expect(onImported).toHaveBeenCalledWith(
      expect.objectContaining({ id: "smp-backend-1", name: "my-recording" }),
    ));
  });

  it("shows error message when createSample API fails", async () => {
    mockCreateSample.mockRejectedValueOnce(new Error("Network error"));
    await reachConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Add sample" }));

    await screen.findByText(/Failed to save sample/);
  });
});
