import { useState, useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { ChevronDown, ChevronUp, Bus, Navigation } from "lucide-react";
import { cn } from "@/lib/utils";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { nearestStop, formatDistance } from "../utils/geo";
import type { TrackingVehicleDto, VehiclePositionEvent } from "../api/tracking.types";

interface RouteGroupProps {
  routeCode: string;
  vehicles: TrackingVehicleDto[];
  liveMap: Map<string, VehiclePositionEvent>;
}

export function RouteGroup({ routeCode, vehicles, liveMap }: RouteGroupProps) {
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(true);

  // Count how many have active trips (from live WS or initial snapshot)
  const activeCount = vehicles.filter(
    (v) => liveMap.get(v.busId)?.trip ?? v.activeTripId
  ).length;

  // Route name from first vehicle that has one
  const routeName =
    vehicles.find((v) => v.routeName)?.routeName ??
    vehicles.find((v) => liveMap.get(v.busId)?.route?.name)
      ? liveMap.get(vehicles.find((v) => liveMap.get(v.busId)?.route?.name)!.busId)?.route?.name
      : null ??
      `Route ${routeCode}`;

  // Collect unique routeIds for the vehicles in this group
  const routeIds = useMemo(() => {
    const ids = new Set<string>();
    for (const v of vehicles) {
      const rid = liveMap.get(v.busId)?.route?.id ?? v.routeId;
      if (rid) ids.add(rid);
    }
    return [...ids];
  }, [vehicles, liveMap]);

  // Load route details (stops) for each routeId when group is expanded
  const routeQueries = useQueries({
    queries: routeIds.map((rid) => ({
      queryKey: trackingKeys.route(rid),
      queryFn: () => trackingApi.getRouteDetail(rid),
      enabled: expanded,
      staleTime: 60_000,
    })),
  });

  // Build routeId → stops map
  const routeStops = useMemo(() => {
    const m = new Map<string, ReturnType<typeof routeQueries[0]["data"]>>();
    routeIds.forEach((rid, i) => {
      if (routeQueries[i]?.data) m.set(rid, routeQueries[i].data!);
    });
    return m;
  }, [routeIds, routeQueries]);

  return (
    <div className="bg-card rounded-xl border border-border/60 overflow-hidden shadow-ambient">
      {/* ── Header ── */}
      <button
        onClick={() => setExpanded((e) => !e)}
        className="flex w-full items-center gap-4 px-5 py-4 hover:bg-surface-container/40 transition-colors text-left"
      >
        {/* Icon */}
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
          <svg
            width="20" height="20" viewBox="0 0 24 24" fill="none"
            className="text-primary"
            stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
          >
            <path d="M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z" />
          </svg>
        </div>

        {/* Route info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="text-[15px] font-bold text-foreground">{routeCode}</span>
            <span className="text-[13px] font-medium text-foreground truncate">
              {routeName}
            </span>
          </div>
          <p className="text-[11px] text-muted-foreground">
            {activeCount} Bus{activeCount !== 1 ? "es" : ""} active
          </p>
        </div>

        {/* Live badge + chevron */}
        <div className="flex items-center gap-3 shrink-0">
          {activeCount > 0 && (
            <span className="flex items-center gap-1.5 rounded-full border border-[#2E6B1A]/20 bg-[#2E6B1A]/5 px-3 py-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
              <span className="text-[11px] font-semibold text-[#2E6B1A]">Live</span>
            </span>
          )}
          {expanded ? (
            <ChevronUp className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
      </button>

      {/* ── Vehicle cards ── */}
      {expanded && (
        <div className="border-t border-border/50 px-5 py-3 flex flex-wrap gap-3">
          {vehicles.map((v) => {
            const live = liveMap.get(v.busId);
            const routeId = live?.route?.id ?? v.routeId;
            const detail = routeId ? routeStops.get(routeId) : undefined;

            const speed = live?.speedKmh != null ? Math.round(live.speedKmh) : null;
            const isActive = !!(live?.trip ?? v.activeTripId);
            const direction = live?.route?.direction ?? v.direction;

            const nextStop =
              detail?.stops && live?.latitude && live?.longitude
                ? nearestStop(live.latitude, live.longitude, detail.stops)
                : null;

            return (
              <button
                key={v.busId}
                onClick={() =>
                  navigate({ to: "/tracking/$busId", params: { busId: v.busId } })
                }
                className={cn(
                  "flex items-center gap-3 px-4 py-3 rounded-xl border transition-all text-left",
                  "hover:shadow-ambient hover:border-primary/30 hover:-translate-y-0.5",
                  "bg-surface-container/40 border-border/50",
                  "min-w-[200px] max-w-[280px]"
                )}
              >
                {/* Bus icon + status */}
                <div className="relative shrink-0">
                  <div className={cn(
                    "flex h-10 w-10 items-center justify-center rounded-xl",
                    isActive ? "bg-[#2E6B1A]/10" : "bg-muted"
                  )}>
                    <Bus className={cn(
                      "h-[18px] w-[18px]",
                      isActive ? "text-[#2E6B1A]" : "text-muted-foreground"
                    )} />
                  </div>
                  <span className={cn(
                    "absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-card",
                    isActive
                      ? "bg-[#91D06C] pulse-live"
                      : live
                      ? "bg-[#4C8CE4]"
                      : "bg-muted-foreground/40"
                  )} />
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 mb-1">
                    <span className="text-[13px] font-bold text-foreground">{v.plateNumber}</span>
                    {speed != null && (
                      <span className="text-[10px] font-bold rounded-full bg-[#2E6B1A]/10 text-[#2E6B1A] px-1.5 py-0.5">
                        {speed} km/h
                      </span>
                    )}
                  </div>

                  {nextStop ? (
                    <p className="text-[10px] text-muted-foreground truncate">
                      <Navigation className="inline h-2.5 w-2.5 mr-0.5 -mt-0.5" />
                      {nextStop.stop.name}
                      <span className="ml-1 text-muted-foreground/70">
                        · {formatDistance(nextStop.distanceM)}
                      </span>
                    </p>
                  ) : direction ? (
                    <p className="text-[10px] text-muted-foreground">
                      {direction === "FORWARD" ? "→ Forward" : "← Backward"}
                    </p>
                  ) : (
                    <p className="text-[10px] text-muted-foreground">
                      {isActive ? "Active trip" : "Idle"}
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
