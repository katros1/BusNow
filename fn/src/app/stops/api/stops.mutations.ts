import { useMutation, useQueryClient } from "@tanstack/react-query";
import { stopsApi } from "./stops.api";
import { stopKeys } from "./stops.keys";
import type { CreateStopPayload, UpdateStopPayload } from "./stops.types";

export function useCreateStop() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateStopPayload) => stopsApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: stopKeys.lists() }),
  });
}

export function useUpdateStop(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateStopPayload) => stopsApi.update(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: stopKeys.lists() });
      qc.invalidateQueries({ queryKey: stopKeys.detail(id) });
    },
  });
}

export function useDeleteStop() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stopsApi.remove(id),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: stopKeys.detail(id) });
      qc.invalidateQueries({ queryKey: stopKeys.lists() });
    },
  });
}
