export interface Park {
  id: string;
  name: string;
  coordinates: number[][];
  createdAt: string;
  updatedAt: string;
}

export interface CreateParkPayload {
  name: string;
  coordinates: number[][];
}

export interface UpdateParkPayload {
  name?: string;
  coordinates?: number[][];
}
