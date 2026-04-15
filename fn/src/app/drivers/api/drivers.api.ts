import { apiClient } from "@/lib/api/client";
import type { Driver, CreateDriverPayload, UpdateDriverPayload } from "./drivers.types";

export const driversApi = {
  getAll: () => apiClient.get<Driver[]>("/drivers"),
  getById: (id: string) => apiClient.get<Driver>(`/drivers/${id}`),
  create: (payload: CreateDriverPayload) => apiClient.post<Driver>("/drivers", payload),
  update: (id: string, payload: UpdateDriverPayload) => apiClient.patch<Driver>(`/drivers/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/drivers/${id}`),
};
