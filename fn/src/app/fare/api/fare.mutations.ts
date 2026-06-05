import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { fareApi } from "./fare.api";
import { fareKeys } from "./fare.keys";
import type { UpdateFareSettingsPayload } from "./fare.types";

export function useUpdateFareSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateFareSettingsPayload) => fareApi.updateSettings(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: fareKeys.settings() });
      toast.success("Fare settings saved", {
        description: "Base price updated. All new journey fares will reflect the change.",
      });
    },
    onError: (error: unknown) => {
      toast.error("Failed to save fare settings", {
        description: error instanceof Error ? error.message : "Unexpected error occurred.",
      });
    },
  });
}
