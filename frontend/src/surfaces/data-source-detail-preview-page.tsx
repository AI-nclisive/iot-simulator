import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { DataSourceDetailOverviewTab } from "./data-source-detail-overview-tab";
import { DataSourceDetailSettingsTab } from "./data-source-detail-settings-tab";
import { DataSourceDetailValuesTab } from "./data-source-detail-values-tab";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";

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

function statusTone(status: "Active" | "Stopped") {
  return status === "Active" ? "accent" : "neutral";
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

  const stopConfirmationModel = useMemo(() => {
    if (activeSource.status !== "Active") {
      return null;
    }

    const runtimeImpact =
      activeSource.process === "Recording"
        ? "Recording stops immediately and the current capture ends on this source."
        : activeSource.process === "Replay"
          ? "Replay stops immediately for this source."
          : "The source stops serving simulated values until someone starts it again.";

    return {
      confirmLabel: "Stop source",
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
      message:
        "Stopping a source interrupts its current runtime behavior for everyone using this project.",
      objectLabel: `${activeSource.name} (${activeSource.protocol})`,
      reversibilityLabel:
        "This action is reversible. The source can be started again later.",
      title: "Stop this source?",
      tone: "warning" as const,
    };
  }, [activeSource]);

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
          message={`Schema work will open here for ${activeSource.parameterCount.toLocaleString()} parameters, with the deeper editor behavior added in the later schema task.`}
          state="empty"
          title="Schema surface is attached to the detail shell."
        />
      );
    }

    if (activeTab === "values") {
      return <DataSourceDetailValuesTab source={activeSource} />;
    }

    if (activeTab === "clients") {
      return (
        <SharedStatePanel
          message="Client connection details will live here without sending users back to the source list or Overview."
          state="empty"
          title="Clients tab is attached to the detail shell."
        />
      );
    }

    if (activeTab === "events") {
      return (
        <SharedStatePanel
          message="Runtime event history for this source will open here as part of the full detail surface."
          state="empty"
          title="Events tab is attached to the detail shell."
        />
      );
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
            <StatusBadge label={activeSource.status} tone={statusTone(activeSource.status)} />
            <StatusBadge label={activeSource.health} tone={healthTone(activeSource.health)} />
            {activeSource.process ? (
              <StatusBadge
                label={activeSource.process}
                tone={activeSource.process === "Recording" ? "warning" : "accent"}
              />
            ) : null}
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
              {activeSource.parameterCount.toLocaleString()}
            </dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Clients
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.clients}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Current process
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.process ?? "Idle"}</dd>
          </div>
          <div>
            <dt className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
              Last operator
            </dt>
            <dd className="mt-2 text-sm text-shell-ink">{activeSource.lastOperator}</dd>
          </div>
        </dl>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {activeSource.status === "Stopped" && access.canStartStoppedSource ? (
            <button
              className="shell-action"
              type="button"
              onClick={() => startDataSource(activeSource.id)}
            >
              Start source
            </button>
          ) : null}
          {activeSource.status === "Active" && access.canStopSource ? (
            <button
              className="shell-action"
              type="button"
              onClick={() => setStopConfirmationOpen(true)}
            >
              Stop source
            </button>
          ) : null}
          {access.canRecordSource ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/record`}>
              Record
            </Link>
          ) : (
            <button className="shell-action" disabled type="button">
              Record
            </button>
          )}
          {access.canConfigureReplay ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/replay`}>
              Replay
            </Link>
          ) : (
            <button className="shell-action" disabled type="button">
              Replay
            </button>
          )}
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
