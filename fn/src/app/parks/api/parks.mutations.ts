import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { parksApi } from "./parks.api";
import { parkKeys } from "./parks.keys";
import type { CreateParkPayload, UpdateParkPayload } from "./parks.types";

export function useCreatePark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateParkPayload) => parksApi.create(payload),
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: parkKeys.lists() });
      toast.success("Park saved", {
        description: `"${created.name}" was created successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to save park", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}

export function useUpdatePark(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateParkPayload) => parksApi.update(id, payload),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: parkKeys.lists() });
      qc.invalidateQueries({ queryKey: parkKeys.detail(id) });
      toast.success("Park updated", {
        description: `"${updated.name}" was updated successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to update park", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}

export function useDeletePark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => parksApi.remove(id),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: parkKeys.detail(id) });
      qc.invalidateQueries({ queryKey: parkKeys.lists() });
      toast.success("Park deleted");
    },
    onError: (error: unknown) => {
      toast.error("Failed to delete park", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}
