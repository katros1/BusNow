import type { TripListParams } from "./trips.types";

export const tripKeys = {
  all:    ()           => ["trips"] as const,
  lists:  ()           => ["trips", "list"] as const,
  list:   (p: TripListParams) => ["trips", "list", p] as const,
  detail: (id: string) => ["trips", id] as const,
};
