import { useState, useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { ChevronDown, ChevronUp, Bus, Navigation } from "lucide-react";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { nearestStop, formatDistance } from "../utils/geo";
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

  const routeIds = useMemo(() => {
    const ids = new Set<string>();

    for (const v of enrichedVehicles) {
      const rid = v.liveRouteId ?? v.routeId;
      if (rid) ids.add(rid);
    }

    return [...ids];
  }, [enrichedVehicles]);

  const routeQueries = useQueries({
    queries: routeIds.map((rid) => ({
      queryKey: trackingKeys.route(rid),
      queryFn: () => trackingApi.getRouteDetail(rid),
      enabled: expanded,
      staleTime: 60_000,
    })),
  });

  const routeStops = useMemo(() => {
    const m = new Map<string, (typeof routeQueries)[0]["data"]>();

    routeIds.forEach((rid, i) => {
      const data = routeQueries[i]?.data;
      if (data) m.set(rid, data);
    });

    return m;
  }, [routeIds, routeQueries]);

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
            const routeId = v.liveRouteId ?? v.routeId;
            const detail = routeId ? routeStops.get(routeId) : undefined;

            const speed =
              v.live?.speedKmh != null
                ? Math.round(v.live.speedKmh)
                : null;

            const isActive = !!(v.live?.trip ?? v.activeTripId);

            const nextStop =
              detail?.stops && v.live?.latitude && v.live?.longitude
                ? nearestStop(
                    v.live.latitude,
                    v.live.longitude,
                    detail.stops
                  )
                : null;

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

                  {nextStop ? (
                    <p className="text-xs">
                      <Navigation className="inline w-3 h-3" />
                      {nextStop.stop.name} ·{" "}
                      {formatDistance(nextStop.distanceM)}
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
