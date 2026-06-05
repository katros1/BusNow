export const fareKeys = {
  all:      () => ["fare-settings"] as const,
  settings: () => [...fareKeys.all(), "settings"] as const,
};
