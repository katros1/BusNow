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
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
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
 * Bus icon and all stop dots are centered ON the rail line (same Y axis).
 * Labels alternate above / below the rail.
 */
export function RouteLine({ routeDetail, liveEvent, hasActiveTrip }: RouteLineProps) {
  const stops = useMemo(
    () => [...routeDetail.stops].sort((a, b) => a.sequence - b.sequence),
    [routeDetail.stops]
  );

  // Backend returns [lon, lat] — swap to [lat, lon] for all geo calculations.
  // Also normalise direction: if the route geometry was drawn end→start (reversed),
  // flip it so path[0] is always near startBusPark.
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
  // Priority: server progressPercent → client pathProgress → null.
  const busProgress: number | null = useMemo(() => {
    if (!liveEvent || liveEvent.gpsValid === false) return null;
    if (liveEvent.progressPercent != null) return liveEvent.progressPercent / 100;
    if (liveEvent.latitude != null && liveEvent.longitude != null && path.length >= 2) {
      return pathProgress(liveEvent.latitude, liveEvent.longitude, path);
    }
    return null;
  }, [liveEvent, path]);

  // Stop dot positions (0–1) along the normalised path
  const stopProgs = useMemo(() => {
    if (!stops.length) return [];
    if (path.length < 2) return stops.map((_, i) => (i + 1) / (stops.length + 1));
    return stopProgresses(stops, path);
  }, [stops, path]);

  const atStop     = liveEvent?.currentStopName ?? null;
  const nextStop   = liveEvent?.nextStopName    ?? null;
  const distToNext = liveEvent?.distanceToNextStopM ?? null;
  const distToTerm = liveEvent?.distanceToTerminalM ?? null;
  const busColor   = hasActiveTrip ? "#22c55e" : "#3b82f6";

  // ── Rail geometry constants ────────────────────────────────────────────────
  // Everything (bus, dots, rail) shares the same Y centre inside the 80 px track div.
  const RAIL_Y    = 40; // px from top of track container — rail centre line
  const TRACK_H   = 80; // px — total height of the track container

  return (
    <div className="bg-card">

      {/* ── Sub-header: route name + next stop + speed ─────────────────────── */}
      <div className="flex items-center justify-between gap-2 px-4 py-2 border-b border-border/30">
        <div className="flex-1 min-w-0 text-[11px] leading-snug">
          <span className="font-bold text-foreground truncate block">{routeDetail.name}</span>
          {atStop ? (
            <span className="text-[#22c55e] font-semibold">◉ At {atStop}
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
            <span className="text-orange-500 font-semibold">
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

      {/* ── Track ─────────────────────────────────────────────────────────── */}
      <div className="flex px-3" style={{ height: TRACK_H }}>

        {/* Start terminal — icon sits ON the rail, name below */}
        <div className="shrink-0 w-[70px] relative">
          <div
            className="absolute flex items-center justify-center rounded-full text-[11px] shadow-sm"
            style={{
              width: 22, height: 22,
              top: RAIL_Y, left: "50%",
              transform: "translate(-50%, -50%)",
              background: "rgba(34,197,94,0.12)",
              border: "1.5px solid rgba(34,197,94,0.3)",
            }}
          >🚏</div>
          <p
            className="absolute text-[6.5px] font-semibold text-muted-foreground text-center leading-tight px-1"
            style={{ top: RAIL_Y + 13, left: 0, right: 0 }}
          >
            {routeDetail.startBusPark.name}
          </p>
        </div>

        {/* Rail area */}
        <div className="relative flex-1" style={{ height: TRACK_H }}>

          {/* Base rail — grey */}
          <div
            className="absolute left-0 right-0 h-[2px] bg-border/55 rounded-full"
            style={{ top: RAIL_Y - 1 }}
          />

          {/* Progress fill — primary colour, left → bus */}
          {busProgress != null && (
            <div
              className="absolute left-0 h-[2px] rounded-full transition-all duration-1000"
              style={{ top: RAIL_Y - 1, width: `${busProgress * 100}%`, background: busColor }}
            />
          )}

          {/* Stop dots + alternating labels */}
          {stops.map((stop, i) => {
            const leftPct = `${(stopProgs[i] ?? 0) * 100}%`;
            const above   = i % 2 === 1;                              // odd → above rail
            const passed  = busProgress != null && (stopProgs[i] ?? 0) < busProgress - 0.01;
            const current = atStop === stop.name;

            return (
              <div
                key={stop.id}
                className="absolute z-[5]"
                style={{ left: leftPct, top: RAIL_Y, transform: "translate(-50%, -50%)" }}
              >
                {/* Pulse ring on current stop */}
                {current && (
                  <span
                    className="absolute rounded-full pulse-live"
                    style={{
                      inset: -4,
                      border: "1.5px solid #22c55e",
                      opacity: 0.55,
                      borderRadius: "50%",
                    }}
                  />
                )}

                {/* Dot */}
                <div
                  className={cn(
                    "h-[10px] w-[10px] rounded-full transition-colors duration-500",
                    current ? "bg-[#22c55e]"
                    : passed ? "bg-primary"
                    : "bg-border"
                  )}
                />

                {/* Label */}
                <p
                  className="absolute whitespace-nowrap text-[6.5px] font-medium text-muted-foreground leading-none"
                  style={{
                    left: "50%",
                    transform: "translateX(-50%)",
                    ...(above
                      ? { bottom: "calc(100% + 7px)" }
                      : { top:    "calc(100% + 7px)" }),
                  }}
                >
                  {stop.name}
                </p>
              </div>
            );
          })}

          {/* Bus icon — centered ON the rail at busProgress */}
          {busProgress != null && (
            <div
              className="absolute z-10 transition-all duration-1000 ease-in-out"
              style={{
                left: `${busProgress * 100}%`,
                top: RAIL_Y,
                transform: "translate(-50%, -50%)",
              }}
            >
              <div
                className="relative flex h-[28px] w-[28px] items-center justify-center rounded-full border-[2px] border-white shadow-md"
                style={{ background: busColor }}
              >
                {BUS_SVG}
                {hasActiveTrip && (
                  <span
                    className="absolute rounded-full pulse-live"
                    style={{
                      inset: -5,
                      border: `2px solid ${busColor}`,
                      opacity: 0.22,
                      borderRadius: "50%",
                    }}
                  />
                )}
              </div>
            </div>
          )}

          {/* No-position placeholder */}
          {busProgress == null && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <p className="text-[9px] text-muted-foreground/50 bg-card/80 rounded px-2 py-0.5">
                No position
              </p>
            </div>
          )}
        </div>

        {/* End terminal — icon sits ON the rail, name + distance below */}
        <div className="shrink-0 w-[70px] relative">
          <div
            className="absolute flex items-center justify-center rounded-full text-[11px] shadow-sm"
            style={{
              width: 22, height: 22,
              top: RAIL_Y, left: "50%",
              transform: "translate(-50%, -50%)",
              background: "rgba(239,68,68,0.12)",
              border: "1.5px solid rgba(239,68,68,0.28)",
            }}
          >🏁</div>
          <p
            className="absolute text-[6.5px] font-semibold text-muted-foreground text-center leading-tight px-1"
            style={{ top: RAIL_Y + 13, left: 0, right: 0 }}
          >
            {routeDetail.endBusPark.name}
          </p>
          {distToTerm != null && hasActiveTrip && (
            <p
              className="absolute text-[6px] text-muted-foreground/55 text-center"
              style={{ top: RAIL_Y + 24, left: 0, right: 0 }}
            >
              {formatDistance(distToTerm)}
            </p>
          )}
        </div>

      </div>
    </div>
  );
}
