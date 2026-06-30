import { Link } from "react-router-dom";
import type { DataSourceRow } from "../shell/data-sources-store";

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
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <SummaryBlock label="State" value={stateLabel(source)} />
        <SummaryBlock label="Health" value={source.health} />
        <SummaryBlock
          label="Parameters"
          value={`${source.parameterCount.toLocaleString()} in Schema`}
        />
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
          <p className="text-sm font-medium text-shell-ink">Source</p>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-shell-muted">
            {source.status === "Active"
              ? "The source is running. Open Schema to review parameters, or Values to see current readings."
              : "The source is stopped. Start it to enable recording, replay, and live values."}
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
                Protocol
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.protocol}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Next action
          </p>
          <div className="mt-3 flex flex-col items-start gap-2">
            {source.status === "Active" ? (
              <>
                <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
                  Start recording
                </Link>
                <Link className="shell-text-action" to={`/data-sources/${source.id}/replay`}>
                  Set up replay
                </Link>
              </>
            ) : (
              <p className="text-sm text-shell-muted">Start the source to begin recording or replay.</p>
            )}
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
