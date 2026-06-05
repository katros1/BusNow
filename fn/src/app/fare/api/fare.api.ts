import { apiClient } from "@/lib/api/client";
import type { FareSettings, UpdateFareSettingsPayload } from "./fare.types";

export const fareApi = {
  getSettings: () => apiClient.get<FareSettings>("/fare-settings"),
  updateSettings: (payload: UpdateFareSettingsPayload) =>
    apiClient.put<FareSettings>("/fare-settings", payload),
};
