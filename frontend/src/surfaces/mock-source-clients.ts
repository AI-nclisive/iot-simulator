export type ClientConnectionState = "Connected" | "Connecting" | "Disconnected";

export type SourceClient = {
  id: string;
  remoteAddress: string;
  state: ClientConnectionState;
  connectedSince: string;
  readCount: number;
  lastReadAt: string;
};

const clientsBySource: Record<string, SourceClient[]> = {
  "src-01": [
    {
      id: "cli-01-a",
      remoteAddress: "192.168.1.42:49231",
      state: "Connected",
      connectedSince: "14:12",
      readCount: 18420,
      lastReadAt: "14:32",
    },
    {
      id: "cli-01-b",
      remoteAddress: "192.168.1.55:49802",
      state: "Connected",
      connectedSince: "14:18",
      readCount: 9340,
      lastReadAt: "14:32",
    },
    {
      id: "cli-01-c",
      remoteAddress: "192.168.1.77:50011",
      state: "Connecting",
      connectedSince: "14:31",
      readCount: 0,
      lastReadAt: "—",
    },
  ],
  "src-02": [
    {
      id: "cli-02-a",
      remoteAddress: "10.0.0.14:38471",
      state: "Connected",
      connectedSince: "13:45",
      readCount: 42100,
      lastReadAt: "14:31",
    },
    {
      id: "cli-02-b",
      remoteAddress: "10.0.0.21:38502",
      state: "Disconnected",
      connectedSince: "13:50",
      readCount: 5000,
      lastReadAt: "14:10",
    },
  ],
  "src-03": [
    {
      id: "cli-03-a",
      remoteAddress: "172.16.0.8:51200",
      state: "Connected",
      connectedSince: "14:00",
      readCount: 71800,
      lastReadAt: "14:32",
    },
  ],
};

export function getClientsForSource(sourceId: string): SourceClient[] {
  return clientsBySource[sourceId] ?? [];
}
