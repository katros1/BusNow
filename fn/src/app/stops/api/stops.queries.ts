import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { stopsApi } from "./stops.api";
import { stopKeys } from "./stops.keys";

export const useStops = () =>
  useQuery(stopKeys.lists(), {
    queryFn: () => stopsApi.getAll(),
  });

export const useStop = (id: string) =>
  useQuery(stopKeys.detail(id), {
    queryFn:  () => stopsApi.getById(id),
    enabled:  !!id,
  });

export const useStopsSuspense = () =>
  useSuspenseQuery({
    queryKey: stopKeys.lists(),
    queryFn:  () => stopsApi.getAll(),
  });
