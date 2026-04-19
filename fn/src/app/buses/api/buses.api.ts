import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { Bus, CreateBusPayload, UpdateBusPayload } from "./buses.types";

export const busesApi = {
  getAll: (params?: { search?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<Bus>>(qs ? `/buses?${qs}` : "/buses");
  },
  getById: (id: string) => apiClient.get<Bus>(`/buses/${id}`),
  create: (payload: CreateBusPayload) => apiClient.post<Bus>("/buses", payload),
  update: (id: string, payload: UpdateBusPayload) => apiClient.patch<Bus>(`/buses/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/buses/${id}`),
};
