import { apiClient } from "@/lib/api/client";
import type { PaginatedResponse } from "@/lib/api/types";
import type { Driver, CreateDriverPayload, UpdateDriverPayload } from "./drivers.types";

export const driversApi = {
  getAll: (params?: { search?: string; gender?: string; licenseCategory?: string; page?: number; size?: number }) => {
    const queryParams = new URLSearchParams();
    if (params?.search) queryParams.append("search", params.search);
    if (params?.gender) queryParams.append("gender", params.gender);
    if (params?.licenseCategory) queryParams.append("licenseCategory", params.licenseCategory);
    if (params?.page !== undefined) queryParams.append("page", params.page.toString());
    if (params?.size !== undefined) queryParams.append("size", params.size.toString());
    const qs = queryParams.toString();
    return apiClient.get<PaginatedResponse<Driver>>(qs ? `/drivers?${qs}` : "/drivers");
  },
  getById: (id: string) => apiClient.get<Driver>(`/drivers/${id}`),
  create: (payload: CreateDriverPayload) => apiClient.post<Driver>("/drivers", payload),
  update: (id: string, payload: UpdateDriverPayload) => apiClient.patch<Driver>(`/drivers/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/drivers/${id}`),
};
