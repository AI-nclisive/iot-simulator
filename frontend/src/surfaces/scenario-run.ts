/**
 * scenario-run.ts — model + mock data for the Scenario Run View (UI-065).
 *
 * A run is one observed execution of a scenario. The run view is read-only
 * (all users can inspect; stopping is allowed for User and Admin) and must show
 * run summary, current step, ordered step timeline, involved sources, faults,
 * clients, events, and evidence state. The run backend is later-wave, so this
 * is a mock model; shapes stay close to the scenario step model and the live
 * runtime/event streams so a later API swap is localized.
 */

export type RunStatus = "queued" | "running" | "stopped" | "failed" | "completed" | "stale";

export type RunStepStatus = "pending" | "active" | "done" | "skipped" | "failed";

export interface RunTimelineStep {
  stepId: string;
  label: string;
  type: string;
  status: RunStepStatus;
  /** ISO time the step started, or null if not yet reached. */
  startedAt: string | null;
}

export interface RunSource {
  sourceId: string;
  name: string;
  state: "Active" | "Stopped" | "Error";
}

export interface RunFault {
  stepId: string;
  label: string;
  kind: string;
  active: boolean;
}

export interface RunClient {
  clientId: string;
  connected: boolean;
}

export interface RunEvent {
  at: string;
  type: string;
  detail: string;
}

export type EvidenceState = "none" | "collecting" | "available";

export interface ScenarioRun {
  scenarioId: string;
  scenarioName: string;
  status: RunStatus;
  /** Who started the run. */
  initiator: string;
  startedAt: string;
  /** Index into timeline of the current step, or null if not running. */
  currentStepIndex: number | null;
  timeline: RunTimelineStep[];
  sources: RunSource[];
  faults: RunFault[];
  clients: RunClient[];
  events: RunEvent[];
  evidence: EvidenceState;
  /** Evidence artifact id when evidence is available. */
  evidenceId: string | null;
}

/**
 * Mock run for a scenario. Deterministically derived from the scenario id so
 * the view is stable to look at. In the real system this comes from the run
 * API + live runtime/event streams (UI-098 wiring).
 */
export function mockRunFor(scenarioId: string, scenarioName: string): ScenarioRun {
  // A representative in-progress run: source started, replay active, fault armed.
  return {
    scenarioId,
    scenarioName,
    status: "running",
    initiator: "Olena Ohii",
    startedAt: "2026-07-01T08:30:00Z",
    currentStepIndex: 1,
    timeline: [
      { stepId: "t1", label: "Start Line A", type: "start", status: "done", startedAt: "2026-07-01T08:30:01Z" },
      { stepId: "t2", label: "Replay calibration", type: "replay", status: "active", startedAt: "2026-07-01T08:30:12Z" },
      { stepId: "t3", label: "Inject quality fault", type: "fault", status: "pending", startedAt: null },
      { stepId: "t4", label: "Hold 30s", type: "wait", status: "pending", startedAt: null },
    ],
    sources: [{ sourceId: "src-01", name: "Line A telemetry", state: "Active" }],
    faults: [{ stepId: "t3", label: "Quality fault", kind: "quality", active: false }],
    clients: [
      { clientId: "opcua-collector-1", connected: true },
      { clientId: "dashboard-tap", connected: true },
    ],
    events: [
      { at: "2026-07-01T08:30:12Z", type: "STEP_STARTED", detail: "Replay calibration" },
      { at: "2026-07-01T08:30:01Z", type: "SOURCE_STARTED", detail: "Line A telemetry" },
      { at: "2026-07-01T08:30:00Z", type: "RUN_STARTED", detail: "by Olena Ohii" },
    ],
    evidence: "collecting",
    evidenceId: null,
  };
}
