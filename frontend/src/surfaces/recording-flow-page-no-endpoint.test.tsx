/**
 * Tests for RecordingFlowPage — no real device endpoint (UI-138)
 *
 * Covers:
 * - Start recording button is disabled and SharedStatePanel shown when source has no endpoint
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { LiveValuesResult } from "../shell/use-live-values";

const { mockUseLiveValues, mockApiFetch } = vi.hoisted(() => ({
  mockUseLiveValues: vi.fn((): LiveValuesResult => ({ rows: [], status: "connecting" })),
  mockApiFetch: vi.fn(),
}));

vi.mock("../shell/use-live-values", () => ({ useLiveValues: mockUseLiveValues }));
vi.mock("../api", () => ({ apiFetch: mockApiFetch }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => vi.fn() };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ currentProjectId: "proj-1", accessMode: "local", sharedRole: "admin" }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      dataSources: [
        {
          id: "src-no-ep",
          name: "Synthetic Source",
          protocol: "OPC UA",
          endpoint: "",
          parameterCount: 0,
        },
      ],
    }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ appendRecording: vi.fn() }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: vi.fn() }),
}));

let RecordingFlowPage: typeof import("./recording-flow-page").RecordingFlowPage;
beforeAll(async () => {
  ({ RecordingFlowPage } = await import("./recording-flow-page"));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("RecordingFlowPage — no real device endpoint (UI-138)", () => {
  function renderNoEndpointPage() {
    return render(
      <MemoryRouter initialEntries={["/data-sources/src-no-ep/record"]}>
        <Routes>
          <Route path="/data-sources/:sourceId/record" element={<RecordingFlowPage />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("disables Start recording button when source has no real device endpoint", () => {
    renderNoEndpointPage();
    const btn = screen.getByRole("button", { name: /start recording/i }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("shows no-endpoint SharedStatePanel when source has no real device endpoint", () => {
    renderNoEndpointPage();
    expect(screen.getByText("No real device endpoint configured.")).toBeTruthy();
  });
});
