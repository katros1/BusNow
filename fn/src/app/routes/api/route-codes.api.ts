import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { RouteCode, CreateRouteCodePayload, UpdateRouteCodePayload } from "./route-codes.types";

export const routeCodesApi = {
  getAll: (params?: { search?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<RouteCode>>(qs ? `/route-codes?${qs}` : "/route-codes");
  },
  getById: (id: string) => apiClient.get<RouteCode>(`/route-codes/${id}`),
  create: (payload: CreateRouteCodePayload) => apiClient.post<RouteCode>("/route-codes", payload),
  update: (id: string, payload: UpdateRouteCodePayload) => apiClient.patch<RouteCode>(`/route-codes/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/route-codes/${id}`),
};
