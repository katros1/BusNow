import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams, useNavigate, useSearch } from "@tanstack/react-router";
import {
  ArrowLeft, Bus, Clock, Users, Satellite, Map as MapIcon,
  WifiOff, AlertTriangle, MapPin, Navigation, Gauge, ChevronRight,
  ChevronDown,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { useVehicleSocket } from "../hooks/useVehicleSocket";
import { RouteLine } from "../components/RouteLine";
import { TrackingMapTab } from "../components/TrackingMapTab";
import { formatDistance } from "../utils/geo";
import type { VehicleLiveSnapshot } from "../api/tracking.types";

// ── Constants ────────────────────────────────────────────────────────────────
const NAVBAR_H = 60;

function elapsed(ts: string | null | undefined): string {
  if (!ts) return "—";
  const s = Math.max(0, Math.floor((Date.now() - new Date(ts).getTime()) / 1000));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : m === 0 ? "<1m" : `${m}m`;
}

// ── Small stat chip ───────────────────────────────────────────────────────────
function Chip({
  icon: Icon, value, label, sub, color = "text-primary", bg = "bg-primary/8", wide,
}: {
  icon: React.ElementType; value: React.ReactNode; label: string;
  sub?: string; color?: string; bg?: string; wide?: boolean;
}) {
  return (
    <div className={cn(
      "flex items-center gap-2 sm:gap-3 rounded-xl border border-border/60 bg-card px-3 py-2 sm:px-4 sm:py-3",
      wide ? "col-span-2" : ""
    )}>
      <div className={cn("flex h-7 w-7 sm:h-8 sm:w-8 shrink-0 items-center justify-center rounded-lg", bg)}>
        <Icon className={cn("h-3.5 w-3.5 sm:h-4 sm:w-4", color)} />
      </div>
      <div className="min-w-0">
        <p className="text-[13px] sm:text-[16px] font-bold text-foreground leading-none truncate">{value}</p>
        <p className="text-[9px] sm:text-[10px] text-muted-foreground mt-0.5 truncate">{label}</p>
        {sub && <p className="text-[9px] text-muted-foreground/70 truncate">{sub}</p>}
      </div>
    </div>
  );
}

// ── Occupancy bar ─────────────────────────────────────────────────────────────
function OccupancyChip({ onBoard, available, capacity }: {
  onBoard: number; available: number | null; capacity: number | null;
}) {
  const pct = capacity ? Math.min(100, Math.round((onBoard / capacity) * 100)) : null;
  const color = pct == null ? "text-primary" : pct >= 90 ? "text-red-600" : pct >= 70 ? "text-yellow-600" : "text-[#2E6B1A]";
  const barColor = pct == null ? "bg-primary" : pct >= 90 ? "bg-red-500" : pct >= 70 ? "bg-yellow-500" : "bg-[#91D06C]";

  return (
    <div className="rounded-xl border border-border/60 bg-card px-4 py-3 space-y-2 col-span-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/8">
            <Users className="h-4 w-4 text-primary" />
          </div>
          <div>
            <p className="text-[16px] font-bold text-foreground leading-none">{onBoard}</p>
            <p className="text-[10px] text-muted-foreground">on board</p>
          </div>
        </div>
        <div className="flex items-center gap-3 text-right">
          {available != null && (
            <div>
              <p className={cn("text-[16px] font-bold leading-none", color)}>{available}</p>
              <p className="text-[10px] text-muted-foreground">available</p>
            </div>
          )}
          {pct != null && (
            <div>
              <p className={cn("text-[14px] font-bold leading-none", color)}>{pct}%</p>
              <p className="text-[10px] text-muted-foreground">full</p>
            </div>
          )}
        </div>
      </div>
      {pct != null && (
        <div className="h-1.5 rounded-full bg-muted overflow-hidden">
          <div
            className={cn("h-full rounded-full transition-all duration-700", barColor)}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  );
}

// ── Seat availability badge ───────────────────────────────────────────────────
// Shown in the top bar so passengers always see availability at a glance.
// Color: green (seats free) → yellow (< 30% left) → red (full / 1 seat left)
function SeatBadge({ onBoard, available, capacity }: {
  onBoard: number; available: number | null; capacity: number;
}) {
  const free  = available ?? Math.max(0, capacity - onBoard);
  const pct   = Math.min(100, Math.round((onBoard / capacity) * 100));
  const isFull = free === 0;
  const isLow  = !isFull && pct >= 70;

  if (isFull) {
    return (
      <span className="flex items-center gap-1 rounded-full bg-red-50 border border-red-200 px-2 py-0.5">
        <span className="h-1.5 w-1.5 rounded-full bg-red-500" />
        <span className="text-[9px] font-bold text-red-700">FULL</span>
      </span>
    );
  }
  if (isLow) {
    return (
      <span className="flex items-center gap-1 rounded-full bg-yellow-50 border border-yellow-200 px-2 py-0.5">
        <span className="text-[9px] font-bold text-yellow-700">{free} seat{free !== 1 ? "s" : ""}</span>
      </span>
    );
  }
  return (
    <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/8 border border-[#2E6B1A]/15 px-2 py-0.5">
      <span className="text-[9px] font-semibold text-[#2E6B1A]">{free} seats</span>
    </span>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────
export default function VehicleTracking() {
  const navigate = useNavigate();
  const { busId } = useParams({ strict: false }) as { busId: string };
  // routeId passed from the fleet list so the map renders immediately,
  // before the WebSocket snapshot arrives with its own routeId.
  const { routeId: routeIdFromUrl } = useSearch({ strict: false }) as { routeId?: string };
  const [tab, setTab] = useState<"passengers" | "map" | "satellite">("passengers");
  // Collapse header on map/satellite tabs to maximise map area on small screens
  const [headerExpanded, setHeaderExpanded] = useState(true);

  // REST baseline
  const { data: allVehicles = [], isLoading } = useQuery({
    queryKey: trackingKeys.vehicles(),
    queryFn: trackingApi.getVehicles,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });

  const vehicle = useMemo(() => allVehicles.find((v) => v.busId === busId), [allVehicles, busId]);

  // WebSocket live updates — stable across direction toggles (subscribed by plate)
  const { snapshot: live, connected } = useVehicleSocket(vehicle?.plateNumber);

  // Effective snapshot: prefer live WS, fall back to REST
  const snap = useMemo((): VehicleLiveSnapshot | undefined => {
    if (live) return live;
    if (!vehicle) return undefined;
    return {
      busId:               vehicle.busId,
      plateNumber:         vehicle.plateNumber,
      routeId:             vehicle.routeId ?? null,
      routeCode:           vehicle.routeCode ?? null,
      routeName:           vehicle.routeName ?? null,
      latitude:            vehicle.latitude ?? null,
      longitude:           vehicle.longitude ?? null,
      speedKmh:            null,
      headingDeg:          null,
      gpsValid:            vehicle.latitude != null,
      gpsStale:            false,
      currentStopName:     null,
      nextStopName:        null,
      distanceToNextStopM: null,
      distanceToTerminalM: null,
      progressPercent:     null,
      passengersOnBoard:   vehicle.passengersOnBoard ?? 0,
      availableSeats:      vehicle.availableSeats ?? null,
      tripId:              vehicle.activeTripId ?? null,
      tripStartedAt:       vehicle.tripStartedAt ?? null,
      timestamp:           new Date().toISOString(),
    };
  }, [live, vehicle]);

  const hasTrip  = snap?.tripId != null;
  const isLive   = !!live;
  const gpsValid = snap?.gpsValid ?? false;
  const gpsStale = snap?.gpsStale ?? false;

  // Route detail:
  //  1. Live WS snapshot provides routeId (follows direction toggles instantly)
  //  2. REST vehicle fallback provides routeId (active trip)
  //  3. URL search param ?routeId= provides it immediately on first render,
  //     before any snapshot arrives — so the map shows the route right away.
  const routeId = snap?.routeId ?? routeIdFromUrl ?? null;

  const { data: routeDetail } = useQuery({
    queryKey: trackingKeys.route(routeId ?? ""),
    queryFn:  () => trackingApi.getRouteDetail(routeId!),
    enabled:  !!routeId,
    staleTime: 120_000,
  });

  // Auto-collapse header when user switches to map/satellite to give map max space
  const isMapTab = tab === "map" || tab === "satellite";

  // ── Not found ──────────────────────────────────────────────────────────────
  if (!isLoading && !vehicle) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-20 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
          <Bus className="h-6 w-6 text-muted-foreground" />
        </div>
        <div>
          <p className="text-[15px] font-bold text-foreground">Vehicle not found</p>
          <p className="text-[13px] text-muted-foreground mt-1">No vehicle with this ID is registered.</p>
        </div>
        <button
          onClick={() => navigate({ to: "/tracking" })}
          className="flex items-center gap-1.5 text-[13px] font-medium text-primary hover:underline"
        >
          <ArrowLeft className="h-3.5 w-3.5" /> Back to Fleet
        </button>
      </div>
    );
  }

  return (
    <div className="-m-5 lg:-m-7 flex flex-col overflow-hidden" style={{ height: `calc(100vh - ${NAVBAR_H}px)` }}>

      {/* ══ HEADER ════════════════════════════════════════════════════════════ */}
      <div className={cn(
        "shrink-0 bg-card border-b border-border/50",
        // On map tabs: auto-collapse the detail section on small screens
        isMapTab && "sm:block"
      )}>

        {/* Top bar */}
        <div className="flex items-center gap-3 px-4 py-3">
          <button
            onClick={() => navigate({ to: "/tracking" })}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>

          <div className="flex items-center gap-2 flex-1 min-w-0">
            <span className="text-[15px] font-bold text-foreground truncate">
              {vehicle?.plateNumber ?? "—"}
            </span>
            {vehicle?.model && (
              <span className="text-[11px] text-muted-foreground shrink-0 hidden sm:block">
                · {vehicle.model}
              </span>
            )}
            {snap?.routeCode && (
              <span className="shrink-0 text-[9px] font-black uppercase tracking-widest rounded bg-primary/10 text-primary px-1.5 py-0.5">
                {snap.routeCode}
              </span>
            )}
          </div>

          {/* Status badges */}
          <div className="flex items-center gap-1.5 shrink-0">
            {gpsStale && (
              <span className="hidden sm:flex items-center gap-1 rounded-full bg-orange-50 border border-orange-200 px-2 py-0.5">
                <AlertTriangle className="h-2.5 w-2.5 text-orange-500" />
                <span className="text-[9px] font-bold text-orange-700">GPS Stale</span>
              </span>
            )}
            {!gpsValid && snap && !gpsStale && (
              <span className="hidden sm:flex items-center gap-1 rounded-full bg-yellow-50 border border-yellow-200 px-2 py-0.5">
                <AlertTriangle className="h-2.5 w-2.5 text-yellow-500" />
                <span className="text-[9px] font-bold text-yellow-700">No GPS</span>
              </span>
            )}

            {/* ── Seat availability — always visible when trip is active ── */}
            {hasTrip && snap && vehicle?.capacity != null && (
              <SeatBadge
                onBoard={snap.passengersOnBoard}
                available={snap.availableSeats}
                capacity={vehicle.capacity}
              />
            )}

            {connected ? (
              <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 border border-[#2E6B1A]/20 px-2 py-0.5">
                <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
                <span className="text-[9px] font-bold text-[#2E6B1A]">Live</span>
              </span>
            ) : (
              <span className="flex items-center gap-1 rounded-full bg-muted px-2 py-0.5">
                <WifiOff className="h-2.5 w-2.5 text-muted-foreground" />
                <span className="text-[9px] text-muted-foreground">Offline</span>
              </span>
            )}
          </div>
        </div>

        {/* ── Collapse toggle — visible on small screens when on map tabs ── */}
        {isMapTab && (
          <button
            onClick={() => setHeaderExpanded(e => !e)}
            className="flex w-full items-center justify-between px-4 py-1.5 border-t border-border/30 sm:hidden text-muted-foreground hover:bg-muted/40 transition-colors"
          >
            <span className="text-[10px] font-medium">
              {headerExpanded ? "Hide route details" : "Show route details"}
            </span>
            <ChevronDown className={cn(
              "h-3.5 w-3.5 transition-transform duration-200",
              !headerExpanded && "rotate-180"
            )} />
          </button>
        )}

        {/* ── Route line ─────────────────────────────────────────────────── */}
        <div className={cn(isMapTab && !headerExpanded && "hidden sm:block")}>
          {routeDetail ? (
            <RouteLine
              routeDetail={routeDetail}
              liveEvent={snap}
              hasActiveTrip={hasTrip}
              plateNumber={vehicle?.plateNumber ?? ""}
            />
          ) : (
            <div className="flex items-center justify-center h-20 sm:h-[136px] text-muted-foreground gap-2">
              {routeId ? (
                <>
                  <div className="h-3.5 w-3.5 rounded-full border-2 border-primary/30 border-t-primary animate-spin" />
                  <span className="text-[12px]">Loading route…</span>
                </>
              ) : (
                <>
                  <Bus className="h-4 w-4 opacity-30" />
                  <span className="text-[12px]">No route assigned</span>
                </>
              )}
            </div>
          )}
        </div>

        {/* ── Live stat chips ─────────────────────────────────────────────── */}
        {snap && (
          <div className={cn(
            "grid grid-cols-2 gap-1.5 sm:gap-2.5 px-3 sm:px-4 pb-3 sm:pb-4 pt-2 sm:pt-3 border-t border-border/40",
            isMapTab && !headerExpanded && "hidden sm:grid"
          )}>
            <Chip
              icon={Gauge}
              value={gpsValid && snap.speedKmh != null ? `${Math.round(snap.speedKmh)} km/h` : "—"}
              label="Speed"
              color="text-primary"
              bg="bg-primary/8"
            />
            <Chip
              icon={MapPin}
              value={
                snap.currentStopName
                  ? snap.currentStopName
                  : !hasTrip
                    ? "At terminal"
                    : (snap.progressPercent ?? 0) >= 95
                      ? routeDetail?.endBusPark.name ?? "At terminal"
                      : "In transit"
              }
              label={
                snap.currentStopName
                  ? "At stop"
                  : !hasTrip
                    ? "Terminal"
                    : (snap.progressPercent ?? 0) >= 95
                      ? "Arrived at terminal"
                      : "Current stop"
              }
              color="text-[#2E6B1A]"
              bg="bg-[#2E6B1A]/8"
            />
            <Chip
              icon={Navigation}
              value={
                snap.nextStopName != null
                  ? snap.nextStopName
                  : hasTrip
                    ? (routeDetail?.endBusPark.name ?? "Terminal")
                    : "—"
              }
              label={
                snap.nextStopName != null
                  ? (snap.distanceToNextStopM != null ? `Next · ${formatDistance(snap.distanceToNextStopM)}` : "Next stop")
                  : hasTrip && snap.distanceToTerminalM != null
                    ? `Approaching terminal · ${formatDistance(snap.distanceToTerminalM)}`
                    : hasTrip
                      ? "Approaching terminal"
                      : "Next stop"
              }
              color={snap.nextStopName == null && hasTrip ? "text-orange-600" : "text-muted-foreground"}
              bg={snap.nextStopName == null && hasTrip ? "bg-orange-50" : "bg-muted"}
            />
            {hasTrip ? (
              <Chip
                icon={Clock}
                value={elapsed(snap.tripStartedAt ?? vehicle?.tripStartedAt)}
                label="Trip duration"
                color="text-primary"
                bg="bg-primary/8"
                sub={!isLive ? "from snapshot" : undefined}
              />
            ) : (
              <Chip
                icon={Clock}
                value="—"
                label="No active trip"
                color="text-muted-foreground"
                bg="bg-muted"
              />
            )}
          </div>
        )}
      </div>

      {/* ══ TABS ══════════════════════════════════════════════════════════════ */}
      {/* min-h-0 lets the flex child shrink below content height so the map fills remaining space */}
      <div className="flex-1 flex flex-col overflow-hidden min-h-0">
        <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)} className="flex flex-col h-full">

          <div className="shrink-0 bg-card border-b border-border/50 px-4">
            <TabsList className="h-[44px] bg-transparent gap-0 p-0">
              {(["passengers", "map", "satellite"] as const).map((t) => {
                const cfg = {
                  passengers: { icon: Users,     label: "Passengers" },
                  map:        { icon: MapIcon,   label: "Map" },
                  satellite:  { icon: Satellite, label: "Satellite" },
                } as const;
                const { icon: Icon, label } = cfg[t];
                return (
                  <TabsTrigger key={t} value={t} className={cn(
                    "flex items-center gap-1.5 h-[44px] rounded-none border-b-2 px-4 text-[12px] font-semibold transition-colors",
                    tab === t ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
                  )}>
                    <Icon className="h-3.5 w-3.5" />{label}
                  </TabsTrigger>
                );
              })}
            </TabsList>
          </div>

          {/* ── Passengers ────────────────────────────────────────────────── */}
          <TabsContent value="passengers" className="flex-1 overflow-y-auto bg-background m-0">
            <div className="max-w-2xl mx-auto w-full p-4 space-y-3">

              {/* GPS stale warning */}
              {gpsStale && (
                <div className="flex items-center gap-3 rounded-xl border border-orange-200 bg-orange-50 px-4 py-3">
                  <AlertTriangle className="h-4 w-4 text-orange-600 shrink-0" />
                  <p className="text-[12px] text-orange-800 font-medium">
                    GPS signal lost — last known position shown.
                  </p>
                </div>
              )}

              {/* No trip */}
              {!hasTrip && (
                <div className="flex flex-col items-center gap-3 rounded-xl border border-border/60 bg-card py-12">
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-muted">
                    <Clock className="h-5 w-5 text-muted-foreground opacity-50" />
                  </div>
                  <div className="text-center">
                    <p className="text-[13px] font-bold text-foreground">No active trip</p>
                    <p className="text-[11px] text-muted-foreground mt-1 max-w-[220px]">
                      {connected ? "Waiting for bus to depart a terminal." : "Reconnecting to live feed…"}
                    </p>
                  </div>
                </div>
              )}

              {/* Active trip */}
              {hasTrip && snap && (
                <>
                  {/* Route + direction */}
                  <div className="flex items-center justify-between rounded-xl border border-border/60 bg-card px-4 py-3.5">
                    <div className="min-w-0">
                      <p className="text-[10px] font-bold uppercase tracking-[0.1em] text-muted-foreground/60">Active Route</p>
                      <p className="text-[13px] font-bold text-foreground mt-0.5 truncate">
                        {routeDetail?.name ?? vehicle?.routeName ?? snap.routeName ?? "—"}
                      </p>
                      {snap.progressPercent != null && (
                        <p className="text-[10px] text-muted-foreground mt-1">
                          {Math.round(snap.progressPercent)}% of route completed
                        </p>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1.5 shrink-0">
                      <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2.5 py-1">
                        <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
                        <span className="text-[9px] font-bold text-[#2E6B1A]">Active</span>
                      </span>
                      {!isLive && (
                        <span className="text-[9px] text-muted-foreground italic">REST snapshot</span>
                      )}
                    </div>
                  </div>

                  {/* Next stop banner — approaching a stop */}
                  {snap.nextStopName && !snap.currentStopName && (
                    <div className="flex items-center gap-3 rounded-xl border border-border/60 bg-card px-4 py-3">
                      <ChevronRight className="h-4 w-4 text-muted-foreground shrink-0" />
                      <div className="flex-1 min-w-0">
                        <p className="text-[10px] text-muted-foreground">Approaching</p>
                        <p className="text-[13px] font-bold text-foreground truncate">{snap.nextStopName}</p>
                      </div>
                      {snap.distanceToNextStopM != null && (
                        <span className="text-[12px] font-semibold text-primary shrink-0">
                          {formatDistance(snap.distanceToNextStopM)}
                        </span>
                      )}
                    </div>
                  )}

                  {/* Terminal approach banner — no more stops */}
                  {!snap.nextStopName && !snap.currentStopName && snap.distanceToTerminalM != null && (
                    <div className="flex items-center gap-3 rounded-xl border border-orange-200 bg-orange-50 px-4 py-3">
                      <span className="text-lg shrink-0">🏁</span>
                      <div className="flex-1 min-w-0">
                        <p className="text-[10px] text-orange-700 font-semibold">Approaching terminal</p>
                        <p className="text-[13px] font-bold text-orange-900 truncate">
                          {routeDetail?.endBusPark.name ?? "End terminal"}
                        </p>
                      </div>
                      <span className="text-[12px] font-semibold text-orange-700 shrink-0">
                        {formatDistance(snap.distanceToTerminalM)}
                      </span>
                    </div>
                  )}

                  {/* Occupancy */}
                  <div className="grid grid-cols-2 gap-2.5">
                    <OccupancyChip
                      onBoard={snap.passengersOnBoard}
                      available={snap.availableSeats}
                      capacity={vehicle?.capacity ?? null}
                    />
                  </div>

                  {/* Vehicle details */}
                  {vehicle && (
                    <div className="rounded-xl border border-border/60 bg-card px-4 py-3.5">
                      <p className="text-[9.5px] font-bold uppercase tracking-[0.1em] text-muted-foreground/60 mb-3">Vehicle</p>
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 shrink-0">
                          <Bus className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <p className="text-[13px] font-bold text-foreground">{vehicle.plateNumber}</p>
                          <p className="text-[11px] text-muted-foreground">
                            {vehicle.model ?? "Unknown model"}
                            {vehicle.capacity ? ` · ${vehicle.capacity} seats` : ""}
                          </p>
                        </div>
                        {snap.speedKmh != null && gpsValid && (
                          <div className="ml-auto text-center">
                            <p className="text-[20px] font-bold text-foreground leading-none">{Math.round(snap.speedKmh)}</p>
                            <p className="text-[9px] text-muted-foreground">km/h</p>
                          </div>
                        )}
                        {snap.headingDeg != null && gpsValid && (
                          <div className="rounded-xl bg-muted/50 p-2 text-center">
                            <Navigation
                              className="h-4 w-4 text-muted-foreground mx-auto"
                              style={{ transform: `rotate(${snap.headingDeg}deg)` }}
                            />
                            <p className="text-[8px] text-muted-foreground mt-0.5">hdg</p>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </TabsContent>

          {/* ── Map (OSM) ─────────────────────────────────────────────────── */}
          <TabsContent value="map" className="m-0 p-0 flex-1 min-h-0 overflow-hidden">
            <TrackingMapTab mode="plain" routeDetail={routeDetail} liveEvent={snap}
              plateNumber={vehicle?.plateNumber ?? ""} />
          </TabsContent>

          {/* ── Satellite (ESRI) ──────────────────────────────────────────── */}
          <TabsContent value="satellite" className="m-0 p-0 flex-1 min-h-0 overflow-hidden">
            <TrackingMapTab mode="satellite" routeDetail={routeDetail} liveEvent={snap}
              plateNumber={vehicle?.plateNumber ?? ""} />
          </TabsContent>
        </Tabs>
      </div>

      {/* Loading overlay */}
      {isLoading && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-background/60 backdrop-blur-sm">
          <div className="flex flex-col items-center gap-3 bg-card rounded-xl px-8 py-6 border border-border/60">
            <div className="h-6 w-6 rounded-full border-2 border-primary/20 border-t-primary animate-spin" />
            <p className="text-[12px] font-semibold text-foreground">Loading vehicle…</p>
          </div>
        </div>
      )}
    </div>
  );
}
