import { useState, useMemo } from "react";
import { useNavigate } from "@tanstack/react-router";
import { ChevronDown, ChevronUp, Bus } from "lucide-react";
import type {
  TrackingVehicleDto,
  VehiclePositionEvent,
} from "../api/tracking.types";

interface RouteGroupProps {
  routeCode: string;
  vehicles: TrackingVehicleDto[];
  liveMap: Map<string, VehiclePositionEvent>;
}

export function RouteGroup({ routeCode, vehicles, liveMap }: RouteGroupProps) {
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(true);

  const enrichedVehicles = useMemo(() => {
    if (!Array.isArray(vehicles)) return [];

    return vehicles.map((v) => {
      const live = liveMap.get(v.busId);

      return {
        ...v,
        live,
        liveRouteName: live?.route?.name ?? null,
        liveRouteId: live?.route?.id ?? null,
      };
    });
  }, [vehicles, liveMap]);

  const activeCount = enrichedVehicles.filter(
    (v) => v.live?.trip ?? v.activeTripId
  ).length;

  const routeName = useMemo(() => {
    if (enrichedVehicles.length === 0) {
      return `Route ${routeCode}`;
    }

    const dbRoute = enrichedVehicles.find((v) => v.routeName);
    if (dbRoute?.routeName) return dbRoute.routeName;

    const liveRoute = enrichedVehicles.find((v) => v.liveRouteName);
    if (liveRoute?.liveRouteName) return liveRoute.liveRouteName;

    return `Route ${routeCode}`;
  }, [enrichedVehicles, routeCode]);

  return (
    <div className="bg-card rounded-xl border border-border/60 overflow-hidden shadow-ambient">
      <button
        onClick={() => setExpanded((e) => !e)}
        className="flex w-full items-center gap-4 px-5 py-4 hover:bg-surface-container/40 transition-colors text-left"
      >
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
          <Bus className="h-5 w-5 text-primary" />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-bold">{routeCode}</span>
            <span className="truncate">{routeName}</span>
          </div>
          <p className="text-xs text-muted-foreground">
            {activeCount} Bus{activeCount !== 1 ? "es" : ""} active
          </p>
        </div>

        <div className="flex items-center gap-3">
          {activeCount > 0 && (
            <span className="px-2 py-1 text-xs bg-green-100 text-green-700 rounded-full">
              Live
            </span>
          )}
          {expanded ? <ChevronUp /> : <ChevronDown />}
        </div>
      </button>

      {expanded && (
        <div className="border-t px-5 py-3 flex flex-wrap gap-3">
          {enrichedVehicles.map((v) => {
            const speed =
              v.live?.speedKmh != null
                ? Math.round(v.live.speedKmh)
                : null;

            const isActive = !!(v.live?.trip ?? v.activeTripId);

            const currentStop = v.live?.currentStop ?? null;

            return (
              <button
                key={v.busId}
                onClick={() =>
                  navigate({
                    to: "/tracking/$busId",
                    params: { busId: v.busId },
                  })
                }
                className="flex items-center gap-3 px-4 py-3 rounded-xl border hover:shadow-md"
              >
                <Bus />

                <div className="flex-1">
                  <div className="flex gap-2 items-center">
                    <span className="font-bold">{v.plateNumber}</span>
                    {speed && <span>{speed} km/h</span>}
                  </div>

                  {currentStop ? (
                    <p className="text-xs text-[#91D06C] font-medium">
                      ◉ At {currentStop.name}
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      {isActive ? "Active" : "Idle"}
                    </p>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
