import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { DataSourceDetailClientsTab } from "./data-source-detail-clients-tab";
import { DataSourceDetailEventsTab } from "./data-source-detail-events-tab";
import { DataSourceDetailOverviewTab } from "./data-source-detail-overview-tab";
import { DataSourceDetailSettingsTab } from "./data-source-detail-settings-tab";
import { DataSourceDetailValuesTab } from "./data-source-detail-values-tab";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { stopActionCopy } from "./source-action-copy";

type DetailTabId =
  | "overview"
  | "schema"
  | "values"
  | "clients"
  | "events"
  | "settings";

const detailTabs: { id: DetailTabId; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "schema", label: "Schema" },
  { id: "values", label: "Values" },
  { id: "clients", label: "Clients" },
  { id: "events", label: "Events" },
  { id: "settings", label: "Settings" },
];

function stateMeta(source: {
  status: "Active" | "Stopped";
}) {
  if (source.status === "Active") {
    return { label: "Run", tone: "accent" as const };
  }

  return { label: "Off", tone: "neutral" as const };
}

function healthTone(health: "Healthy" | "Warning" | "Error") {
  if (health === "Error") {
    return "danger";
  }

  if (health === "Warning") {
    return "warning";
  }

  return "accent";
}

function currentTabId(searchValue: string | null): DetailTabId {
  return detailTabs.some((tab) => tab.id === searchValue)
    ? (searchValue as DetailTabId)
    : "overview";
}

function healthDiagnosticCopy(source: {
  endpoint: string;
  health: "Healthy" | "Warning" | "Error";
  process?: "Recording" | "Replay";
  clients: number;
}) {
  if (source.health === "Healthy") {
    return null;
  }

  if (source.health === "Warning") {
    return {
      checks: [
        `Check recent reconnects or slow responses from ${source.endpoint}.`,
        source.clients > 0
          ? "Review connected clients for partial delivery or backpressure."
          : "No clients are connected; confirm whether that is expected.",
        source.process
          ? `Review the active ${source.process.toLowerCase()} process before changing source settings.`
          : "Open Settings if the endpoint or protocol setup needs correction.",
      ],
      message:
        "The source is still usable, but recent runtime events need review before relying on its values.",
      title: "Health warning needs review.",
    };
  }

  return {
    checks: [
      `Verify that ${source.endpoint} is reachable from this environment.`,
      "Check credentials, protocol settings, and endpoint address in Settings.",
      "Review connected clients after the source returns to Run.",
    ],
    message:
      "The source is not healthy enough for normal operation. Fix the connection or configuration before recording or replaying from it.",
    title: "Health error requires action.",
  };
}

