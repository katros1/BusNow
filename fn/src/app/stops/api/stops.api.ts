import { apiClient } from "@/lib/api/client";
import type { Stop, CreateStopPayload, UpdateStopPayload } from "./stops.types";

export const stopsApi = {
  getAll: () => apiClient.get<Stop[]>("/stops"),
  getById: (id: string) => apiClient.get<Stop>(`/stops/${id}`),
  create: (payload: CreateStopPayload) => apiClient.post<Stop>("/stops", payload),
  update: (id: string, payload: UpdateStopPayload) => apiClient.patch<Stop>(`/stops/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/stops/${id}`),
};
