import { apiClient } from "@/lib/api/client";
import type { Bus, CreateBusPayload, UpdateBusPayload } from "./buses.types";

export const busesApi = {
  getAll: () => apiClient.get<Bus[]>("/buses"),
  getById: (id: string) => apiClient.get<Bus>(`/buses/${id}`),
  create: (payload: CreateBusPayload) => apiClient.post<Bus>("/buses", payload),
  update: (id: string, payload: UpdateBusPayload) => apiClient.patch<Bus>(`/buses/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/buses/${id}`),
};
