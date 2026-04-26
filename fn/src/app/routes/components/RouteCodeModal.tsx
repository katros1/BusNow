import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { X, Save } from "lucide-react";
import { useCreateRouteCode, useUpdateRouteCode } from "../api/route-codes.mutations";
import { routesApi } from "../api/routes.api";
import { routeKeys } from "../api/routes.keys";
import type { RouteCode } from "../api/route-codes.types";

interface RouteCodeModalProps {
  routeCode: RouteCode | null;
  onClose: () => void;
}

export function RouteCodeModal({ routeCode, onClose }: RouteCodeModalProps) {
  const isEditing = !!routeCode;
  
  const [code, setCode] = useState(routeCode?.code || "");
  const [forwardRouteId, setForwardRouteId] = useState(routeCode?.forwardRoute?.id || "");
  const [backwardRouteId, setBackwardRouteId] = useState(routeCode?.backwardRoute?.id || "");

  const { data: routesResponse, isLoading: isLoadingRoutes } = useQuery(routeKeys.lists(), {
    queryFn: () => routesApi.getAll(),
  });
  const routes = routesResponse?.content ?? [];

  const createMut = useCreateRouteCode();
  const updateMut = useUpdateRouteCode(routeCode?.id || "");

  const handleSave = () => {
    if (!code || !forwardRouteId || !backwardRouteId) return;

    const payload = { code, forwardRouteId, backwardRouteId };

    if (isEditing) {
      updateMut.mutate(payload, { onSuccess: onClose });
    } else {
      createMut.mutate(payload, { onSuccess: onClose });
    }
  };

  const isPending = createMut.isPending || updateMut.isPending;
  const isComplete = code && forwardRouteId && backwardRouteId;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div 
        className="bg-white w-full max-w-md rounded-2xl shadow-xl border border-border overflow-hidden flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="px-6 py-5 border-b border-border flex items-center justify-between bg-surface-container-lowest">
          <div>
            <h2 className="text-xl font-bold text-foreground">
              {isEditing ? "Edit Route Code" : "Create Route Code"}
            </h2>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Link forward and backward routes.
            </p>
          </div>
          <button 
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-muted-foreground hover:bg-surface-container hover:text-foreground transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-6 flex flex-col gap-5">
          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Code Identifier
            </label>
            <input 
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="e.g. 104, 305A"
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Forward Route
            </label>
            <select
              value={forwardRouteId}
              onChange={(e) => setForwardRouteId(e.target.value)}
              disabled={isLoadingRoutes}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium cursor-pointer disabled:opacity-50"
            >
              <option value="" disabled>Select forwarding route...</option>
              {routes.map((r) => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Backward Route
            </label>
            <select
              value={backwardRouteId}
              onChange={(e) => setBackwardRouteId(e.target.value)}
              disabled={isLoadingRoutes}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium cursor-pointer disabled:opacity-50"
            >
              <option value="" disabled>Select returning route...</option>
              {routes.map((r) => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="px-6 py-4 border-t border-border bg-surface-container-lowest flex justify-end gap-3">
          <button 
            onClick={onClose}
            className="px-5 h-10 text-[13px] font-bold text-muted-foreground hover:text-foreground transition-colors"
          >
            Cancel
          </button>
          <button 
            onClick={handleSave}
            disabled={isPending || !isComplete}
            className="flex items-center gap-2 px-6 h-10 bg-primary text-primary-foreground text-[13px] font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {isPending ? "Saving..." : (
               <>
                 <Save className="h-4 w-4" />
                 Save Route Code
               </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
