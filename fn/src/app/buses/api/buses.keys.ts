export const busKeys = {
  all: ["buses"] as const,
  lists: () => [...busKeys.all, "list"] as const,
  list: (filters: string) => [...busKeys.lists(), { filters }] as const,
  details: () => [...busKeys.all, "detail"] as const,
  detail: (id: string) => [...busKeys.details(), id] as const,
};
