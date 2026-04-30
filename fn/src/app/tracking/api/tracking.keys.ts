export const trackingKeys = {
  all: ["tracking"] as const,
  vehicles: () => [...trackingKeys.all, "vehicles"] as const,
  route: (id: string) => [...trackingKeys.all, "route", id] as const,
};
