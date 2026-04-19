import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { Route, CreateRoutePayload, UpdateRoutePayload } from "./routes.types";
import type { Stop } from "../../stops/api/stops.types";

export const routesApi = {
  getAll: (params?: { search?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<Route>>(qs ? `/routes?${qs}` : "/routes");
  },
  getById: (id: string) => apiClient.get<Route>(`/routes/${id}`),
  create: (payload: CreateRoutePayload) => apiClient.post<Route>("/routes", payload),
  update: (id: string, payload: UpdateRoutePayload) => apiClient.patch<Route>(`/routes/${id}`, payload),
  updateStops: (id: string, payload: { stopIds: string[] }) => apiClient.put<void>(`/routes/${id}/stops`, payload),
  getRouteStops: (id: string) => apiClient.get<Stop[]>(`/routes/${id}/stops`),
  remove: (id: string) => apiClient.delete<void>(`/routes/${id}`),
};
