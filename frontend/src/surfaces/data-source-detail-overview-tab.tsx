import { Link } from "react-router-dom";
import type { DataSourceRow } from "./mock-data-sources";

function runtimeBehaviorText(source: DataSourceRow) {
  if (source.process === "Replay") {
    return "Replay process is running. The Values tab reflects current output, while saved recordings remain separate artifacts.";
  }

  if (source.process === "Recording") {
    return "Recording process is running. The Values tab shows current values while the capture is being assembled separately.";
  }

  if (source.status === "Active") {
    return "Run means the source is available. No recording or replay process is running.";
  }

  return "Off. Start the source before starting recording or replay.";
}

function stateLabel(source: DataSourceRow) {
  return source.status === "Active" ? "Run" : "Off";
}

function healthGuidanceText(source: DataSourceRow) {
  if (source.health === "Healthy") {
    return null;
  }

  if (source.health === "Warning") {
    return "Review Events for reconnects, slow responses, client backpressure, or protocol warnings before using this source for a new capture.";
  }

  return "Open Events first, then check Settings for endpoint, protocol, or credential issues before starting this source again.";
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
  const healthGuidance = healthGuidanceText(source);

  return (
    <div className="space-y-5">
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <SummaryBlock label="State" value={stateLabel(source)} />
        <SummaryBlock label="Health" value={source.health} />
        <SummaryBlock
          label="Parameters"
          value={`${source.parameterCount.toLocaleString()} in Schema`}
        />
        <SummaryBlock label="Clients" value={source.clients} />
      </div>

      {healthGuidance ? (
        <div className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Health follow-up
          </p>
          <p className="mt-3 text-sm leading-6 text-shell-muted">{healthGuidance}</p>
        </div>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div>
          <p className="text-sm font-medium text-shell-ink">
            {source.process ? "Current process" : "Source"}
          </p>
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
            {source.process ? (
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Process
                </dt>
                <dd className="mt-2 text-sm text-shell-ink">{source.process}</dd>
              </div>
            ) : null}
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
            {source.process === "Recording" ? (
              <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
                Open recording
              </Link>
            ) : null}
            {source.process === "Replay" ? (
              <Link className="shell-text-action" to={`/data-sources/${source.id}/replay`}>
                Open replay
              </Link>
            ) : null}
            {source.status === "Active" && !source.process ? (
              <>
                <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
                  Start recording
                </Link>
                <Link className="shell-text-action" to={`/data-sources/${source.id}/replay`}>
                  Set up replay
                </Link>
              </>
            ) : null}
            <Link className="shell-text-action" to="?tab=values">
              Open Values
            </Link>
            <Link className="shell-text-action" to="?tab=schema">
              Open Schema
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
