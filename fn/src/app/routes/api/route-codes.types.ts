export interface RouteCodeBase {
  id: string;
  name: string;
}

export interface RouteCode {
  id: string;
  code: string;
  forwardRoute: RouteCodeBase;
  backwardRoute: RouteCodeBase;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRouteCodePayload {
  code: string;
  forwardRouteId: string;
  backwardRouteId: string;
}

export interface UpdateRouteCodePayload {
  code?: string;
  forwardRouteId?: string;
  backwardRouteId?: string;
}
