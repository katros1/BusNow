import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { routesApi } from "./routes.api";
import { routeKeys } from "./routes.keys";
import type { CreateRoutePayload, UpdateRoutePayload } from "./routes.types";

export function useCreateRoute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRoutePayload) => routesApi.create(payload),
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: routeKeys.lists() });
      toast.success("Route saved", {
        description: `"${created.name}" was created successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to save route", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}

export function useUpdateRoute(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateRoutePayload) => routesApi.update(id, payload),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: routeKeys.lists() });
      qc.invalidateQueries({ queryKey: routeKeys.detail(id) });
      toast.success("Route updated", {
        description: `"${updated.name}" was updated successfully.`,
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to update route", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}

export function useDeleteRoute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => routesApi.remove(id),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: routeKeys.detail(id) });
      qc.invalidateQueries({ queryKey: routeKeys.lists() });
      toast.success("Route deleted");
    },
    onError: (error: unknown) => {
      toast.error("Failed to delete route", {
        description:
          error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}