export function DataSourceDetailPreviewPage() {
  const { sourceId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const startDataSource = useDataSourcesStore((state) => state.startDataSource);
  const stopDataSource = useDataSourcesStore((state) => state.stopDataSource);
  const access = resolveAccess(accessMode, sharedRole);
  const activeTab = currentTabId(searchParams.get("tab"));
  const [stopConfirmationOpen, setStopConfirmationOpen] = useState(false);

  if (!source) {
    return (
      <div className="flex h-full flex-col gap-3">
        <section className="shell-panel px-5 py-5">
          <SharedStatePanel
            message="Return to the project source list and choose a valid source."
            state="error"
            title="This source could not be found."
          />
          <div className="mt-4">
            <Link className="shell-text-action" to="/data-sources">
              Back to sources
            </Link>
          </div>
        </section>
      </div>
    );
  }

  const activeSource = source;
  const healthDiagnostic = healthDiagnosticCopy(activeSource);
  const activeState = stateMeta(activeSource);
  const sourceStarted = activeSource.status === "Active";
  const recordingActive = activeSource.process === "Recording";
  const replayActive = activeSource.process === "Replay";
  const sourceControlAction =
    sourceStarted && access.canStopSource
      ? {
          label: "Stop source",
          onClick: () => setStopConfirmationOpen(true),
        }
      : !sourceStarted && access.canStartStoppedSource
        ? {
            label: "Start source",
            onClick: () => startDataSource(activeSource.id),
          }
        : null;

  const stopConfirmationModel = useMemo(() => {
    if (!sourceStarted) {
      return null;
    }

    const runtimeImpact =
      activeSource.process === "Recording"
        ? "Recording stops immediately and the current capture ends on this source."
        : activeSource.process === "Replay"
          ? "Replay stops immediately for this source."
          : "The source stops serving values until someone starts it again.";

    const copy = stopActionCopy;

    return {
      confirmLabel: copy.confirmLabel,
      impacts: [
        { label: "Endpoint", value: activeSource.endpoint },
        { label: "Runtime impact", value: runtimeImpact },
        {
          label: "Connected clients",
          value:
            activeSource.clients > 0
              ? `${activeSource.clients} connected client${activeSource.clients === 1 ? "" : "s"} may notice the interruption.`
              : "No connected clients are currently shown for this source.",
        },
      ],
      message: copy.message,
      objectLabel: `${activeSource.name} (${activeSource.protocol})`,
      reversibilityLabel:
        "This action is reversible. The source can be started again later.",
      title: copy.title,
      tone: "warning" as const,
    };
  }, [activeSource, sourceStarted]);

  function setActiveTab(tabId: DetailTabId) {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", tabId);
    setSearchParams(nextParams, { replace: true });
  }

  function renderTabContent() {
    if (activeTab === "overview") {
      return <DataSourceDetailOverviewTab source={activeSource} />;
    }

    if (activeTab === "schema") {
      return (
        <SharedStatePanel
          message={`Parameters for this source are managed in Schema. The full editor will open here for ${activeSource.parameterCount.toLocaleString()} parameters, with deeper editor behavior added in the later schema task.`}
          state="empty"
          title="Schema manages source parameters."
        />
      );
    }

    if (activeTab === "values") {
      return <DataSourceDetailValuesTab source={activeSource} />;
    }

    if (activeTab === "clients") {
      return <DataSourceDetailClientsTab source={activeSource} />;
    }

    if (activeTab === "events") {
      return <DataSourceDetailEventsTab source={activeSource} />;
    }

    return <DataSourceDetailSettingsTab source={activeSource} />;
  }

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <h2 className="text-2xl font-semibold text-shell-ink">{activeSource.name}</h2>
            <p className="mt-2 text-sm leading-6 text-shell-muted">{activeSource.endpoint}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={activeState.label} tone={activeState.tone} />
            <StatusBadge label={activeSource.health} tone={healthTone(activeSource.health)} />
          </div>
        </div>

        <dl className="mt-5 grid gap-3 text-sm text-shell-muted sm:grid-cols-2 xl:grid-cols-5">
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Protocol
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.protocol}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Parameters
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">
              {activeSource.parameterCount.toLocaleString()} in Schema
            </dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Clients
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.clients}</dd>
          </div>
          {activeSource.process ? (
            <div>
              <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                Process
              </dt>
              <dd className="mt-2 text-sm text-shell-ink">{activeSource.process}</dd>
            </div>
          ) : null}
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Last operator
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.lastOperator}</dd>
          </div>
        </dl>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {recordingActive ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/record`}>
              Open recording
            </Link>
          ) : access.canRecordSource && sourceStarted && !replayActive ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/record`}>
              Start recording
            </Link>
          ) : (
            <button className="shell-action" disabled type="button">
              Start recording
            </button>
          )}
          {replayActive ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/replay`}>
              Open replay
            </Link>
          ) : access.canConfigureReplay && sourceStarted && !recordingActive ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/replay`}>
              Set up replay
            </Link>
          ) : (
            <button className="shell-action" disabled type="button">
              Set up replay
            </button>
          )}
          {sourceControlAction ? (
            <button className="shell-action" type="button" onClick={sourceControlAction.onClick}>
              {sourceControlAction.label}
            </button>
          ) : null}
          <Link className="shell-text-action" to="/data-sources">
            Back to sources
          </Link>
        </div>

        {access.isSharedUser ? (
          <p className="mt-4 text-sm leading-6 text-shell-muted">
            Shared User can inspect this source, but configuration and runtime setup
            stay read-only.
          </p>
        ) : null}
      </section>

      <section className="shell-panel px-5 py-5">
        <div
          aria-label="Data source detail sections"
          className="flex flex-wrap gap-2"
          role="tablist"
        >
          {detailTabs.map((tab) => {
            const isActive = tab.id === activeTab;

            return (
              <button
                key={tab.id}
                aria-selected={isActive}
                className={`shell-action ${isActive ? "border-shell-accent text-shell-accent" : ""}`}
                role="tab"
                type="button"
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        <div className="mt-5" aria-live="polite" role="tabpanel">
          {renderTabContent()}
        </div>
      </section>

      {stopConfirmationModel ? (
        <ConfirmationDialog
          confirmLabel={stopConfirmationModel.confirmLabel}
          impacts={stopConfirmationModel.impacts}
          message={stopConfirmationModel.message}
          objectLabel={stopConfirmationModel.objectLabel}
          open={stopConfirmationOpen}
          reversibilityLabel={stopConfirmationModel.reversibilityLabel}
          title={stopConfirmationModel.title}
          tone={stopConfirmationModel.tone}
          onClose={() => setStopConfirmationOpen(false)}
          onConfirm={() => {
            stopDataSource(activeSource.id);
            setStopConfirmationOpen(false);
          }}
        />
      ) : null}
    </div>
  );
}
