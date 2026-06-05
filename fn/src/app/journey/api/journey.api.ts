import { apiClient } from "@/lib/api/client";
import type { JourneyPlanRequest, JourneyPlanResponse } from "./journey.types";

export const journeyApi = {
  plan: ({ fromLat, fromLng, toLat, toLng }: JourneyPlanRequest) =>
    apiClient.get<JourneyPlanResponse>(
      `/journey-planner/plan?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}`
    ),
};
