export interface RouteStop {
  id: string;
  sequenceIndex: number;
  name: string;
  coordinates: number[][];
}

export interface RouteBusPark {
  id: string;
  name: string;
  coordinates: number[][];
}

export interface Route {
  id: string;
  name: string;
  routePath: number[][];
  startBusPark: RouteBusPark;
  endBusPark: RouteBusPark;
  stops?: RouteStop[];
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
