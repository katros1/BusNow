import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { driversApi } from "./drivers.api";
import { driverKeys } from "./drivers.keys";
import type { CreateDriverPayload, UpdateDriverPayload } from "./drivers.types";

export const useCreateDriver = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateDriverPayload) => driversApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: driverKeys.lists() });
      toast.success("Driver registered successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to register driver");
    },
  });
};

export const useUpdateDriver = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateDriverPayload) => driversApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: driverKeys.lists() });
      queryClient.invalidateQueries({ queryKey: driverKeys.detail(id) });
      toast.success("Driver details updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to update driver details");
    },
  });
};

export const useDeleteDriver = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => driversApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: driverKeys.lists() });
      toast.success("Driver record deleted successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to delete driver record");
    },
  });
};
