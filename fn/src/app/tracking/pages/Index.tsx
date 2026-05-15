import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { RadioTower, WifiOff } from "lucide-react";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { useTrackingSocket } from "../hooks/useTrackingSocket";
import { RouteGroup } from "../components/RouteGroup";
import type { TrackingVehicleDto } from "../api/tracking.types";

export default function TrackingOverview() {
  const { data: initialVehicles = [], isLoading } = useQuery({
    queryKey: trackingKeys.vehicles(),
    queryFn: trackingApi.getVehicles,
    refetchInterval: 30_000,
    staleTime: 10_000,
  });

  // Unique route IDs — drives STOMP subscriptions
  const routeIds = useMemo(
    () => [...new Set(initialVehicles.map((v) => v.routeId).filter(Boolean) as string[])],
    [initialVehicles]
  );

  const { vehicles: liveMap, connected } = useTrackingSocket(routeIds);

  // Group vehicles by routeCode (fall back to "Unassigned" bucket)
  const groups = useMemo(() => {
    const map = new Map<string, TrackingVehicleDto[]>();
    for (const v of initialVehicles) {
      const key = v.routeCode ?? "__none__";
      const arr = map.get(key) ?? [];
      arr.push(v);
      map.set(key, arr);
    }
    return [...map.entries()].sort(([a], [b]) => {
      if (a === "__none__") return 1;
      if (b === "__none__") return -1;
      return a.localeCompare(b);
    });
  }, [initialVehicles]);

  const totalActive = useMemo(
    () =>
      initialVehicles.filter(
        (v) => liveMap.get(v.busId)?.tripId != null || v.activeTripId
      ).length,
    [initialVehicles, liveMap]
  );

  return (
    <div className="max-w-4xl mx-auto w-full space-y-5">
      {/* ── Page header ── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10">
            <RadioTower className="h-4.5 w-4.5 text-primary" />
          </div>
          <div>
            <h1 className="text-[22px] font-bold text-foreground leading-tight tracking-tight">
              Live by Route
            </h1>
            <p className="text-[11px] text-muted-foreground">
              {totalActive} active trip{totalActive !== 1 ? "s" : ""} across{" "}
              {groups.filter(([k]) => k !== "__none__").length} route{groups.filter(([k]) => k !== "__none__").length !== 1 ? "s" : ""}
            </p>
          </div>
        </div>

        {/* WS status */}
        {connected ? (
          <span className="flex items-center gap-1.5 rounded-full border border-[#2E6B1A]/20 bg-[#2E6B1A]/5 px-3 py-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
            <span className="text-[11px] font-semibold text-[#2E6B1A]">Connected</span>
          </span>
        ) : (
          <span className="flex items-center gap-1.5 rounded-full border border-border bg-muted px-3 py-1.5">
            <WifiOff className="h-3 w-3 text-muted-foreground" />
            <span className="text-[11px] font-medium text-muted-foreground">Reconnecting…</span>
          </span>
        )}
      </div>

      {/* ── Loading skeleton ── */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div
              key={i}
              className="h-24 rounded-xl bg-card border border-border/60 animate-pulse"
            />
          ))}
        </div>
      )}

      {/* ── Empty state ── */}
      {!isLoading && initialVehicles.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-border/60 bg-card py-16 text-muted-foreground">
          <RadioTower className="h-10 w-10 opacity-20" />
          <div className="text-center">
            <p className="text-[14px] font-semibold text-foreground">No vehicles registered</p>
            <p className="text-[12px] mt-1">
              Register a bus and assign it to a route to start tracking.
            </p>
          </div>
        </div>
      )}

      {/* ── Route groups ── */}
      {!isLoading &&
        groups.map(([routeCode, vehicles]) => (
          <RouteGroup
            key={routeCode}
            routeCode={routeCode === "__none__" ? "—" : routeCode}
            vehicles={vehicles}
            liveMap={liveMap}
          />
        ))}
    </div>
  );
}
