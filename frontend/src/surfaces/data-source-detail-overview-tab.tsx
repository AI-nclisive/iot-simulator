import { Link } from "react-router-dom";
import type { DataSourceRow } from "./mock-data-sources";

function runtimeBehaviorText(source: DataSourceRow) {
  if (source.process === "Replay") {
    return "Replay is active. The Values tab reflects current runtime output, while saved recordings remain separate artifacts.";
  }

  if (source.process === "Recording") {
    return "Recording is active. The Values tab shows current runtime values while the capture is being assembled separately.";
  }

  if (source.status === "Active") {
    return "The source is active and serving runtime values. Current output stays separate from saved recordings and evidence.";
  }

  return "The source is stopped. Last-known values may still appear as stale until the next runtime start.";
}

function SummaryBlock({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <p className="mt-3 text-sm font-medium text-shell-ink">{value}</p>
    </div>
  );
}

export function DataSourceDetailOverviewTab({
  source,
}: {
  source: DataSourceRow;
}) {
  return (
    <div className="space-y-5">
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <SummaryBlock label="Status" value={source.status} />
        <SummaryBlock label="Health" value={source.health} />
        <SummaryBlock label="Parameters" value={source.parameterCount.toLocaleString()} />
        <SummaryBlock label="Clients" value={source.clients} />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div>
          <p className="text-sm font-medium text-shell-ink">Current runtime</p>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-shell-muted">
            {runtimeBehaviorText(source)}
          </p>

          <dl className="mt-5 grid gap-3 text-sm text-shell-muted sm:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Endpoint
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.endpoint}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Active behavior
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.process ?? "Idle"}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Protocol
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.protocol}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Last operator
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.lastOperator}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Next action
          </p>
          <div className="mt-3 flex flex-col items-start gap-2">
            <Link className="shell-text-action" to="?tab=values">
              Open Values
            </Link>
            <Link className="shell-text-action" to="?tab=schema">
              Open Schema
            </Link>
            <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
              Record
            </Link>
            <Link className="shell-text-action" to={`/data-sources/${source.id}/replay`}>
              Replay
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
