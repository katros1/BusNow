export const parkKeys = {
  all:    ()         => ["parks"]              as const,
  lists:  ()         => ["parks", "list"]      as const,
  detail: (id: string) => ["parks", "detail", id] as const,
};
