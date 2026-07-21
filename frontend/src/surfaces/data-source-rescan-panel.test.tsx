import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DataSourceRescanPanel } from "./data-source-rescan-panel";
import type { DataSourceRow } from "../shell/data-sources-store";
import type { DiscoveredNodeResponse } from "./create-data-source-wizard-page";

// UnknownNodesList (reused from create-data-source-wizard-page.tsx) uses
// @tanstack/react-virtual, which observes its scroll container's size via
// ResizeObserver — absent in jsdom — to decide which rows are "visible".
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
if (typeof globalThis.ResizeObserver === "undefined") {
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}
for (const prop of ["offsetHeight", "offsetWidth", "clientHeight", "clientWidth"] as const) {
  Object.defineProperty(HTMLElement.prototype, prop, { configurable: true, value: 500 });
}

const { mockApiFetch, mockApplyRescan, mockPush } = vi.hoisted(() => ({
  mockApiFetch: vi.fn(),
  mockApplyRescan: vi.fn(),
  mockPush: vi.fn(),
}));

vi.mock("../api", async () => {
  const actual = await vi.importActual<typeof import("../api")>("../api");
  return { ...actual, apiFetch: mockApiFetch };
});

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ applyRescan: mockApplyRescan }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: { push: typeof mockPush }) => unknown) =>
    selector({ push: mockPush }),
}));

const source: DataSourceRow = {
  id: "src-01",
  name: "Line A",
  protocol: "OPC UA",
  endpoint: "opc.tcp://line-a.local:4840",
  parameterCount: 100,
  status: "Stopped",
  health: "Healthy",
  basis: "SCAN",
  realDeviceEndpoint: "opc.tcp://real-device:4840",
};

const knownNode: DiscoveredNodeResponse = {
  nodeId: "ns=2;s=Temp",
  parentId: null,
  path: "/Temp",
  name: "Temp",
  kind: "Variable",
  dataType: "Float",
  valueRank: 1,
  access: "READ",
  unit: null,
  description: null,
  unknownType: false,
};

const unknownNode: DiscoveredNodeResponse = {
  ...knownNode,
  nodeId: "ns=2;s=X",
  name: "X",
  dataType: null,
  unknownType: true,
};

function makeNodesPage(nodes: DiscoveredNodeResponse[]) {
  return Promise.resolve({ items: nodes, nextCursor: null, limit: 200 });
}

async function advanceIntervalAndFlush(ms: number) {
  const { act } = await import("@testing-library/react");
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("DataSourceRescanPanel", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  });
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("rescans, discovers only known nodes, and applies immediately", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" })) // POST /rescan
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-1",
          status: "OK",
          discoveredSoFar: 1,
          discoveredCount: 1,
          unknownCount: 0,
          message: null,
        }),
      ) // GET /scan/job-1
      .mockImplementationOnce(() => makeNodesPage([knownNode])); // GET /scan/job-1/nodes
    mockApplyRescan.mockResolvedValue(undefined);

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<DataSourceRescanPanel source={source} projectId="proj-1" />);

    await user.click(screen.getByRole("button", { name: /^Rescan tags$/i }));
    await advanceIntervalAndFlush(2000);

    await waitFor(() =>
      expect(
        (screen.getByRole("button", { name: /^Apply rescan$/i }) as HTMLButtonElement).disabled,
      ).toBe(false),
    );
    expect(screen.queryByRole("listitem")).toBeNull(); // no unknown-node rows to resolve

    await user.click(screen.getByRole("button", { name: /^Apply rescan$/i }));

    await waitFor(() => expect(mockApplyRescan).toHaveBeenCalledWith("src-01", "job-1", [], "proj-1"));
    await waitFor(() => expect(screen.getByRole("button", { name: /^Rescan tags$/i })).toBeTruthy());
  });

  it("blocks Apply until an unknown-typed node's data type is resolved", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-2", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-2",
          status: "OK",
          discoveredSoFar: 2,
          discoveredCount: 2,
          unknownCount: 1,
          message: null,
        }),
      )
      .mockImplementationOnce(() => makeNodesPage([knownNode, unknownNode]));

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<DataSourceRescanPanel source={source} projectId="proj-1" />);

    await user.click(screen.getByRole("button", { name: /^Rescan tags$/i }));
    await advanceIntervalAndFlush(2000);

    await waitFor(() => expect(screen.getByRole("listitem")).toBeTruthy());
    expect((screen.getByRole("button", { name: /^Apply rescan$/i }) as HTMLButtonElement).disabled).toBe(
      true,
    );

    const typeSelect = within(screen.getByRole("listitem")).getByRole("combobox");
    await user.selectOptions(typeSelect, "INT32");

    expect((screen.getByRole("button", { name: /^Apply rescan$/i }) as HTMLButtonElement).disabled).toBe(
      false,
    );
  });
});
