/**
 * Tests for RecordingImportDialog (UI-461)
 *
 * Covers:
 * - Dialog renders when open
 * - Import button disabled until .iotsim file selected
 * - Non-.iotsim files rejected
 * - canImport=false shows read-only message
 * - Uploading spinner shown during in-flight request
 * - Success step shows recording name and calls onImported (no args)
 * - Error step shows error message and Try another file
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingImportDialog } from "./recording-import-dialog";

// ---------------------------------------------------------------------------
// API mock — use vi.hoisted so variables are available inside the factory
// ---------------------------------------------------------------------------

const { mockApiFetch, MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    title: string;
    status: number;
    constructor(title: string, status: number) {
      super(title);
      this.title = title;
      this.status = status;
    }
  }
  return { mockApiFetch: vi.fn(), MockApiError };
});

vi.mock("../api", () => ({
  apiFetch: (...args: unknown[]) => mockApiFetch(...args),
  ApiError: MockApiError,
}));

// Mock createPortal so the dialog renders inline during tests
vi.mock("react-dom", async () => {
  const actual = await vi.importActual<typeof import("react-dom")>("react-dom");
  return {
    ...actual,
    createPortal: (children: React.ReactNode) => children,
  };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeFile(name: string): File {
  return new File(["data"], name, { type: "application/octet-stream" });
}

const DEFAULT_RECORDING = {
  id: "rec-001",
  name: "My Recording",
  valueCount: 1000,
  origin: "imported",
};

function renderOpenDialog(canImport = true, onClose = vi.fn(), onImported = vi.fn()) {
  return render(
    <RecordingImportDialog
      canImport={canImport}
      open={true}
      projectId="proj-001"
      onClose={onClose}
      onImported={onImported}
    />,
  );
}

async function selectFile(file: File) {
  const fileInput = screen.getByTestId("file-input") as HTMLInputElement;
  await userEvent.upload(fileInput, file);
}

// ---------------------------------------------------------------------------
// Dialog opens
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — opens", () => {
  it("renders the dialog heading when open=true", () => {
    renderOpenDialog();
    expect(screen.getByText("Import recording")).toBeTruthy();
  });

  it("renders Import button disabled when no file is selected", () => {
    renderOpenDialog();
    const importBtn = screen.getByRole("button", { name: "Import" }) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(true);
  });

  it("enables Import button once a .iotsim file is selected", async () => {
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    const importBtn = screen.getByRole("button", { name: "Import" }) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(false);
  });

  it("does not enable Import button for non-.iotsim files", async () => {
    renderOpenDialog();
    await selectFile(makeFile("recording.json"));
    const importBtn = screen.getByRole("button", { name: "Import" }) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(true);
  });

  it("shows read-only message when canImport=false", () => {
    renderOpenDialog(false);
    expect(screen.getByText(/Importing recordings is available to Admins only/i)).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Uploading state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — uploading", () => {
  it("shows spinner while upload is in flight", async () => {
    mockApiFetch.mockImplementation(() => new Promise(() => {}));
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    expect(screen.getByText("Importing recording…")).toBeTruthy();
    expect(screen.getByRole("status")).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Success (done) state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — success", () => {
  it("shows 'Imported successfully' with recording name after upload", async () => {
    mockApiFetch.mockResolvedValue(DEFAULT_RECORDING);
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByText("Imported successfully")).toBeTruthy();
    });
    expect(screen.getByText("My Recording")).toBeTruthy();
  });

  it("calls onImported with no args after successful upload", async () => {
    const onImported = vi.fn();
    mockApiFetch.mockResolvedValue(DEFAULT_RECORDING);
    render(
      <RecordingImportDialog
        canImport={true}
        open={true}
        projectId="proj-001"
        onClose={vi.fn()}
        onImported={onImported}
      />,
    );
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => expect(onImported).toHaveBeenCalledOnce());
  });

  it("shows Done button after successful upload", async () => {
    mockApiFetch.mockResolvedValue(DEFAULT_RECORDING);
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Done" })).toBeTruthy();
    });
  });
});

// ---------------------------------------------------------------------------
// Error state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — error", () => {
  it("shows generic error message when upload fails with a plain Error", async () => {
    mockApiFetch.mockRejectedValue(new Error("Network error"));
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeTruthy();
    });
    expect(
      screen.getByText("Import failed. Check the file and try again."),
    ).toBeTruthy();
  });

  it("shows API error title when an ApiError is thrown", async () => {
    mockApiFetch.mockRejectedValue(new MockApiError("Invalid file format", 422));
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByText("Invalid file format")).toBeTruthy();
    });
  });

  it("shows 'Try another file' button on error", async () => {
    mockApiFetch.mockRejectedValue(new Error("fail"));
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Try another file" })).toBeTruthy();
    });
  });

  it("returns to select step with disabled Import after clicking 'Try another file'", async () => {
    mockApiFetch.mockRejectedValue(new Error("fail"));
    renderOpenDialog();
    await selectFile(makeFile("recording.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Try another file" })).toBeTruthy();
    });

    await userEvent.click(screen.getByRole("button", { name: "Try another file" }));

    const importBtn = screen.getByRole("button", { name: "Import" }) as HTMLButtonElement;
    expect(importBtn.disabled).toBe(true);
  });
});
