export interface Stop {
  id: string;
  name: string;
  coordinates: number[][];
  createdAt: string;
  updatedAt: string;
}

export interface CreateStopPayload {
  name: string;
  coordinates: number[][];
}

export interface UpdateStopPayload {
  name?: string;
  coordinates?: number[][];
}
