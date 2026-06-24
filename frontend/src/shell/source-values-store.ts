import { create } from "zustand";
import { sourceValueRows, type SourceValueRow } from "../surfaces/mock-source-values";

type SourceValuesState = {
  togglePinnedValue: (valueId: string) => void;
  values: SourceValueRow[];
};

export const useSourceValuesStore = create<SourceValuesState>((set) => ({
  togglePinnedValue: (valueId) =>
    set((state) => ({
      values: state.values.map((valueRow) =>
        valueRow.id === valueId
          ? { ...valueRow, pinned: !valueRow.pinned }
          : valueRow,
      ),
    })),
  values: sourceValueRows,
}));
