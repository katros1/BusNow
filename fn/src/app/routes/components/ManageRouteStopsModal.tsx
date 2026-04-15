import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { X, Search, CheckSquare, Square, Save } from "lucide-react";
import { stopsApi } from "../../stops/api/stops.api";
import { stopKeys } from "../../stops/api/stops.keys";
import { useUpdateRouteStops } from "../api/routes.mutations";
import type { Route } from "../api/routes.types";

interface ManageRouteStopsModalProps {
  route: Route | null;
  onClose: () => void;
}

export function ManageRouteStopsModal({ route, onClose }: ManageRouteStopsModalProps) {
  const [query, setQuery] = useState("");
  const [selectedStops, setSelectedStops] = useState<Set<string>>(() => {
    if (route && (route as any).stopIds) {
      return new Set((route as any).stopIds);
    }
    return new Set();
  });

  const { data: stops = [], isLoading } = useQuery({
    queryKey: stopKeys.lists(),
    queryFn: () => stopsApi.getAll(),
    enabled: !!route,
  });

  const updateStopsMut = useUpdateRouteStops(route?.id ?? "");

  if (!route) return null;

  const filteredStops = stops.filter(s => s.name.toLowerCase().includes(query.toLowerCase()));

  const toggleStop = (stopId: string) => {
    setSelectedStops((prev) => {
      const next = new Set(prev);
      if (next.has(stopId)) next.delete(stopId);
      else next.add(stopId);
      return next;
    });
  };

  const handleSave = () => {
    updateStopsMut.mutate({ stopIds: Array.from(selectedStops) }, {
      onSuccess: () => {
        onClose();
      }
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div 
        className="bg-white w-full max-w-lg rounded-2xl shadow-xl border border-border overflow-hidden flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="px-6 py-5 border-b border-border flex items-center justify-between bg-surface-container-lowest">
          <div>
            <h2 className="text-xl font-bold text-foreground">Manage Route Stops</h2>
            <p className="text-[13px] text-muted-foreground mt-0.5">Attach passenger stops to {route.name}</p>
          </div>
          <button 
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-muted-foreground hover:bg-surface-container hover:text-foreground transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-4 border-b border-border bg-white sticky top-0">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input 
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search available stops..."
              className="w-full h-10 pl-9 pr-4 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>
          <p className="text-[11px] font-semibold text-muted-foreground uppercase tracking-widest mt-4 ml-1">
             {selectedStops.size} selected
          </p>
        </div>

        <div className="max-h-[350px] overflow-y-auto p-2">
          {isLoading ? (
            <div className="py-12 flex flex-col items-center justify-center gap-2 text-muted-foreground">
              <div className="w-5 h-5 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
              <span className="text-[13px]">Loading stops array...</span>
            </div>
          ) : filteredStops.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground text-[13px]">
              No stops matched your search query.
            </div>
          ) : (
            filteredStops.map(stop => (
              <button
                key={stop.id}
                onClick={() => toggleStop(stop.id)}
                className={`w-full flex items-center gap-3 p-3 rounded-lg hover:bg-primary/[0.03] transition-colors text-left ${selectedStops.has(stop.id) ? 'bg-primary/5 border border-primary/10' : 'border border-transparent'}`}
              >
                {selectedStops.has(stop.id) ? (
                  <CheckSquare className="h-5 w-5 text-primary shrink-0" />
                ) : (
                  <Square className="h-5 w-5 text-muted-foreground shrink-0" />
                )}
                <div>
                  <p className="text-[14px] font-semibold text-foreground">{stop.name}</p>
                  <p className="text-[11px] text-muted-foreground font-medium">UK-{stop.id.slice(0,8).toUpperCase()}</p>
                </div>
              </button>
            ))
          )}
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
            disabled={updateStopsMut.isPending}
            className="flex items-center gap-2 px-6 h-10 bg-primary text-primary-foreground text-[13px] font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {updateStopsMut.isPending ? "Configuring..." : (
               <>
                 <Save className="h-4 w-4" />
                 Save Route Configuration
               </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
