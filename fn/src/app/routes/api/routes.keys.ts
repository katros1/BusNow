export const routeKeys = {
  all:    ()         => ["routes"]              as const,
  lists:  ()         => ["routes", "list"]      as const,
  detail: (id: string) => ["routes", "detail", id] as const,
};
