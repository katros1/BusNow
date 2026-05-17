import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { RadioTower, WifiOff, Bus, Activity } from "lucide-react";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { useTrackingSocket } from "../hooks/useTrackingSocket";
import { RouteGroup } from "../components/RouteGroup";
import type { TrackingVehicleDto } from "../api/tracking.types";

export default function TrackingOverview() {
  const { data: initialVehicles = [], isLoading } = useQuery({
    queryKey: trackingKeys.vehicles(),
    queryFn: trackingApi.getVehicles,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });

  // Subscribe per-plateNumber — stable across direction toggles
  const plates = useMemo(
    () => initialVehicles.map((v) => v.plateNumber).filter(Boolean),
    [initialVehicles]
  );

  const { vehicles: liveMap, connected } = useTrackingSocket(plates);

  // Group by routeCode
  const groups = useMemo(() => {
    const map = new Map<string, TrackingVehicleDto[]>();
    for (const v of initialVehicles) {
      const key = v.routeCode ?? "__none__";
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(v);
    }
    return [...map.entries()].sort(([a], [b]) => {
      if (a === "__none__") return 1;
      if (b === "__none__") return -1;
      return a.localeCompare(b);
    });
  }, [initialVehicles]);

  const totalActive = useMemo(
    () => initialVehicles.filter((v) => liveMap.get(v.busId)?.tripId != null || v.activeTripId).length,
    [initialVehicles, liveMap]
  );

  const routeCount = groups.filter(([k]) => k !== "__none__").length;

  return (
    <div className="max-w-4xl mx-auto w-full space-y-5">

      {/* ── Page header ── */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 shrink-0">
            <RadioTower className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h1 className="text-[20px] font-bold text-foreground leading-tight">Live Fleet</h1>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {isLoading ? "Loading…" : `${totalActive} active · ${routeCount} route${routeCount !== 1 ? "s" : ""} · ${initialVehicles.length} bus${initialVehicles.length !== 1 ? "es" : ""}`}
            </p>
          </div>
        </div>

        {connected ? (
          <span className="flex items-center gap-1.5 rounded-full border border-[#2E6B1A]/30 bg-[#2E6B1A]/8 px-3 py-1.5 shrink-0">
            <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
            <span className="text-[11px] font-semibold text-[#2E6B1A]">Live</span>
          </span>
        ) : (
          <span className="flex items-center gap-1.5 rounded-full border border-border bg-muted px-3 py-1.5 shrink-0">
            <WifiOff className="h-3 w-3 text-muted-foreground" />
            <span className="text-[11px] font-medium text-muted-foreground">Reconnecting…</span>
          </span>
        )}
      </div>

      {/* ── Stats strip (only when data is loaded) ── */}
      {!isLoading && initialVehicles.length > 0 && (
        <div className="grid grid-cols-3 gap-3">
          {[
            { icon: Bus,      label: "Total",  value: initialVehicles.length, color: "text-primary", bg: "bg-primary/8" },
            { icon: Activity, label: "Active", value: totalActive,             color: "text-[#2E6B1A]", bg: "bg-[#2E6B1A]/8" },
            { icon: RadioTower, label: "Routes", value: routeCount,            color: "text-primary", bg: "bg-primary/8" },
          ].map(({ icon: Icon, label, value, color, bg }) => (
            <div key={label} className="rounded-xl border border-border/60 bg-card px-4 py-3 flex items-center gap-3">
              <div className={`flex h-8 w-8 items-center justify-center rounded-lg ${bg}`}>
                <Icon className={`h-4 w-4 ${color}`} />
              </div>
              <div>
                <p className="text-[18px] font-bold text-foreground leading-none">{value}</p>
                <p className="text-[10px] text-muted-foreground mt-0.5">{label}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Skeleton ── */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-28 rounded-xl bg-card border border-border/60 animate-pulse" />
          ))}
        </div>
      )}

      {/* ── Empty state ── */}
      {!isLoading && initialVehicles.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-border/60 bg-card py-20">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
            <RadioTower className="h-6 w-6 text-muted-foreground opacity-40" />
          </div>
          <div className="text-center">
            <p className="text-[14px] font-bold text-foreground">No vehicles registered</p>
            <p className="text-[12px] text-muted-foreground mt-1">
              Register a bus and assign it to a route to start tracking.
            </p>
          </div>
        </div>
      )}

      {/* ── Route groups ── */}
      {!isLoading && groups.map(([routeCode, vehicles]) => (
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
