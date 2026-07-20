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
            {source.basis === "IMPORT"
              ? source.status === "Active"
                ? "Replaying from recording — clients can connect to the replay endpoint below."
                : "Start this source to begin replaying from the imported recording."
              : source.basis === "SYNTHETIC"
                ? source.status === "Active"
                  ? "The synthetic generator is running and producing values from the variable definitions set at creation. Open Values to see live readings."
                  : "Click Run to start the synthetic generator. Variable definitions and signal patterns are visible in the Schema tab."
                : source.status === "Active"
                  ? "The source is running. Open Schema to review parameters, or Values to see current readings."
                  : "The source is stopped. Start it to enable recording and live values."}
          </p>

          {source.basis === "IMPORT" ? (
            <div className="mt-4 rounded-md border border-shell-line bg-shell-base/40 px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Data basis
              </p>
              <p className="mt-2 text-sm text-shell-ink">
                This simulator replays data from an imported recording. The schema and parameter set are determined by that recording.
              </p>
            </div>
          ) : null}

          <dl className="mt-5 grid gap-3 text-sm text-shell-muted sm:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                {source.basis === "IMPORT" ? "Replay serve URL" : "Simulator serve URL"}
              </dt>
              <dd className="mt-2 break-all font-mono text-sm text-shell-ink">{source.endpoint || "—"}</dd>
            </div>
            {source.basis === "SCAN" ? (
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Real device endpoint
                </dt>
                <dd className="mt-2 break-all font-mono text-sm text-shell-ink">
                  {source.realDeviceEndpoint || "—"}
                </dd>
              </div>
            ) : null}
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Protocol
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{source.protocol}</dd>
            </div>
            {source.basis ? (
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Source type
                </dt>
                <dd className="mt-2 text-sm text-shell-ink">
                  {source.basis === "SCAN" ? "Real device scan"
                    : source.basis === "IMPORT" ? "Prepared data (recording)"
                    : source.basis === "SYNTHETIC" ? "Synthetic generation"
                    : "Manual schema"}
                </dd>
              </div>
            ) : null}
          </dl>
        </div>

        <div className="rounded-md border border-shell-line bg-white px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
            Next action
          </p>
          <div className="mt-3 flex flex-col items-start gap-2">
            {source.basis !== "IMPORT" && source.basis !== "SYNTHETIC" ? (
              <Link className="shell-text-action" to={`/data-sources/${source.id}/record`}>
                Record
              </Link>
            ) : null}
            {source.basis === "IMPORT" ? (
              <Link className="shell-text-action" to={`/data-sources/${source.id}/replay`}>
                Replay recording
              </Link>
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
