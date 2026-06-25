import { Link, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import {
  evidenceDeliveryTone,
  evidenceExportStateTone,
  evidenceIssueTone,
  evidenceStatusTone,
  evidenceTimelineTone,
  isEvidenceExportAvailable,
} from "./evidence-detail-helpers";
import { evidenceArtifacts } from "./mock-evidence";

function SummaryCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-md border border-shell-line bg-white px-4 py-4">
      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
        {label}
      </p>
      <p className="mt-3 text-sm font-medium text-shell-ink">{value}</p>
    </div>
  );
}

export function EvidenceDetailPage() {
  const { evidenceId } = useParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);
  const evidence = evidenceArtifacts.find((a) => a.id === evidenceId);

  if (!evidence) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Return to the Evidence list and choose a valid artifact."
            state="error"
            title="This evidence artifact could not be found."
          />
          <div className="mt-4">
            <Link className="shell-text-action" to="/evidence">
              Back to evidence
            </Link>
          </div>
        </section>
      </div>
    );
  }

  const exportAvailable = isEvidenceExportAvailable(evidence);

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-4xl">
            <Link className="shell-text-action -ml-2" to="/evidence">
              Back to evidence
            </Link>
            <h2 className="mt-2 text-2xl font-semibold text-shell-ink">{evidence.title}</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">{evidence.completeness}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={evidence.status} tone={evidenceStatusTone(evidence.status)} />
            <StatusBadge
              label={evidence.exportState}
              tone={evidenceExportStateTone(evidence.exportState)}
            />
          </div>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <SummaryCard label="Run origin" value={`${evidence.runType} · ${evidence.runId}`} />
          <SummaryCard label="Initiator" value={evidence.initiator} />
          <SummaryCard label="Duration" value={evidence.duration} />
          <SummaryCard label="Values" value={evidence.valueCount.toLocaleString()} />
          <SummaryCard label="Size" value={evidence.sizeLabel} />
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {access.isAdmin && exportAvailable ? (
            <button className="shell-action" disabled type="button">
              Export evidence
            </button>
          ) : (
            <button className="shell-action" disabled type="button">
              {exportAvailable ? "Export evidence" : "Export not ready"}
            </button>
          )}
          {evidence.sourcePath ? (
            <Link className="shell-text-action" to={evidence.sourcePath}>
              Open source
            </Link>
          ) : null}
          {evidence.scenarioName ? (
            <Link className="shell-text-action" to="/scenarios">
              Open scenario
            </Link>
          ) : null}
        </div>
      </section>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Timeline</h3>
            <StatusBadge label={evidence.completedAt ?? "Still capturing"} />
          </div>

          <ol className="mt-5 space-y-3">
            {evidence.timeline.map((event) => (
              <li
                key={event.id}
                className="rounded-md border border-shell-line bg-white px-4 py-4"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge
                    label={event.time}
                    tone={evidenceTimelineTone(event.tone)}
                  />
                  <p className="text-sm font-medium text-shell-ink">{event.title}</p>
                </div>
                <p className="mt-2 text-sm leading-6 text-shell-muted">{event.description}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="shell-panel px-5 py-5">
          <h3 className="text-base font-semibold text-shell-ink">Origin</h3>
          <dl className="mt-5 grid gap-3">
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Project
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{evidence.projectName}</dd>
            </div>
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Source or scenario
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">
                {evidence.scenarioName ?? evidence.sourceName}
              </dd>
            </div>
            <div className="rounded-md border border-shell-line bg-white px-4 py-3">
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Export formats
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{evidence.formats.join(", ")}</dd>
            </div>
          </dl>
        </section>
      </div>

      <div className="grid gap-3 xl:grid-cols-2">
        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Clients</h3>
            <StatusBadge
              label={`${evidence.clientCount} client${evidence.clientCount === 1 ? "" : "s"}`}
            />
          </div>

          {evidence.clients.length > 0 ? (
            <div className="mt-5 space-y-3">
              {evidence.clients.map((client) => (
                <div
                  key={client.id}
                  className="rounded-md border border-shell-line bg-white px-4 py-4"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium text-shell-ink">{client.name}</p>
                    <StatusBadge
                      label={client.delivery}
                      tone={evidenceDeliveryTone(client.delivery)}
                    />
                  </div>
                  <p className="mt-2 text-sm text-shell-muted">{client.protocol}</p>
                </div>
              ))}
            </div>
          ) : (
            <SharedStatePanel
              message="No client delivery was captured for this evidence artifact."
              state={
                evidence.status === "Partial" || evidence.status === "Export failed"
                  ? "warning"
                  : "empty"
              }
              title="No clients captured."
            />
          )}
        </section>

        <section className="shell-panel px-5 py-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-base font-semibold text-shell-ink">Faults and errors</h3>
            <StatusBadge
              label={`${evidence.issues.length} issue${evidence.issues.length === 1 ? "" : "s"}`}
            />
          </div>

          {evidence.issues.length > 0 ? (
            <div className="mt-5 space-y-3">
              {evidence.issues.map((issue) => (
                <div
                  key={issue.id}
                  className="rounded-md border border-shell-line bg-white px-4 py-4"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge
                      label={issue.severity}
                      tone={evidenceIssueTone(issue.severity)}
                    />
                    <p className="text-sm font-medium text-shell-ink">{issue.label}</p>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-shell-muted">{issue.description}</p>
                </div>
              ))}
            </div>
          ) : (
            <SharedStatePanel
              message="No faults or errors are attached to this evidence artifact."
              state="empty"
              title="No issues captured."
            />
          )}
        </section>
      </div>
    </div>
  );
}
