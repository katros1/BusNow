import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { parksApi } from "./parks.api";
import { parkKeys } from "./parks.keys";

export const useParks = () =>
  useQuery({
    queryKey: parkKeys.lists(),
    queryFn:  parksApi.getAll,
  });

export const usePark = (id: string) =>
  useQuery({
    queryKey: parkKeys.detail(id),
    queryFn:  () => parksApi.getById(id),
    enabled:  !!id,
  });

export const useParksSuspense = () =>
  useSuspenseQuery({
    queryKey: parkKeys.lists(),
    queryFn:  parksApi.getAll,
  });
