import { apiClient } from "@/lib/api/client";
import type { Route, CreateRoutePayload, UpdateRoutePayload } from "./routes.types";

export const routesApi = {
  getAll: () => apiClient.get<Route[]>("/routes"),
  getById: (id: string) => apiClient.get<Route>(`/routes/${id}`),
  create: (payload: CreateRoutePayload) => apiClient.post<Route>("/routes", payload),
  update: (id: string, payload: UpdateRoutePayload) => apiClient.patch<Route>(`/routes/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/routes/${id}`),
};
