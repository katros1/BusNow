import { apiClient } from "@/lib/api/client";
import type { RouteCode, CreateRouteCodePayload, UpdateRouteCodePayload } from "./route-codes.types";

export const routeCodesApi = {
  getAll: () => apiClient.get<RouteCode[]>("/route-codes"),
  getById: (id: string) => apiClient.get<RouteCode>(`/route-codes/${id}`),
  create: (payload: CreateRouteCodePayload) => apiClient.post<RouteCode>("/route-codes", payload),
  update: (id: string, payload: UpdateRouteCodePayload) => apiClient.patch<RouteCode>(`/route-codes/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/route-codes/${id}`),
};
