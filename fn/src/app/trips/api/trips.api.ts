import { apiClient } from "@/lib/api/client";
import type { PagedTrips, TripListParams, TripSummaryDto } from "./trips.types";

export const tripsApi = {
  list: (params: TripListParams = {}) => {
    const qs = new URLSearchParams();
    if (params.busId)  qs.set("busId",  params.busId);
    if (params.status) qs.set("status", params.status);
    if (params.page != null) qs.set("page", String(params.page));
    if (params.size != null) qs.set("size", String(params.size));
    const query = qs.toString();
    return apiClient.get<PagedTrips>(`/trips${query ? `?${query}` : ""}`);
  },

  get: (id: string) => apiClient.get<TripSummaryDto>(`/trips/${id}`),
};
