import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { Stop, CreateStopPayload, UpdateStopPayload } from "./stops.types";

export const stopsApi = {
  getAll: (params?: { search?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<Stop>>(qs ? `/stops?${qs}` : "/stops");
  },
  getById: (id: string) => apiClient.get<Stop>(`/stops/${id}`),
  create: (payload: CreateStopPayload) => apiClient.post<Stop>("/stops", payload),
  update: (id: string, payload: UpdateStopPayload) => apiClient.patch<Stop>(`/stops/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/stops/${id}`),
};
