import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { Park, CreateParkPayload, UpdateParkPayload } from "./parks.types";

export const parksApi = {
  getAll: (params?: { search?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<Park>>(qs ? `/terminals?${qs}` : "/terminals");
  },
  getById: (id: string) => apiClient.get<Park>(`/terminals/${id}`),
  create: (payload: CreateParkPayload) => apiClient.post<Park>("/terminals", payload),
  update: (id: string, payload: UpdateParkPayload) => apiClient.patch<Park>(`/terminals/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/terminals/${id}`),
};
