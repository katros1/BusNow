import { useMemo } from "react";
import { cn } from "@/lib/utils";
import { pathProgress, stopProgresses, nearestStop, formatDistance } from "../utils/geo";
import type { RouteDetailDto, VehiclePositionEvent } from "../api/tracking.types";

interface RouteLineProps {
  routeDetail: RouteDetailDto;
  liveEvent: VehiclePositionEvent | undefined;
  plateNumber: string;
}

const BUS_SVG = (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
    stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="7" width="20" height="12" rx="2" />
    <path d="M7 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2" />
    <circle cx="7" cy="19" r="2" /><circle cx="17" cy="19" r="2" />
  </svg>
);

export function RouteLine({ routeDetail, liveEvent, plateNumber }: RouteLineProps) {
  const stops = useMemo(
    () => [...routeDetail.stops].sort((a, b) => a.sequence - b.sequence),
    [routeDetail.stops]
  );
  const path = routeDetail.routePath;

  const busProgress = useMemo(() => {
    const lat = liveEvent?.latitude;
    const lon = liveEvent?.longitude;
    if (!lat || !lon || !liveEvent?.gpsValid || path.length < 2) return null;
    return pathProgress(lat, lon, path);
  }, [liveEvent, path]);

  const stopProgs = useMemo(() => {
    if (!stops.length) return [];
    if (path.length < 2) return stops.map((_, i) => (i + 1) / (stops.length + 1));
    return stopProgresses(stops, path);
  }, [stops, path]);

  const nextStop = useMemo(() => {
    if (!liveEvent?.latitude || !liveEvent?.longitude || !stops.length) return null;
    return nearestStop(liveEvent.latitude, liveEvent.longitude, stops);
  }, [liveEvent, stops]);

  const isActive = !!(liveEvent?.trip);
  const busColor = isActive ? "#91D06C" : "#4C8CE4";

  return (
    <div className="flex flex-col bg-card">
      {/* Sub-header: route name + next stop + speed */}
      <div className="flex items-center justify-between gap-3 px-5 py-3 border-b border-border/40">
        <div className="flex-1 min-w-0">
          <p className="text-[12px] font-bold text-foreground truncate">
            {routeDetail.name}
          </p>
          {nextStop ? (
            <p className="text-[10px] text-muted-foreground mt-0.5">
              <span className="font-semibold text-foreground">→ {nextStop.stop.name}</span>
              <span className="ml-1.5">· {formatDistance(nextStop.distanceM)}</span>
            </p>
          ) : (
            <p className="text-[10px] text-muted-foreground mt-0.5">
              {liveEvent?.gpsValid === false ? "GPS signal lost" : "Awaiting position…"}
            </p>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {liveEvent?.speedKmh != null && liveEvent.gpsValid && (
            <span className="rounded-full bg-[#2E6B1A]/10 px-2.5 py-0.5 text-[10px] font-bold text-[#2E6B1A]">
              {Math.round(liveEvent.speedKmh)} km/h
            </span>
          )}
          {isActive ? (
            <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2.5 py-0.5">
              <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
              <span className="text-[9px] font-bold uppercase tracking-widest text-[#2E6B1A]">Active</span>
            </span>
          ) : (
            <span className="text-[9px] font-medium text-muted-foreground">No active trip</span>
          )}
        </div>
      </div>

      {/* ── Line visualization ── */}
      <div className="flex items-center px-3 py-4" style={{ height: 136 }}>

        {/* Start park */}
        <div className="shrink-0 w-[86px] flex flex-col items-center gap-1 pr-1">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#2E6B1A]/15 text-[15px]">🚏</div>
          <p className="text-[7.5px] font-semibold text-muted-foreground text-center leading-tight line-clamp-3 w-full">
            {routeDetail.startBusPark.name}
          </p>
        </div>

        {/* Track (flex-1 so it fills remaining width) */}
        <div className="relative flex-1" style={{ height: 80 }}>
          {/* Background rail */}
          <div
            className="absolute left-0 right-0 h-[2px] bg-border/70 rounded-full"
            style={{ top: 32 }}
          />

          {/* Progress rail */}
          {busProgress != null && (
            <div
              className="absolute left-0 h-[2px] bg-primary rounded-full transition-all duration-1000"
              style={{ top: 32, width: `${busProgress * 100}%` }}
            />
          )}

          {/* Stop markers */}
          {stops.map((stop, i) => {
            const pct = `${(stopProgs[i] ?? 0) * 100}%`;
            const above = i % 2 === 1;
            const passed = busProgress != null && (stopProgs[i] ?? 0) < busProgress;
            return (
              <div
                key={stop.id}
                className="absolute"
                style={{ left: pct, top: 24, transform: "translateX(-50%)" }}
              >
                {/* Dot */}
                <div
                  className={cn(
                    "h-[16px] w-[16px] rounded-full border-2 border-card transition-colors duration-500",
                    passed ? "bg-primary" : "bg-border"
                  )}
                />
                {/* Label */}
                <div
                  className="absolute whitespace-nowrap text-[8px] font-medium text-muted-foreground"
                  style={{
                    left: "50%",
                    transform: "translateX(-50%)",
                    ...(above ? { bottom: 20 } : { top: 20 }),
                  }}
                >
                  {stop.name}
                </div>
              </div>
            );
          })}

          {/* Bus icon */}
          {busProgress != null && (
            <div
              className="absolute z-10 transition-all duration-1000 ease-in-out"
              style={{ left: `${busProgress * 100}%`, top: 12, transform: "translateX(-50%)" }}
            >
              <div
                className="relative flex h-[38px] w-[38px] items-center justify-center rounded-full border-[2.5px] border-white shadow-lg"
                style={{ background: busColor }}
              >
                {BUS_SVG}
                {isActive && (
                  <span
                    className="absolute inset-[-5px] rounded-full border-2 opacity-25 pulse-live"
                    style={{ borderColor: busColor }}
                  />
                )}
              </div>
            </div>
          )}

          {/* No position placeholder */}
          {busProgress == null && (
            <div className="absolute inset-0 flex items-center justify-center">
              <p className="text-[10px] text-muted-foreground/60 bg-card/80 rounded px-2 py-0.5">
                Position unknown
              </p>
            </div>
          )}
        </div>

        {/* End park */}
        <div className="shrink-0 w-[86px] flex flex-col items-center gap-1 pl-1">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#C0392B]/15 text-[15px]">🏁</div>
          <p className="text-[7.5px] font-semibold text-muted-foreground text-center leading-tight line-clamp-3 w-full">
            {routeDetail.endBusPark.name}
          </p>
        </div>
      </div>
    </div>
  );
}
