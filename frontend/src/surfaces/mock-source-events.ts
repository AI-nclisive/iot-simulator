export type RuntimeEventLevel = "info" | "warning" | "error";

export type RuntimeEvent = {
  id: string;
  level: RuntimeEventLevel;
  timestamp: string;
  message: string;
  category: "connection" | "runtime" | "recording" | "replay";
};

const eventsBySource: Record<string, RuntimeEvent[]> = {
  "src-01": [
    {
      id: "ev-01-1",
      level: "info",
      timestamp: "14:31:04",
      message: "Replay started. 2,480 parameters loaded from recording.",
      category: "replay",
    },
    {
      id: "ev-01-2",
      level: "info",
      timestamp: "14:18:22",
      message: "Client 192.168.1.55:49802 connected.",
      category: "connection",
    },
    {
      id: "ev-01-3",
      level: "warning",
      timestamp: "14:15:11",
      message: "Parameter oven.zone[2].temp out of expected range. Value: 312.4 °C.",
      category: "runtime",
    },
    {
      id: "ev-01-4",
      level: "info",
      timestamp: "14:12:09",
      message: "Client 192.168.1.42:49231 connected.",
      category: "connection",
    },
    {
      id: "ev-01-5",
      level: "info",
      timestamp: "14:10:00",
      message: "Source started.",
      category: "runtime",
    },
  ],
  "src-02": [
    {
      id: "ev-02-1",
      level: "error",
      timestamp: "14:27:55",
      message: "Register read timeout on address 0x0120. Retrying.",
      category: "runtime",
    },
    {
      id: "ev-02-2",
      level: "info",
      timestamp: "14:27:00",
      message: "Nightly regression replay started by CI pipeline.",
      category: "replay",
    },
    {
      id: "ev-02-3",
      level: "warning",
      timestamp: "14:10:33",
      message: "Client 10.0.0.21:38502 disconnected unexpectedly.",
      category: "connection",
    },
    {
      id: "ev-02-4",
      level: "info",
      timestamp: "13:45:01",
      message: "Client 10.0.0.14:38471 connected.",
      category: "connection",
    },
  ],
  "src-03": [
    {
      id: "ev-03-1",
      level: "warning",
      timestamp: "14:32:10",
      message: "Evidence assembly retry scheduled. Previous attempt failed.",
      category: "recording",
    },
    {
      id: "ev-03-2",
      level: "info",
      timestamp: "14:14:00",
      message: "Recording started by Jordan K. Target: Field capture telemetry.",
      category: "recording",
    },
    {
      id: "ev-03-3",
      level: "info",
      timestamp: "14:00:15",
      message: "Source started.",
      category: "runtime",
    },
  ],
};

export function getEventsForSource(sourceId: string): RuntimeEvent[] {
  return eventsBySource[sourceId] ?? [];
}
