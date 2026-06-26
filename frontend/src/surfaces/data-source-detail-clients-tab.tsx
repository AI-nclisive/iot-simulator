import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import { getClientsForSource } from "./mock-source-clients";
import type { DataSourceRow } from "./mock-data-sources";

function connectionTone(state: "Connected" | "Connecting" | "Disconnected"): StatusTone {
  if (state === "Connected") return "accent";
  if (state === "Connecting") return "warning";
  return "neutral";
}

export function DataSourceDetailClientsTab({ source }: { source: DataSourceRow }) {
  const clients = getClientsForSource(source.id);

  if (clients.length === 0) {
    return (
      <SharedStatePanel
        message="No clients are connected to this source right now. Clients appear here when they connect to the endpoint."
        state="empty"
        title="No clients connected."
      />
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-shell-muted">
        {clients.length} client{clients.length === 1 ? "" : "s"} tracked for this source.
      </p>

      <div className="overflow-hidden rounded-md border border-shell-line bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full border-collapse">
            <thead className="bg-shell-base/65">
              <tr>
                {(["Remote address", "State", "Connected since", "Reads", "Last read"] as const).map(
                  (col) => (
                    <th
                      key={col}
                      scope="col"
                      className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted"
                    >
                      {col}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody>
              {clients.map((client) => (
                <tr key={client.id} className="border-t border-shell-line">
                  <td className="px-4 py-3 align-top font-mono text-sm text-shell-ink">
                    {client.remoteAddress}
                  </td>
                  <td className="px-4 py-3 align-top">
                    <StatusBadge label={client.state} tone={connectionTone(client.state)} />
                  </td>
                  <td className="px-4 py-3 align-top text-sm text-shell-ink">
                    {client.connectedSince}
                  </td>
                  <td className="px-4 py-3 align-top text-sm text-shell-ink">
                    {client.readCount.toLocaleString()}
                  </td>
                  <td className="px-4 py-3 align-top text-sm text-shell-ink">
                    {client.lastReadAt}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {source.status === "Stopped" ? (
        <p className="text-sm text-shell-muted">
          This source is stopped. Clients shown above may have lost their connection.
        </p>
      ) : null}
    </div>
  );
}
