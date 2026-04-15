import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { stopsApi } from "./stops.api";
import { stopKeys } from "./stops.keys";
import type { CreateStopPayload, UpdateStopPayload } from "./stops.types";

export function useCreateStop() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateStopPayload) => stopsApi.create(payload),
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: stopKeys.lists() });
      toast.success("Stop saved", {
        description: `"${created.name}" was created successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to save stop", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}

export function useUpdateStop(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateStopPayload) => stopsApi.update(id, payload),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: stopKeys.lists() });
      qc.invalidateQueries({ queryKey: stopKeys.detail(id) });
      toast.success("Stop updated", {
        description: `"${updated.name}" was updated successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to update stop", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
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
      toast.success("Stop deleted");
    },
    onError: (error: unknown) => {
      toast.error("Failed to delete stop", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}
