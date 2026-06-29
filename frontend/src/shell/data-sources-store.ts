import { create } from "zustand";
import { sourceRows, type DataSourceRow } from "../surfaces/mock-data-sources";

type CreateDataSourceInput = {
  endpoint: string;
  name: string;
  protocol: DataSourceRow["protocol"];
};

type DataSourcesState = {
  assignReplayArtifact: (rowId: string, artifactId: string, operator?: string) => void;
  finishRecording: (rowId: string, operator?: string) => void;
  finishReplay: (rowId: string, operator?: string) => void;
  createDataSource: (input: CreateDataSourceInput) => string;
  dataSources: DataSourceRow[];
  deleteDataSource: (rowId: string) => void;
  duplicateDataSource: (rowId: string) => void;
  startRecording: (rowId: string, operator?: string) => void;
  startReplay: (
    rowId: string,
    operator?: string,
    deterministicSettings?: Record<string, unknown> | null,
  ) => void;
  startDataSource: (rowId: string) => void;
  stopDataSource: (rowId: string) => void;
  updateSourceConfiguration: (
    rowId: string,
    input: Pick<CreateDataSourceInput, "endpoint" | "name">,
  ) => void;
};

function nextId(currentRows: DataSourceRow[]) {
  const highestId = currentRows.reduce((highest, row) => {
    const numericId = Number(row.id.replace("src-", ""));
    if (Number.isNaN(numericId)) {
      return highest;
    }

    return Math.max(highest, numericId);
  }, 0);

  return `src-${String(highestId + 1).padStart(2, "0")}`;
}

function runtimeOperator(operator?: string) {
  return operator?.trim() || "You";
}

export const useDataSourcesStore = create<DataSourcesState>((set) => ({
  assignReplayArtifact: (rowId, artifactId, operator) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              assignedReplayArtifactId: artifactId,
              lastOperator: runtimeOperator(operator),
            }
          : row,
      ),
    })),
  finishRecording: (rowId, operator) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              lastOperator: runtimeOperator(operator),
              process: undefined,
              status: "Active",
            }
          : row,
      ),
    })),
  finishReplay: (rowId, operator) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              lastOperator: runtimeOperator(operator),
              process: undefined,
              status: "Active",
            }
          : row,
      ),
    })),
  createDataSource: ({ endpoint, name, protocol }) => {
    let createdId = "";

    set((state) => {
      createdId = nextId(state.dataSources);

      return {
        dataSources: [
          ...state.dataSources,
          {
            clients: 0,
            endpoint,
            health: "Healthy",
            id: createdId,
            lastOperator: "You",
            name,
            parameterCount: protocol === "OPC UA" ? 480 : 96,
            protocol,
            status: "Stopped",
          },
        ],
      };
    });

    return createdId;
  },
  dataSources: sourceRows,
  deleteDataSource: (rowId) =>
    set((state) => ({
      dataSources: state.dataSources.filter((row) => row.id !== rowId),
    })),
  duplicateDataSource: (rowId) =>
    set((state) => {
      const row = state.dataSources.find((item) => item.id === rowId);
      if (!row) {
        return state;
      }

      return {
        dataSources: [
          ...state.dataSources,
          {
            assignedReplayArtifactId: row.assignedReplayArtifactId,
            ...row,
            clients: 0,
            id: nextId(state.dataSources),
            lastOperator: "You",
            name: `${row.name} copy`,
            parameterCount: row.parameterCount,
            process: undefined,
            status: "Stopped",
          },
        ],
      };
    }),
  startRecording: (rowId, operator) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              lastOperator: runtimeOperator(operator),
              process: "Recording",
              status: "Active",
            }
          : row,
      ),
    })),
  startReplay: (rowId, operator, _deterministicSettings) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              lastOperator: runtimeOperator(operator),
              process: "Replay",
              status: "Active",
            }
          : row,
      ),
    })),
  startDataSource: (rowId) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId ? { ...row, lastOperator: "You", status: "Active" } : row,
      ),
    })),
  stopDataSource: (rowId) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              clients: 0,
              lastOperator: "You",
              process: undefined,
              status: "Stopped",
            }
          : row,
      ),
    })),
  updateSourceConfiguration: (rowId, input) =>
    set((state) => ({
      dataSources: state.dataSources.map((row) =>
        row.id === rowId
          ? {
              ...row,
              endpoint: input.endpoint,
              lastOperator: "You",
              name: input.name,
            }
          : row,
      ),
    })),
}));

export type { CreateDataSourceInput };
