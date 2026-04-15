export const stopKeys = {
  all:    ()         => ["stops"]              as const,
  lists:  ()         => ["stops", "list"]      as const,
  detail: (id: string) => ["stops", "detail", id] as const,
};
