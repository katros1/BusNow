import { apiClient } from "@/lib/api/client";
import type { Route, CreateRoutePayload, UpdateRoutePayload } from "./routes.types";

export const routesApi = {
  getAll: () => apiClient.get<Route[]>("/routes"),
  getById: (id: string) => apiClient.get<Route>(`/routes/${id}`),
  create: (payload: CreateRoutePayload) => apiClient.post<Route>("/routes", payload),
  update: (id: string, payload: UpdateRoutePayload) => apiClient.patch<Route>(`/routes/${id}`, payload),
  updateStops: (id: string, payload: { stopIds: string[] }) => apiClient.put<void>(`/routes/${id}/stops`, payload),
  remove: (id: string) => apiClient.delete<void>(`/routes/${id}`),
};
