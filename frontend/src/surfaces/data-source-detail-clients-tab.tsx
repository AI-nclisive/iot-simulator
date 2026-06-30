import { useLiveClients } from "../shell/use-live-clients";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import type { DataSourceRow } from "./mock-data-sources";

function connectionTone(connected: boolean): StatusTone {
  return connected ? "accent" : "neutral";
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString("en-GB", { hour12: false });
}

export function DataSourceDetailClientsTab({ source }: { source: DataSourceRow }) {
  const isLive = source.status === "Active";
  const { rows: clients } = useLiveClients(source.id, isLive);

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
                {(["Client", "State", "Connected since", "Disconnected"] as const).map(
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
                <tr key={client.clientId} className="border-t border-shell-line">
                  <td className="px-4 py-3 align-top font-mono text-sm text-shell-ink">
                    {client.clientId}
                  </td>
                  <td className="px-4 py-3 align-top">
                    <StatusBadge
                      label={client.connected ? "Connected" : "Disconnected"}
                      tone={connectionTone(client.connected)}
                    />
                  </td>
                  <td className="px-4 py-3 align-top text-sm text-shell-ink">
                    {formatTime(client.connectedAt)}
                  </td>
                  <td className="px-4 py-3 align-top text-sm text-shell-ink">
                    {client.disconnectedAt ? formatTime(client.disconnectedAt) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {source.status === "Stopped" ? (
        <p className="text-sm text-shell-muted">
          This source is stopped. Live client tracking resumes when it runs again.
        </p>
      ) : null}
    </div>
  );
}
