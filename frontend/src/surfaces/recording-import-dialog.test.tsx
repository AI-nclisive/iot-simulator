/**
 * Tests for RecordingImportDialog (UI-051)
 *
 * Covers:
 * - Dialog renders when open (Next disabled when no file selected)
 * - Incompatible state shows the reason message  (file name starting with 'a' → charCode%3===1)
 * - Ready (ok) state shows "Import" button       (file name starting with 'r' → charCode%3===0)
 * - Unsupported version shows version info        (file name starting with 'b' → charCode%3===2)
 * - Read-only message shown when canImport=false
 *
 * simulateImportValidation rotates based on file name:
 *   charCode[0] % 3 === 0  → ok
 *   charCode[0] % 3 === 1  → incompatible  (also: name includes "incompat")
 *   charCode[0] % 3 === 2  → unsupported_version (also: name includes "version")
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RecordingImportDialog } from "./recording-import-dialog";

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

/** Create a File whose first char determines the mock validation outcome. */
function makeFile(name: string): File {
  return new File(["data"], name, { type: "application/json" });
}

function renderOpenDialog(canImport = true, onClose = vi.fn(), onImported = vi.fn()) {
  return render(
    <RecordingImportDialog
      canImport={canImport}
      open={true}
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
    expect(screen.getByText(/Import artifact/i)).toBeTruthy();
  });

  it("renders Next button disabled when no file is selected", () => {
    renderOpenDialog();
    const nextBtn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(nextBtn.disabled).toBe(true);
  });

  it("enables Next button once a valid file is selected", async () => {
    renderOpenDialog();
    // 'r'.charCodeAt(0) = 114; 114 % 3 = 0 → ok branch
    await selectFile(makeFile("result-ok.json"));
    const nextBtn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(nextBtn.disabled).toBe(false);
  });

  it("shows read-only message when canImport=false", () => {
    renderOpenDialog(false);
    expect(screen.getByText(/Importing artifacts is available to Admins only/i)).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Incompatible state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — incompatible state", () => {
  it("shows 'Incompatible artifact' heading and reason after validation", async () => {
    renderOpenDialog();
    // 'a'.charCodeAt(0) = 97; 97 % 3 = 1 → incompatible branch
    await selectFile(makeFile("artifact-bad.json")); // starts with 'a'
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByText("Incompatible artifact")).toBeTruthy();
    }, { timeout: 2000 });

    expect(screen.getByText(/Protocol mismatch/i)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Try another file" })).toBeTruthy();
  });

  it("shows incompatible state when file name contains 'incompat'", async () => {
    renderOpenDialog();
    // The word "incompat" in the name is an explicit trigger
    await selectFile(makeFile("incompat-sensor.json")); // starts with 'i' (ok normally), but name includes "incompat"
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByText("Incompatible artifact")).toBeTruthy();
    }, { timeout: 2000 });
  });
});

// ---------------------------------------------------------------------------
// Ready (ok) state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — ready to import", () => {
  it("shows 'Ready to import' preview card and Import button", async () => {
    renderOpenDialog();
    // 'r'.charCodeAt(0) = 114; 114 % 3 = 0 → ok branch
    await selectFile(makeFile("result-ok.json"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByText("Ready to import")).toBeTruthy();
    }, { timeout: 2000 });

    expect(screen.getByRole("button", { name: "Import" })).toBeTruthy();
  });

  it("advances to confirm step and shows 'Confirm import' button", async () => {
    renderOpenDialog();
    // 'r' → ok
    await selectFile(makeFile("result-ok.json"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Import" })).toBeTruthy();
    }, { timeout: 2000 });

    await userEvent.click(screen.getByRole("button", { name: "Import" }));

    expect(screen.getByTestId("confirm-import-btn")).toBeTruthy();
  });

  it("calls onImported with the artifact after confirming import", async () => {
    const onImported = vi.fn();
    render(
      <RecordingImportDialog
        canImport={true}
        open={true}
        onClose={vi.fn()}
        onImported={onImported}
      />,
    );
    // 'r'.charCodeAt(0) = 114; 114 % 3 = 0 → ok branch
    await selectFile(makeFile("result-ok.json"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Import" })).toBeTruthy();
    }, { timeout: 2000 });

    await userEvent.click(screen.getByRole("button", { name: "Import" }));
    await userEvent.click(screen.getByTestId("confirm-import-btn"));

    expect(onImported).toHaveBeenCalledOnce();
    expect(onImported).toHaveBeenCalledWith(
      expect.objectContaining({ name: "result-ok", type: "recording", origin: "imported" }),
    );
  });
});

// ---------------------------------------------------------------------------
// Unsupported version state
// ---------------------------------------------------------------------------
describe("RecordingImportDialog — unsupported version", () => {
  it("shows version numbers and Try another file button", async () => {
    renderOpenDialog();
    // 'b'.charCodeAt(0) = 98; 98 % 3 = 2 → unsupported_version branch
    await selectFile(makeFile("backup-too-new.json")); // starts with 'b'
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByText("Unsupported artifact version")).toBeTruthy();
    }, { timeout: 2000 });

    expect(screen.getByText("3.2.0")).toBeTruthy();
    expect(screen.getByText("2.9.1")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Try another file" })).toBeTruthy();
  });

  it("shows unsupported version when file name contains 'version'", async () => {
    renderOpenDialog();
    // "version" in the name is an explicit trigger
    await selectFile(makeFile("version-artifact.iotsim"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(screen.getByText("Unsupported artifact version")).toBeTruthy();
    }, { timeout: 2000 });
  });
});
