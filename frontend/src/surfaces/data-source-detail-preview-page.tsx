import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { useNotificationStore } from "../shell/notification-store";
import { useActiveRuns } from "../shell/use-active-runs";
import { apiFetch } from "../api";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { DataSourceDetailClientsTab } from "./data-source-detail-clients-tab";
import { DataSourceDetailEventsTab } from "./data-source-detail-events-tab";
import { DataSourceDetailOverviewTab } from "./data-source-detail-overview-tab";
import { DataSourceSchemaEditor } from "./data-source-schema-editor";
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

function healthTone(health: string) {
  if (health === "Error") return "danger";
  if (health === "Warning" || health === "Starting") return "warning";
  if (health === "Healthy") return "accent";
  return "neutral";
}

function currentTabId(searchValue: string | null): DetailTabId {
  return detailTabs.some((tab) => tab.id === searchValue)
    ? (searchValue as DetailTabId)
    : "overview";
}

function healthDiagnosticCopy(source: {
  endpoint: string;
  health: "Healthy" | "Warning" | "Error";
}) {
  if (source.health === "Healthy") {
    return null;
  }

  if (source.health === "Warning") {
    return {
      checks: [
        `Check recent reconnects or slow responses from ${source.endpoint}.`,
        "No clients are connected; confirm whether that is expected.",
        "Open Settings if the endpoint or protocol setup needs correction.",
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
  const currentProjectId = useShellStore((state) => state.currentProjectId);
  const source = useDataSourcesStore((state) =>
    state.dataSources.find((row) => row.id === sourceId),
  );
  const stopDataSource = useDataSourcesStore((state) => state.stopDataSource);
  const runSynthetic = useDataSourcesStore((state) => state.runSynthetic);
  const stopSynthetic = useDataSourcesStore((state) => state.stopSynthetic);
  const isLoading = useDataSourcesStore((state) => state.isLoading);
  const loadDataSources = useDataSourcesStore((state) => state.loadDataSources);
  const { runs: activeRuns } = useActiveRuns(currentProjectId);
  const push = useNotificationStore((state) => state.push);
  const access = resolveAccess(accessMode, sharedRole);
  const artifacts = useArtifactsStore((state) => state.artifacts);
  const loadRecordings = useArtifactsStore((state) => state.loadRecordings);
  const fetchedForProjectRef = useRef<string | null>(null);

  useEffect(() => {
    if (currentProjectId && !source && !isLoading && fetchedForProjectRef.current !== currentProjectId) {
      fetchedForProjectRef.current = currentProjectId;
      loadDataSources(currentProjectId);
    }
  }, [currentProjectId, source, isLoading, loadDataSources]);

  useEffect(() => {
    if (currentProjectId && source?.basis === "IMPORT") {
      void loadRecordings(currentProjectId);
    }
  }, [currentProjectId, source?.basis, loadRecordings]);

  const activeTab = currentTabId(searchParams.get("tab"));
  const [stopConfirmationOpen, setStopConfirmationOpen] = useState(false);
  const [schemaUnsaved, setSchemaUnsaved] = useState(false);
  const [pendingTab, setPendingTab] = useState<DetailTabId | null>(null);

  // All hooks above — early returns only after this point
  const sourceStarted = source?.status === "Active";
  const stopConfirmationModel = useMemo(() => {
    if (!source || !sourceStarted) return null;
    const runtimeImpact = "The source stops serving values until someone starts it again.";
    const copy = stopActionCopy;
    return {
      confirmLabel: copy.confirmLabel,
      impacts: [
        { label: "Endpoint", value: source.endpoint },
        { label: "Runtime impact", value: runtimeImpact },
      ],
      message: copy.message,
      objectLabel: `${source.name} (${source.protocol})`,
      reversibilityLabel: "This action is reversible. The source can be started again later.",
      title: copy.title,
      tone: "warning" as const,
    };
  }, [source, sourceStarted]);

  if (!source) {
    if (isLoading) {
      return (
        <div className="flex h-full items-center justify-center">
          <SharedStatePanel message="Loading source…" state="loading" title="" />
        </div>
      );
    }
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

  const importRecordingId: string | null = (() => {
    if (activeSource.basis !== "IMPORT" || !activeSource.runtimeConfig) return null;
    try {
      const cfg = JSON.parse(activeSource.runtimeConfig) as Record<string, unknown>;
      return typeof cfg.importRecordingId === "string" ? cfg.importRecordingId : null;
    } catch {
      return null;
    }
  })();
  const importRecording = importRecordingId
    ? artifacts.find((a) => a.id === importRecordingId) ?? null
    : null;
  const sourceControlAction =
    sourceStarted && access.canStopSource && activeSource.basis !== "SYNTHETIC"
      ? {
          label: "Stop source",
          onClick: () => setStopConfirmationOpen(true),
        }
      : null;
  const activeReplayRun = activeRuns.find(
    (r) =>
      r.relatedSourceId === activeSource.id &&
      r.processType === "Replay" &&
      r.runState === "running",
  ) ?? null;

  async function stopSimulation() {
    if (!activeReplayRun || !currentProjectId) return;
    try {
      await apiFetch(
        `/api/v1/projects/${currentProjectId}/runs/${activeReplayRun.id}/stop`,
        { method: "POST" },
      );
    } catch (err) {
      const title = err instanceof Error ? err.message : "Stop failed";
      push({ tone: "error", title });
    }
  }

  async function runSyntheticSource() {
    if (!currentProjectId) return;
    try {
      await runSynthetic(activeSource.id, currentProjectId);
      setActiveTab("values");
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to start synthetic run";
      push({ tone: "error", title });
    }
  }

  async function stopSyntheticSource() {
    if (!currentProjectId) return;
    try {
      await stopSynthetic(activeSource.id, currentProjectId);
    } catch (err) {
      const title = err instanceof Error ? err.message : "Failed to stop synthetic run";
      push({ tone: "error", title });
    }
  }

  function setActiveTab(tabId: DetailTabId) {
    if (activeTab === "schema" && schemaUnsaved && tabId !== "schema") {
      setPendingTab(tabId);
      return;
    }
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", tabId);
    setSearchParams(nextParams, { replace: true });
  }

  function confirmTabSwitch() {
    if (!pendingTab) return;
    setSchemaUnsaved(false);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", pendingTab);
    setSearchParams(nextParams, { replace: true });
    setPendingTab(null);
  }

  function renderTabContent() {
    if (activeTab === "overview") {
      return <DataSourceDetailOverviewTab source={activeSource} />;
    }

    if (activeTab === "schema") {
      return <DataSourceSchemaEditor projectId={currentProjectId} source={activeSource} onUnsavedChanges={setSchemaUnsaved} />;
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
            {activeSource.basis === "SCAN" && activeSource.realDeviceEndpoint ? (
              <p className="mt-1 text-xs text-shell-muted">
                Real device: <span className="font-mono">{activeSource.realDeviceEndpoint}</span>
              </p>
            ) : null}
            {activeSource.basis === "IMPORT" ? (
              <p className="mt-1 text-xs text-shell-muted">
                Imported recording
                {importRecordingId ? (
                  <>
                    {" — "}
                    <Link
                      className="shell-text-action"
                      to={`/recordings/${importRecordingId}`}
                    >
                      {importRecording?.name ?? `Recording ${importRecordingId.slice(0, 8)}`}
                    </Link>
                  </>
                ) : null}
              </p>
            ) : null}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge label={activeState.label} tone={activeState.tone} />
            <StatusBadge label={activeSource.health} tone={healthTone(activeSource.health)} />
            {activeReplayRun ? (
              <StatusBadge label="Simulating" tone="accent" />
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
              {activeSource.parameterCount.toLocaleString()} in Schema
            </dd>
          </div>
        </dl>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          {activeSource.basis === "SYNTHETIC" && access.canConfigureReplay ? (
            sourceStarted ? (
              <button className="shell-action" type="button" onClick={() => void stopSyntheticSource()}>
                Stop
              </button>
            ) : (
              <button className="shell-action" type="button" onClick={() => void runSyntheticSource()}>
                Run
              </button>
            )
          ) : null}
          {access.canRecordSource && activeSource.basis !== "IMPORT" && activeSource.basis !== "SYNTHETIC" ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/record`}>
              Record
            </Link>
          ) : null}
          {access.canConfigureReplay && activeSource.basis === "IMPORT" ? (
            <Link className="shell-action" to={`/data-sources/${activeSource.id}/replay`}>
              Replay recording
            </Link>
          ) : null}
          {activeReplayRun && access.canConfigureReplay ? (
            <button className="shell-action" type="button" onClick={() => void stopSimulation()}>
              Stop simulation
            </button>
          ) : null}
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

      {pendingTab ? (
        <ConfirmationDialog
          confirmLabel="Leave tab"
          message="You have unsaved schema changes. Leaving this tab will discard them."
          objectLabel={activeSource.name}
          open={Boolean(pendingTab)}
          reversibilityLabel="This action is not reversible. Unsaved schema changes will be lost."
          title="Leave without saving?"
          tone="warning"
          onClose={() => setPendingTab(null)}
          onConfirm={confirmTabSwitch}
        />
      ) : null}
    </div>
  );
}
