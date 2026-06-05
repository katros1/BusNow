import type { JourneyPlanRequest } from "./journey.types";

export const journeyKeys = {
  all:  () => ["journey"] as const,
  plan: (params: JourneyPlanRequest) => [...journeyKeys.all(), "plan", params] as const,
};
