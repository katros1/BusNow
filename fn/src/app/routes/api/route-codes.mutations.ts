import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { routeCodesApi } from "./route-codes.api";
import { routeCodeKeys } from "./route-codes.keys";
import type { CreateRouteCodePayload, UpdateRouteCodePayload } from "./route-codes.types";

export const useCreateRouteCode = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateRouteCodePayload) => routeCodesApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: routeCodeKeys.lists() });
      toast.success("Route code created successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to create route code");
    },
  });
};

export const useUpdateRouteCode = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateRouteCodePayload) => routeCodesApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: routeCodeKeys.lists() });
      queryClient.invalidateQueries({ queryKey: routeCodeKeys.detail(id) });
      toast.success("Route code updated successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to update route code");
    },
  });
};

export const useDeleteRouteCode = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => routeCodesApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: routeCodeKeys.lists() });
      toast.success("Route code deleted successfully");
    },
    onError: (error: Error) => {
      toast.error(error?.message || "Failed to delete route code");
    },
  });
};
