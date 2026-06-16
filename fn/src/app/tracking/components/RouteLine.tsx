import { useMemo } from "react";
import { cn } from "@/lib/utils";
import { stopProgresses, pathProgress, formatDistance, lonLatToLatLon, haversineM, centroid } from "../utils/geo";
import type { RouteDetailDto, VehicleLiveSnapshot } from "../api/tracking.types";

interface RouteLineProps {
  routeDetail: RouteDetailDto;
  liveEvent: VehicleLiveSnapshot | undefined;
  hasActiveTrip: boolean;
  plateNumber: string;
}

const BUS_SVG = (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
    stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="7" width="20" height="12" rx="2" />
    <path d="M7 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2" />
    <circle cx="7" cy="19" r="2" /><circle cx="17" cy="19" r="2" />
  </svg>
);

/**
 * Route line visualization.
 *
 * Layout invariant: startBusPark is ALWAYS on the left; endBusPark on the right.
 * The bus icon travels left → right matching progressPercent (0 = start, 100 = end).
 * This is correct for BOTH directions — each route entity has its own
 * startBusPark / endBusPark ordered for that direction's travel.
 */
export function RouteLine({ routeDetail, liveEvent, hasActiveTrip }: RouteLineProps) {
  const stops = useMemo(
    () => [...routeDetail.stops].sort((a, b) => a.sequence - b.sequence),
    [routeDetail.stops]
  );
  // Backend returns [lon, lat] — swap to [lat, lon] for all geo calculations.
  // Also normalise direction: if the route geometry was drawn end→start (reversed),
  // flip it so path[0] is always near startBusPark.  This ensures stop dots and the
  // client-side fallback progress both count from left (start) to right (end).
  const path = useMemo(() => {
    const raw = lonLatToLatLon(routeDetail.routePath);
    if (raw.length < 2) return raw;
    if ((routeDetail.startBusPark?.coordinates?.length ?? 0) >= 3) {
      const startC = centroid(lonLatToLatLon(routeDetail.startBusPark.coordinates));
      const dFirst = haversineM(raw[0][0],              raw[0][1],              startC[0], startC[1]);
      const dLast  = haversineM(raw[raw.length - 1][0], raw[raw.length - 1][1], startC[0], startC[1]);
      if (dLast < dFirst) return [...raw].reverse();
    }
    return raw;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeDetail.routePath, routeDetail.startBusPark?.coordinates]);

  // Bus position along the route (0–1).
  // Priority: server-computed progressPercent → client-computed from lat/lon → null.
  // The client-side fallback makes the icon visible from REST data before WS delivers
  // the first snapshot (which includes server-computed progressPercent).
  const busProgress: number | null = useMemo(() => {
    if (!liveEvent || liveEvent.gpsValid === false) return null;
    if (liveEvent.progressPercent != null) {
      return liveEvent.progressPercent / 100;
    }
    if (liveEvent.latitude != null && liveEvent.longitude != null && path.length >= 2) {
      return pathProgress(liveEvent.latitude, liveEvent.longitude, path);
    }
    return null;
  }, [liveEvent, path]);

  // Position of each stop dot along the bar (0–1)
  const stopProgs = useMemo(() => {
    if (!stops.length) return [];
    if (path.length < 2) return stops.map((_, i) => (i + 1) / (stops.length + 1));
    return stopProgresses(stops, path);
  }, [stops, path]);

  const atStop       = liveEvent?.currentStopName ?? null;
  const nextStop     = liveEvent?.nextStopName ?? null;
  const distToNext   = liveEvent?.distanceToNextStopM ?? null;
  const distToTerm   = liveEvent?.distanceToTerminalM ?? null;
  const busColor     = hasActiveTrip ? "#91D06C" : "#4C8CE4";

  return (
    <div className="bg-card">
      {/* Sub-header: route context + speed */}
      <div className="flex items-center justify-between gap-2 px-4 py-2 border-b border-border/30">
        <div className="flex-1 min-w-0 text-[11px] leading-snug">
          <span className="font-bold text-foreground truncate block">{routeDetail.name}</span>
          {atStop ? (
            <span className="text-[#91D06C] font-semibold">◉ At {atStop}
              {nextStop && <span className="text-muted-foreground font-normal"> · Next: {nextStop}</span>}
            </span>
          ) : !hasActiveTrip ? (
            <span className="text-muted-foreground">At terminal · {routeDetail.startBusPark.name}</span>
          ) : nextStop ? (
            <span className="text-foreground">
              → {nextStop}
              {distToNext != null && <span className="text-muted-foreground"> · {formatDistance(distToNext)}</span>}
            </span>
          ) : distToTerm != null ? (
            <span className="text-orange-600 font-semibold">
              ⚑ Approaching {routeDetail.endBusPark.name} · {formatDistance(distToTerm)}
            </span>
          ) : liveEvent?.gpsValid === false ? (
            <span className="text-muted-foreground">GPS lost</span>
          ) : liveEvent?.gpsValid === true ? (
            <span className="text-muted-foreground">Between stops</span>
          ) : (
            <span className="text-muted-foreground">Awaiting position…</span>
          )}
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {liveEvent?.progressPercent != null && hasActiveTrip && (
            <span className="text-[9px] font-bold text-primary bg-primary/10 rounded px-1.5 py-0.5">
              {Math.round(liveEvent.progressPercent)}%
            </span>
          )}
          {liveEvent?.speedKmh != null && liveEvent.gpsValid && !atStop && hasActiveTrip ? (
            <span className="text-[10px] font-semibold text-[#2E6B1A] bg-[#2E6B1A]/10 rounded-full px-2 py-0.5">
              {Math.round(liveEvent.speedKmh)} km/h
            </span>
          ) : (atStop || !hasActiveTrip) ? (
            <span className="text-[9px] font-medium text-muted-foreground bg-muted rounded-full px-2 py-0.5">Stopped</span>
          ) : null}
        </div>
      </div>

      {/* ── Track ── */}
      <div className="flex items-center px-3 py-3" style={{ height: 108 }}>

        {/* Start park — always LEFT */}
        <div className="shrink-0 w-[80px] flex flex-col items-center gap-1 pr-1">
          <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-[#2E6B1A]/15 text-[13px]">🚏</div>
          <p className="text-[7px] font-semibold text-muted-foreground text-center leading-tight line-clamp-3 w-full">
            {routeDetail.startBusPark.name}
          </p>
        </div>

        {/* Rail */}
        <div className="relative flex-1" style={{ height: 72 }}>
          {/* Background rail */}
          <div className="absolute left-0 right-0 h-[2px] bg-border/60 rounded-full" style={{ top: 28 }} />

          {/* Progress (left → right, always) */}
          {busProgress != null && (
            <div
              className="absolute left-0 h-[2px] bg-primary rounded-full transition-all duration-1000"
              style={{ top: 28, width: `${busProgress * 100}%` }}
            />
          )}

          {/* Stop dots */}
          {stops.map((stop, i) => {
            const pct     = `${(stopProgs[i] ?? 0) * 100}%`;
            const above   = i % 2 === 1;
            const passed  = busProgress != null && (stopProgs[i] ?? 0) < busProgress;
            const current = atStop === stop.name;
            return (
              <div key={stop.id} className="absolute" style={{ left: pct, top: 20, transform: "translateX(-50%)" }}>
                <div className="relative flex items-center justify-center">
                  {current && (
                    <span className="absolute inset-[-4px] rounded-full border-2 border-[#91D06C] opacity-60 pulse-live" />
                  )}
                  <div className={cn(
                    "h-[16px] w-[16px] rounded-full border-2 border-card transition-colors duration-500",
                    current ? "bg-[#91D06C]" : passed ? "bg-primary" : "bg-border"
                  )} />
                </div>
                <div
                  className="absolute whitespace-nowrap text-[7.5px] font-medium text-muted-foreground"
                  style={{ left: "50%", transform: "translateX(-50%)", ...(above ? { bottom: 18 } : { top: 18 }) }}
                >
                  {stop.name}
                </div>
              </div>
            );
          })}

          {/* Bus icon — moves left → right */}
          {busProgress != null && (
            <div
              className="absolute z-10 transition-all duration-1000 ease-in-out"
              style={{ left: `${busProgress * 100}%`, top: 8, transform: "translateX(-50%)" }}
            >
              <div
                className="relative flex h-[36px] w-[36px] items-center justify-center rounded-full border-2 border-white shadow-lg"
                style={{ background: busColor }}
              >
                {BUS_SVG}
                {hasActiveTrip && (
                  <span
                    className="absolute inset-[-5px] rounded-full border-2 opacity-20 pulse-live"
                    style={{ borderColor: busColor }}
                  />
                )}
              </div>
            </div>
          )}

          {busProgress == null && (
            <div className="absolute inset-0 flex items-center justify-center">
              <p className="text-[9px] text-muted-foreground/50 bg-card/80 rounded px-2 py-0.5">No position</p>
            </div>
          )}
        </div>

        {/* End park — always RIGHT */}
        <div className="shrink-0 w-[80px] flex flex-col items-center gap-1 pl-1">
          <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-[#C0392B]/15 text-[13px]">🏁</div>
          <p className="text-[7px] font-semibold text-muted-foreground text-center leading-tight line-clamp-3 w-full">
            {routeDetail.endBusPark.name}
          </p>
          {distToTerm != null && hasActiveTrip && (
            <p className="text-[7px] text-muted-foreground/60">{formatDistance(distToTerm)}</p>
          )}
        </div>
      </div>
    </div>
  );
}
