import { Bus, Navigation, Users, WifiOff } from "lucide-react";
import { useNavigate } from "@tanstack/react-router";
import { cn } from "@/lib/utils";
import type { TrackingVehicleDto, VehiclePositionEvent } from "../api/tracking.types";

interface Props {
  vehicles: Map<string, VehiclePositionEvent>;
  initialVehicles: TrackingVehicleDto[];
  connected: boolean;
}

function getStatus(live: VehiclePositionEvent | undefined, hasPos: boolean) {
  if (!live && !hasPos) return { dot: "bg-muted-foreground/30", label: "Unknown" };
  if (!live)           return { dot: "bg-muted-foreground/40", label: "Idle" };
  if (!live.gpsValid)  return { dot: "bg-yellow-400", label: "No GPS" };
  if (live.trip)       return { dot: "bg-[#91D06C] pulse-live", label: "Active" };
  return               { dot: "bg-[#4C8CE4]", label: "Online" };
}

export function VehicleListPanel({ vehicles, initialVehicles, connected }: Props) {
  const navigate = useNavigate();
  const activeCount = [...vehicles.values()].filter((v) => v.trip).length;

  return (
    <div
      className={cn(
        "absolute top-3 left-3 z-[400]",
        "w-[280px] rounded-xl overflow-hidden flex flex-col",
        "bg-card/95 backdrop-blur-md border border-border/60 shadow-ambient-md"
      )}
    >
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border/40 bg-surface-container/30 shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10">
            <Bus className="h-3.5 w-3.5 text-primary" />
          </div>
          <div>
            <p className="text-[13px] font-bold text-foreground leading-tight">
              Fleet Overview
            </p>
            <p className="text-[10px] text-muted-foreground">
              {activeCount} active trip{activeCount !== 1 ? "s" : ""}
            </p>
          </div>
        </div>

        {connected ? (
          <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2 py-0.5">
            <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
            <span className="text-[9px] font-bold uppercase tracking-widest text-[#2E6B1A]">
              Live
            </span>
          </span>
        ) : (
          <span className="flex items-center gap-1 rounded-full bg-muted px-2 py-0.5">
            <WifiOff className="h-2.5 w-2.5 text-muted-foreground" />
            <span className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground">
              Offline
            </span>
          </span>
        )}
      </div>

      {/* ── Vehicle list ── */}
      <div
        className="flex flex-col divide-y divide-border/30 overflow-y-auto"
        style={{ maxHeight: "calc(100vh - 220px)" }}
      >
        {initialVehicles.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 gap-2 text-muted-foreground">
            <Bus className="h-7 w-7 opacity-20" />
            <p className="text-[12px]">No vehicles registered</p>
          </div>
        ) : (
          initialVehicles.map((v) => {
            const live   = vehicles.get(v.busId);
            const status = getStatus(live, !!(v.latitude || v.longitude));
            const speed  = live?.speedKmh != null ? Math.round(live.speedKmh) : null;
            const onBoard = live?.trip?.onBoard ?? v.passengersOnBoard;
            const code    = live?.route?.code ?? v.routeCode;
            const dir     = live?.route?.direction ?? v.direction;

            return (
              <button
                key={v.busId}
                onClick={() =>
                  navigate({ to: "/tracking/$busId", params: { busId: v.busId } })
                }
                className="flex items-center gap-3 px-4 py-3 text-left w-full hover:bg-primary/5 active:bg-primary/10 transition-colors group"
              >
                {/* icon + status dot */}
                <div className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted group-hover:bg-primary/10 transition-colors">
                  <Bus className="h-4 w-4 text-muted-foreground group-hover:text-primary transition-colors" />
                  <span
                    className={cn(
                      "absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-card",
                      status.dot
                    )}
                  />
                </div>

                {/* details */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 mb-0.5">
                    <span className="text-[13px] font-bold text-foreground truncate">
                      {v.plateNumber}
                    </span>
                    {code && (
                      <span className="shrink-0 text-[8px] font-bold uppercase tracking-wide rounded bg-primary/10 text-primary px-1.5 py-0.5">
                        {code}
                        {dir === "FORWARD" ? " →" : dir === "BACKWARD" ? " ←" : ""}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                    {speed != null ? (
                      <span className="flex items-center gap-1">
                        <Navigation className="h-2.5 w-2.5" />
                        {speed} km/h
                      </span>
                    ) : (
                      <span>{v.model ?? "Unknown model"}</span>
                    )}
                    {onBoard != null && v.capacity && (
                      <span className="flex items-center gap-1">
                        <Users className="h-2.5 w-2.5" />
                        {onBoard}/{v.capacity}
                      </span>
                    )}
                  </div>
                </div>

                <span className="text-muted-foreground/30 group-hover:text-primary/40 transition-colors text-lg leading-none shrink-0">
                  ›
                </span>
              </button>
            );
          })
        )}
      </div>

      {/* ── Footer ── */}
      <div className="px-4 py-2 bg-surface-container/30 border-t border-border/40 flex items-center justify-between shrink-0">
        <span className="text-[10px] text-muted-foreground">
          {initialVehicles.length} bus{initialVehicles.length !== 1 ? "es" : ""} total
        </span>
        <span className="text-[10px] text-muted-foreground">Click to track →</span>
      </div>
    </div>
  );
}
