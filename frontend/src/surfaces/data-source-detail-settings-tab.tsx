import { useMemo, useState } from "react";
import { resolveAccess } from "../shell/access-policy";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import type { DataSourceRow } from "./mock-data-sources";

function endpointIsValid(endpoint: string) {
  return endpoint.trim().length > 0 && !endpoint.includes(" ");
}

export function DataSourceDetailSettingsTab({
  source,
}: {
  source: DataSourceRow;
}) {
  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const updateSourceConfiguration = useDataSourcesStore(
    (state) => state.updateSourceConfiguration,
  );
  const access = resolveAccess(accessMode, sharedRole);
  const [name, setName] = useState(source.name);
  const [endpoint, setEndpoint] = useState(source.endpoint);
  const [savedMessage, setSavedMessage] = useState("");

  const validationMessage = useMemo(() => {
    if (name.trim().length === 0) {
      return "Name is required.";
    }

    if (!endpointIsValid(endpoint)) {
      return "Endpoint is required and cannot contain spaces.";
    }

    return "";
  }, [endpoint, name]);

  const hasChanges = name !== source.name || endpoint !== source.endpoint;
  const canSave = access.isAdmin && hasChanges && validationMessage.length === 0;

  function resetForm() {
    setName(source.name);
    setEndpoint(source.endpoint);
    setSavedMessage("");
  }

  function saveChanges() {
    if (!canSave) {
      return;
    }

    updateSourceConfiguration(source.id, {
      endpoint: endpoint.trim(),
      name: name.trim(),
    });
    setSavedMessage("Saved");
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge label={access.isAdmin ? "Editable" : "Read-only"} tone="neutral" />
        {hasChanges ? <StatusBadge label="Unsaved changes" tone="warning" /> : null}
        {savedMessage ? <StatusBadge label={savedMessage} tone="accent" /> : null}
      </div>

      {source.status === "Active" ? (
        <SharedStatePanel
          message="Editing this form changes saved source configuration. Current source output stays separate until the next start or replay setup."
          state="warning"
          title="Configuration and runtime are separated."
        />
      ) : null}

      {access.isSharedUser ? (
        <SharedStatePanel
          message="Shared User can inspect source settings, but changing saved configuration is restricted to Admin."
          state="locked"
          title="Settings are read-only in this role."
        />
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Source name
          <input
            className="shell-field"
            disabled={!access.isAdmin}
            type="text"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setSavedMessage("");
            }}
          />
        </label>

        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Endpoint
          <input
            className="shell-field"
            disabled={!access.isAdmin}
            type="text"
            value={endpoint}
            onChange={(event) => {
              setEndpoint(event.target.value);
              setSavedMessage("");
            }}
          />
        </label>

        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Protocol
          <input
            className="shell-field"
            disabled
            type="text"
            value={source.protocol}
            readOnly
          />
        </label>

        <label className="flex flex-col gap-2 text-sm text-shell-muted">
          Parameters
          <input
            className="shell-field"
            disabled
            type="text"
            value={source.parameterCount.toLocaleString()}
            readOnly
          />
        </label>
      </div>

      {validationMessage ? (
        <p className="text-sm text-shell-danger">{validationMessage}</p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button
          className="shell-action"
          disabled={!canSave}
          type="button"
          onClick={saveChanges}
        >
          Save changes
        </button>
        <button
          className="shell-action"
          disabled={!access.isAdmin || !hasChanges}
          type="button"
          onClick={resetForm}
        >
          Reset
        </button>
      </div>
    </div>
  );
}
