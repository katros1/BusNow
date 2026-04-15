import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { busesApi } from "./buses.api";
import { busKeys } from "./buses.keys";
import type { CreateBusPayload, UpdateBusPayload } from "./buses.types";

export const useCreateBus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateBusPayload) => busesApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: busKeys.lists() });
      toast.success("Bus registered successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to register bus");
    },
  });
};

export const useUpdateBus = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateBusPayload) => busesApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: busKeys.lists() });
      queryClient.invalidateQueries({ queryKey: busKeys.detail(id) });
      toast.success("Bus details updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to update bus details");
    },
  });
};

export const useDeleteBus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => busesApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: busKeys.lists() });
      toast.success("Bus record deleted successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to delete bus record");
    },
  });
};
