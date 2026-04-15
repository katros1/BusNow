import { apiClient } from "@/lib/api/client";
import type { Park, CreateParkPayload, UpdateParkPayload } from "./parks.types";

export const parksApi = {
  getAll: () => apiClient.get<Park[]>("/terminals"),
  getById: (id: string) => apiClient.get<Park>(`/terminals/${id}`),
  create: (payload: CreateParkPayload) => apiClient.post<Park>("/terminals", payload),
  update: (id: string, payload: UpdateParkPayload) => apiClient.patch<Park>(`/terminals/${id}`, payload),
  remove: (id: string) => apiClient.delete<void>(`/terminals/${id}`),
};
