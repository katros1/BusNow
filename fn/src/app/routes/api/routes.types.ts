export interface Route {
  id: string;
  name: string;
  coordinates: number[][];
  startBusParkId: string;
  endBusParkId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoutePayload {
  name: string;
  coordinates: number[][];
  startBusParkId: string;
  endBusParkId: string;
}

export interface UpdateRoutePayload {
  name?: string;
  coordinates?: number[][];
  startBusParkId?: string;
  endBusParkId?: string;
}
