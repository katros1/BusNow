import { useState, useMemo } from "react";
import { useNavigate } from "@tanstack/react-router";
import { ChevronDown, ChevronUp, Bus, MapPin, Navigation, AlertTriangle, Wifi } from "lucide-react";
import { cn } from "@/lib/utils";
import { formatDistance } from "../utils/geo";
import type { TrackingVehicleDto, VehicleLiveSnapshot } from "../api/tracking.types";

interface RouteGroupProps {
  routeCode: string;
  vehicles: TrackingVehicleDto[];
  liveMap: Map<string, VehicleLiveSnapshot>;
}

function OccupancyBar({ onBoard, capacity }: { onBoard: number; capacity: number | null }) {
  if (!capacity) return null;
  const pct = Math.min(100, Math.round((onBoard / capacity) * 100));
  const color = pct >= 90 ? "bg-red-500" : pct >= 70 ? "bg-yellow-500" : "bg-[#91D06C]";
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1 rounded-full bg-border/60 overflow-hidden">
        <div className={cn("h-full rounded-full transition-all duration-700", color)} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-[10px] tabular-nums text-muted-foreground shrink-0">
        {onBoard}/{capacity}
      </span>
    </div>
  );
}

function BusCard({ v, live, onClick }: {
  v: TrackingVehicleDto;
  live: VehicleLiveSnapshot | null;
  onClick: () => void;
}) {
  const isActive  = !!(live?.tripId ?? v.activeTripId);
  const isStale   = live?.gpsStale === true;
  const hasGps    = live?.gpsValid === true;
  const onBoard   = live?.passengersOnBoard ?? v.passengersOnBoard ?? 0;
  const speed     = live?.speedKmh != null && hasGps ? Math.round(live.speedKmh) : null;
  const atStop    = live?.currentStopName ?? null;
  const nextStop  = live?.nextStopName ?? null;
  const distNext  = live?.distanceToNextStopM ?? null;

  return (
    <button
      onClick={onClick}
      className={cn(
        "w-full text-left rounded-xl border bg-background px-4 py-3.5 space-y-2",
        "hover:bg-muted/40 hover:border-primary/30 active:scale-[.99] transition-all duration-150",
        isActive ? "border-border/70" : "border-border/40 opacity-80"
      )}
    >
      {/* ── Row 1: plate + badges ── */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <div className={cn(
            "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg",
            isActive ? "bg-[#2E6B1A]/10" : "bg-muted"
          )}>
            <Bus className={cn("h-3.5 w-3.5", isActive ? "text-[#2E6B1A]" : "text-muted-foreground")} />
          </div>
          <span className="text-[13px] font-bold text-foreground truncate">{v.plateNumber}</span>
          {v.model && <span className="text-[10px] text-muted-foreground truncate hidden sm:block">{v.model}</span>}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {isStale && (
            <span className="flex items-center gap-1 rounded-full bg-orange-50 border border-orange-200 px-2 py-0.5">
              <AlertTriangle className="h-2.5 w-2.5 text-orange-600" />
              <span className="text-[9px] font-bold text-orange-700">Stale</span>
            </span>
          )}
          {!hasGps && live && !isStale && (
            <span className="text-[9px] font-bold text-yellow-600 bg-yellow-50 border border-yellow-200 rounded-full px-2 py-0.5">No GPS</span>
          )}
          {speed != null && (
            <span className="text-[10px] font-semibold tabular-nums text-muted-foreground bg-muted rounded-full px-2 py-0.5">
              {speed} km/h
            </span>
          )}
          {isActive ? (
            <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 border border-[#2E6B1A]/20 px-2 py-0.5">
              <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
              <span className="text-[9px] font-bold text-[#2E6B1A]">Active</span>
            </span>
          ) : (
            <span className="text-[9px] font-medium text-muted-foreground bg-muted rounded-full px-2 py-0.5">Idle</span>
          )}
        </div>
      </div>

      {/* ── Row 2: stop info ── */}
      {isActive && (
        <div className="flex items-start gap-1.5 text-[11px]">
          <MapPin className="h-3 w-3 text-muted-foreground shrink-0 mt-0.5" />
          {atStop ? (
            <span className="text-[#2E6B1A] font-semibold">At {atStop}</span>
          ) : nextStop ? (
            <span className="text-foreground">
              <span className="text-muted-foreground">→ </span>
              <span className="font-medium">{nextStop}</span>
              {distNext != null && (
                <span className="text-muted-foreground"> · {formatDistance(distNext)}</span>
              )}
            </span>
          ) : (
            <span className="text-muted-foreground">In transit</span>
          )}
        </div>
      )}

      {!isActive && (
        <p className="text-[11px] text-muted-foreground">
          {v.routeName ?? "No route assigned"}
        </p>
      )}

      {/* ── Row 3: occupancy ── */}
      {isActive && (
        <OccupancyBar onBoard={onBoard} capacity={v.capacity ?? null} />
      )}
    </button>
  );
}

export function RouteGroup({ routeCode, vehicles, liveMap }: RouteGroupProps) {
  const navigate = useNavigate();
  const [expanded, setExpanded] = useState(true);

  const enriched = useMemo(
    () => vehicles.map((v) => ({ v, live: liveMap.get(v.busId) ?? null })),
    [vehicles, liveMap]
  );

  const activeCount = enriched.filter(({ v, live }) => live?.tripId != null || v.activeTripId).length;

  const routeName = useMemo(() => {
    const fromLive = enriched.find(({ live }) => live?.routeName)?.live?.routeName;
    if (fromLive) return fromLive;
    return enriched.find(({ v }) => v.routeName)?.v?.routeName ?? `Route ${routeCode}`;
  }, [enriched, routeCode]);

  return (
    <div className="rounded-xl border border-border/60 bg-card shadow-ambient overflow-hidden">
      {/* ── Group header ── */}
      <button
        onClick={() => setExpanded((e) => !e)}
        className="flex w-full items-center gap-3 px-5 py-4 hover:bg-muted/30 transition-colors text-left"
      >
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/10">
          <Bus className="h-4.5 w-4.5 text-primary" />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-black uppercase tracking-widest text-primary bg-primary/10 rounded px-1.5 py-0.5">
              {routeCode}
            </span>
            <span className="text-[13px] font-bold text-foreground truncate">{routeName}</span>
          </div>
          <p className="text-[11px] text-muted-foreground mt-0.5">
            {vehicles.length} bus{vehicles.length !== 1 ? "es" : ""} · {activeCount} active
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {activeCount > 0 && (
            <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 border border-[#2E6B1A]/20 px-2.5 py-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
              <span className="text-[10px] font-bold text-[#2E6B1A]">{activeCount} live</span>
            </span>
          )}
          {expanded
            ? <ChevronUp className="h-4 w-4 text-muted-foreground" />
            : <ChevronDown className="h-4 w-4 text-muted-foreground" />}
        </div>
      </button>

      {/* ── Bus cards ── */}
      {expanded && (
        <div className="border-t border-border/40 p-4 grid grid-cols-1 sm:grid-cols-2 gap-3">
          {enriched.map(({ v, live }) => (
            <BusCard
              key={v.busId}
              v={v}
              live={live}
              onClick={() => navigate({ to: "/tracking/$busId", params: { busId: v.busId } })}
            />
          ))}
        </div>
      )}
    </div>
  );
}
