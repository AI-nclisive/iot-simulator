/**
 * Tests for RecordingsPage delete action (UI-467)
 *
 * Covers:
 * - delete button opens a confirmation dialog
 * - confirming calls deleteRecording with projectId + recordingId
 * - a rejected delete (e.g. 422 dependency conflict) surfaces the backend detail as a toast
 */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api";
import { RecordingsPage } from "./recordings-page";

const { mockLoadRecordings, mockDeleteRecording, mockPush } = vi.hoisted(() => ({
  mockLoadRecordings: vi.fn().mockResolvedValue(undefined),
  mockDeleteRecording: vi.fn().mockResolvedValue(undefined),
  mockPush: vi.fn(),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      artifacts: [
        {
          id: "rec-1",
          name: "Nightly capture",
          createdAt: "2026-01-01T00:00:00Z",
          createdBy: "alice",
          sourceId: "src-1",
          origin: "captured",
          valueCount: 42,
        },
      ],
      isLoading: false,
      error: null,
      loadRecordings: mockLoadRecordings,
      deleteRecording: mockDeleteRecording,
    }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ dataSources: [], loadDataSources: vi.fn().mockResolvedValue(undefined) }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: mockPush }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RecordingsPage — delete action (UI-467)", () => {
  it("opens a confirmation dialog and calls deleteRecording on confirm", async () => {
    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: /delete recording nightly capture/i }));
    expect(screen.getByRole("dialog")).not.toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Delete recording" }));

    await waitFor(() => expect(mockDeleteRecording).toHaveBeenCalledWith("proj-1", "rec-1"));
  });

  it("surfaces the backend detail as a toast when delete is rejected", async () => {
    mockDeleteRecording.mockRejectedValueOnce(
      new ApiError(422, "Unprocessable Entity", "delete of rec-1 is blocked by existing dependents", undefined),
    );

    render(
      <MemoryRouter>
        <RecordingsPage />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: /delete recording nightly capture/i }));
    fireEvent.click(screen.getByRole("button", { name: "Delete recording" }));

    await waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith(
        expect.objectContaining({
          tone: "error",
          title: "delete of rec-1 is blocked by existing dependents",
        }),
      ),
    );
  });
});
