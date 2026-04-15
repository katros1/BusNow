export const routeCodeKeys = {
  all: ["route-codes"] as const,
  lists: () => [...routeCodeKeys.all, "list"] as const,
  list: (filters: string) => [...routeCodeKeys.lists(), { filters }] as const,
  details: () => [...routeCodeKeys.all, "detail"] as const,
  detail: (id: string) => [...routeCodeKeys.details(), id] as const,
};
