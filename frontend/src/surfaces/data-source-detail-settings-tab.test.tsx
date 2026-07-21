/**
 * Tests for DataSourceDetailSettingsTab (UI-137)
 *
 * Covers:
 * - Saved badge appears only after updateSourceConfiguration resolves (async sequencing fix)
 */

import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DataSourceDetailSettingsTab } from "./data-source-detail-settings-tab";
import type { DataSourceRow } from "../shell/data-sources-store";

const { mockUpdateSourceConfiguration } = vi.hoisted(() => ({
  mockUpdateSourceConfiguration: vi.fn(),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      updateSourceConfiguration: mockUpdateSourceConfiguration,
      applyRescan: vi.fn(),
    }),
}));

const source: DataSourceRow = {
  id: "src-01",
  name: "Line A",
  protocol: "OPC UA",
  endpoint: "opc.tcp://line-a.local:4840",
  parameterCount: 100,
  status: "Stopped",
  health: "Healthy",
};

const scanSource: DataSourceRow = {
  ...source,
  basis: "SCAN",
  realDeviceEndpoint: "opc.tcp://real-device:4840",
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("DataSourceDetailSettingsTab — async save badge sequencing (UI-137)", () => {
  it("does not show Saved badge until updateSourceConfiguration resolves", async () => {
    let resolve!: () => void;
    mockUpdateSourceConfiguration.mockReturnValue(
      new Promise<void>((r) => { resolve = r; }),
    );

    render(<DataSourceDetailSettingsTab source={source} />);

    const nameInput = screen.getByLabelText("Source name") as HTMLInputElement;
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "New Name");

    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    // promise is still pending — Saved badge must not be visible yet
    expect(screen.queryByText("Saved")).toBeNull();

    await act(async () => { resolve(); });

    await waitFor(() => expect(screen.getByText("Saved")).toBeTruthy());
  });

  it("shows Saved badge after successful save", async () => {
    mockUpdateSourceConfiguration.mockResolvedValue(undefined);

    render(<DataSourceDetailSettingsTab source={source} />);

    const nameInput = screen.getByLabelText("Source name") as HTMLInputElement;
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "Updated Name");

    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(screen.getByText("Saved")).toBeTruthy());
  });
});

describe("DataSourceDetailSettingsTab — Rescan panel visibility", () => {
  it("shows the Rescan panel for a SCAN-basis source", () => {
    render(<DataSourceDetailSettingsTab source={scanSource} />);
    expect(screen.getByRole("button", { name: /Rescan tags/i })).toBeTruthy();
  });

  it("does not show the Rescan panel for a non-SCAN-basis source", () => {
    render(<DataSourceDetailSettingsTab source={source} />);
    expect(screen.queryByRole("button", { name: /Rescan tags/i })).toBeNull();
  });
});
