/**
 * Tests for RecordingFlowPage — no schema (UI-465)
 *
 * Covers:
 * - Start recording button disabled when parameterCount === 0
 * - SharedStatePanel shown with SCAN-specific message when basis is SCAN
 * - SharedStatePanel shown with MANUAL-specific message when basis is not SCAN
 * - ApiError.detail surfaced in toast message on start failure
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { LiveValuesResult } from "../shell/use-live-values";

class StubApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly detail: string | undefined,
  ) {
    super(message);
    this.name = "ApiError";
    Object.setPrototypeOf(this, StubApiError.prototype);
  }
}

const { mockUseLiveValues, mockApiFetch } = vi.hoisted(() => ({
  mockUseLiveValues: vi.fn((): LiveValuesResult => ({ rows: [], status: "connecting" })),
  mockApiFetch: vi.fn(),
}));

vi.mock("../shell/use-live-values", () => ({ useLiveValues: mockUseLiveValues }));

vi.mock("../api", () => ({
  apiFetch: mockApiFetch,
  ApiError: StubApiError,
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => vi.fn() };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ currentProjectId: "proj-1", accessMode: "local", sharedRole: "admin" }),
}));

const mockPush = vi.fn();
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: mockPush }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ appendRecording: vi.fn() }),
}));

const { dataSourcesState } = vi.hoisted(() => ({
  dataSourcesState: {
    dataSources: [
      {
        id: "src-no-schema",
        name: "Press Line A",
        protocol: "OPC UA",
        realDeviceEndpoint: "opc.tcp://device:4840",
        parameterCount: 0,
        basis: "SCAN",
      },
    ],
  },
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector(dataSourcesState as unknown as Record<string, unknown>),
}));

let RecordingFlowPage: typeof import("./recording-flow-page").RecordingFlowPage;
beforeAll(async () => {
  ({ RecordingFlowPage } = await import("./recording-flow-page"));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderNoSchemaPage(sourceId = "src-no-schema") {
  return render(
    <MemoryRouter initialEntries={[`/data-sources/${sourceId}/record`]}>
      <Routes>
        <Route path="/data-sources/:sourceId/record" element={<RecordingFlowPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RecordingFlowPage — no schema (UI-465)", () => {
  it("disables Start recording button when parameterCount is 0", () => {
    renderNoSchemaPage();
    const btn = screen.getByRole("button", { name: /start recording/i }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("shows no-schema SharedStatePanel with SCAN-specific message", () => {
    renderNoSchemaPage();
    expect(screen.getByText(/recreate the source/i)).toBeTruthy();
  });

  it("shows Schema tab message for non-SCAN basis", () => {
    dataSourcesState.dataSources = [
      { ...dataSourcesState.dataSources[0], id: "src-manual", basis: "MANUAL" },
    ];
    renderNoSchemaPage("src-manual");
    expect(screen.getByText(/Schema tab/i)).toBeTruthy();
    dataSourcesState.dataSources = [
      { ...dataSourcesState.dataSources[0], id: "src-no-schema", basis: "SCAN" },
    ];
  });

  it("surfaces ApiError.detail in the toast message on start failure", async () => {
    dataSourcesState.dataSources = [
      { ...dataSourcesState.dataSources[0], parameterCount: 5 },
    ];
    mockApiFetch.mockRejectedValueOnce(
      new StubApiError("Bad Request", 400, "schema has no variables to capture"),
    );

    renderNoSchemaPage();

    await userEvent.click(screen.getByRole("button", { name: /start recording/i }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(
        expect.objectContaining({
          tone: "error",
          title: "Could not start recording",
          message: "schema has no variables to capture",
        }),
      );
    });

    dataSourcesState.dataSources = [
      { ...dataSourcesState.dataSources[0], parameterCount: 0 },
    ];
  });
});
