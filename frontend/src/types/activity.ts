export type ActivityEventDto = {
  id: number;
  projectId: string | null;
  actor: string;
  action: string;
  objectType: string;
  objectId: string | null;
  at: string;
  detail: Record<string, unknown>;
};

export type ActivityHistoryApiResponse = {
  events: ActivityEventDto[];
  nextCursor: string | null;
};
