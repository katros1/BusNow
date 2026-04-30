import { apiClient } from "@/lib/api/client";
import type { TrackingVehicleDto, RouteDetailDto } from "./tracking.types";

export const trackingApi = {
  getVehicles: () => apiClient.get<TrackingVehicleDto[]>("/tracking/vehicles"),
  getRouteDetail: (routeId: string) =>
    apiClient.get<RouteDetailDto>(`/routes/${routeId}`),
};
