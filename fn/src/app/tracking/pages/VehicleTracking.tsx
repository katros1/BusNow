import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams, useNavigate } from "@tanstack/react-router";
import {
  ArrowLeft, Bus, Clock, TrendingUp, TrendingDown, Users,
  Satellite, Map as MapIcon, WifiOff, AlertTriangle, MapPin,
  Navigation,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { trackingApi } from "../api/tracking.api";
import { trackingKeys } from "../api/tracking.keys";
import { useTrackingSocket } from "../hooks/useTrackingSocket";
import { RouteLine } from "../components/RouteLine";
import { TrackingMapTab } from "../components/TrackingMapTab";
import type { VehiclePositionEvent } from "../api/tracking.types";

// ── Constants ────────────────────────────────────────────────────────────────
const NAVBAR_H = 60;
const LINE_H   = 196;
const TABBAR_H = 44;
const MAP_H    = `calc(100vh - ${NAVBAR_H + LINE_H + TABBAR_H}px)`;

// ── Helpers ──────────────────────────────────────────────────────────────────
function tripDuration(startedAt: string | null | undefined): string {
  if (!startedAt) return "—";
  const s = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

function directionLabel(direction: string | null | undefined): string {
  if (direction === "FORWARD")  return "Forward →";
  if (direction === "BACKWARD") return "← Return";
  return direction ?? "";
}

function directionArrow(direction: string | null | undefined): string {
  if (direction === "FORWARD")  return " →";
  if (direction === "BACKWARD") return " ←";
  return "";
}

// ── Page ─────────────────────────────────────────────────────────────────────
export default function VehicleTracking() {
  const navigate  = useNavigate();
  const { busId } = useParams({ strict: false }) as { busId: string };
  const [tab, setTab] = useState<"passengers" | "satellite" | "map">("passengers");

  // ── REST snapshot (initial load + background refresh) ────────────────────
  const { data: allVehicles = [], isLoading } = useQuery({
    queryKey: trackingKeys.vehicles(),
    queryFn: trackingApi.getVehicles,
    staleTime: 15_000,
    refetchInterval: 30_000,
  });

  const vehicle = useMemo(
    () => allVehicles.find((v) => v?.busId === busId),
    [allVehicles, busId]
  );

  // ── Live WebSocket updates ────────────────────────────────────────────────
  const { vehicles: liveMap, connected } = useTrackingSocket();
  const liveEvent = liveMap.get(busId);

  // ── Synthetic "effective event" — merges WS live data with REST fallback ──
  // When WS is down or no frame has arrived yet, construct a position event
  // from the REST snapshot so the UI always has something to render.
  const effectiveEvent = useMemo((): VehiclePositionEvent | undefined => {
    if (liveEvent) return liveEvent;
    if (!vehicle) return undefined;
    const hasPosition = vehicle.latitude != null && vehicle.longitude != null;
    return {
      busId:      vehicle.busId,
      plateNumber: vehicle.plateNumber,
      deviceId:   "",
      gpsValid:   hasPosition,
      latitude:   vehicle.latitude,
      longitude:  vehicle.longitude,
      speedKmh:   null,
      headingDeg: null,
      timestamp:  new Date().toISOString(),
      route: vehicle.routeId ? {
        id:        vehicle.routeId,
        name:      vehicle.routeName,
        code:      vehicle.routeCode,
        direction: vehicle.direction,
      } : null,
      trip: vehicle.activeTripId ? {
        id:             vehicle.activeTripId,
        status:         "ACTIVE",
        startedAt:      vehicle.tripStartedAt ?? new Date().toISOString(),
        passengersIn:   0,
        passengersOut:  0,
        onBoard:        vehicle.passengersOnBoard ?? 0,
        availableSeats: vehicle.availableSeats ?? null,
      } : null,
      currentStop: null,
    };
  }, [liveEvent, vehicle]);

  // ── Derived values ────────────────────────────────────────────────────────
  const trip        = effectiveEvent?.trip  ?? null;
  const gpsValid    = effectiveEvent?.gpsValid ?? false;
  const routeCode   = effectiveEvent?.route?.code ?? vehicle?.routeCode;
  const direction   = effectiveEvent?.route?.direction ?? vehicle?.direction;
  
  // Real-time fix: strictly use live trip counts if a trip is active
  const passengersIn  = trip ? trip.passengersIn : 0;
  const passengersOut = trip ? trip.passengersOut : 0;
  const onBoard       = trip ? trip.onBoard : (vehicle?.passengersOnBoard ?? 0);

  const capacity    = vehicle?.capacity;
  const occupancy   = capacity ? Math.round((onBoard / capacity) * 100) : null;
  const currentStop = effectiveEvent?.currentStop ?? null;
  const isLiveData  = !!liveEvent;

  // Route ID: follow live event first (handles 302F→302R flip automatically)
  const routeId = effectiveEvent?.route?.id ?? vehicle?.routeId ?? null;

  const { data: routeDetail } = useQuery({
    queryKey: trackingKeys.route(routeId ?? ""),
    queryFn:  () => trackingApi.getRouteDetail(routeId!),
    enabled:  !!routeId,
    staleTime: 60_000,
  });

  // ── Not-found ─────────────────────────────────────────────────────────────
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
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Live Tracking
        </button>
      </div>
    );
  }

  return (
    <div className="-m-5 lg:-m-7 flex flex-col overflow-hidden"
      style={{ height: `calc(100vh - ${NAVBAR_H}px)` }}>

      {/* ══ TOP: Route line ═══════════════════════════════════════════════════ */}
      <div className="shrink-0 flex flex-col" style={{ height: LINE_H }}>

        {/* Header bar */}
        <div className="flex items-center gap-3 px-5 py-3 bg-card border-b border-border/50 shrink-0">
          <button
            onClick={() => navigate({ to: "/tracking" })}
            className="flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>

          <div className="flex items-center gap-2 flex-1 min-w-0">
            {routeCode && (
              <span className="shrink-0 text-[9px] font-bold uppercase tracking-wide rounded bg-primary/10 text-primary px-1.5 py-0.5">
                {routeCode}{directionArrow(direction)}
              </span>
            )}
            <span className="text-[14px] font-bold text-foreground truncate">
              {vehicle?.plateNumber ?? "Loading…"}
            </span>
            {vehicle?.model && (
              <span className="text-[11px] text-muted-foreground shrink-0">· {vehicle.model}</span>
            )}
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {effectiveEvent && !gpsValid && (
              <span className="flex items-center gap-1 rounded-full bg-yellow-50 border border-yellow-200 px-2 py-0.5">
                <AlertTriangle className="h-3 w-3 text-yellow-600" />
                <span className="text-[9px] font-bold text-yellow-700">No GPS</span>
              </span>
            )}
            {connected ? (
              <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2 py-0.5">
                <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
                <span className="text-[9px] font-bold uppercase tracking-widest text-[#2E6B1A]">Live</span>
              </span>
            ) : (
              <span className="flex items-center gap-1 rounded-full bg-muted px-2 py-0.5">
                <WifiOff className="h-2.5 w-2.5 text-muted-foreground" />
                <span className="text-[9px] font-bold text-muted-foreground">Offline</span>
              </span>
            )}
          </div>
        </div>

        {/* Route line visualization */}
        {routeDetail ? (
          <RouteLine
            routeDetail={routeDetail}
            liveEvent={effectiveEvent}
            hasActiveTrip={!!trip}
            plateNumber={vehicle?.plateNumber ?? ""}
          />
        ) : (
          <div className="flex-1 flex items-center justify-center bg-card">
            {routeId ? (
              <div className="flex items-center gap-2 text-muted-foreground">
                <div className="h-4 w-4 rounded-full border-2 border-primary/20 border-t-primary animate-spin" />
                <span className="text-[12px]">Loading route…</span>
              </div>
            ) : (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Bus className="h-4 w-4 opacity-30" />
                <span className="text-[12px]">No route assigned to this bus</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ══ BOTTOM: Tabs ══════════════════════════════════════════════════════ */}
      <div className="flex-1 flex flex-col overflow-hidden border-t border-border/50 mt-10">
        <Tabs
          value={tab}
          onValueChange={(v) => setTab(v as typeof tab)}
          className="flex flex-col h-full"
        >
          {/* Tab bar */}
          <div className="shrink-0 bg-card border-b border-border/50 px-4">
            <TabsList className="h-[44px] bg-transparent gap-0 p-0 w-auto">
              {(["passengers", "satellite", "map"] as const).map((t) => {
                const icons: Record<typeof t, React.ReactNode> = {
                  passengers: <Users className="h-3.5 w-3.5" />,
                  satellite:  <Satellite className="h-3.5 w-3.5" />,
                  map:        <MapIcon className="h-3.5 w-3.5" />,
                };
                const labels: Record<typeof t, string> = {
                  passengers: "Passengers",
                  satellite:  "Satellite",
                  map:        "Map",
                };
                return (
                  <TabsTrigger key={t} value={t} className={cn(
                    "flex items-center gap-1.5 h-[44px] rounded-none border-b-2 px-4 text-[12px] font-semibold transition-colors",
                    tab === t
                      ? "border-primary text-primary"
                      : "border-transparent text-muted-foreground hover:text-foreground"
                  )}>
                    {icons[t]}{labels[t]}
                  </TabsTrigger>
                );
              })}
            </TabsList>
          </div>

          {/* ── Passengers tab ─────────────────────────────────────────────── */}
          <TabsContent value="passengers" className="flex-1 overflow-y-auto bg-background m-0">
            <div className="max-w-2xl mx-auto w-full p-5 space-y-3">

              {/* Current stop banner — shown whenever bus is inside a stop polygon */}
              {currentStop && (
                <div className="flex items-center gap-3 rounded-xl border border-primary/30 bg-primary/5 px-4 py-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 shrink-0">
                    <MapPin className="h-4 w-4 text-primary" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[10px] font-bold uppercase tracking-[0.1em] text-primary/70">At stop</p>
                    <p className="text-[13px] font-bold text-foreground truncate">{currentStop.name}</p>
                  </div>
                  <span className="shrink-0 text-[10px] font-bold text-muted-foreground bg-muted rounded px-2 py-0.5">
                    #{currentStop.sequence}
                  </span>
                </div>
              )}

              {/* No trip state */}
              {!trip && (
                <div className="flex flex-col items-center gap-3 rounded-xl border border-border/60 bg-card py-10 text-muted-foreground">
                  <Clock className="h-8 w-8 opacity-20" />
                  <div className="text-center">
                    <p className="text-[13px] font-semibold text-foreground">No active trip</p>
                    <p className="text-[11px] mt-1 max-w-[220px]">
                      {connected
                        ? "Waiting for bus to depart a terminal."
                        : "WebSocket offline — reconnecting…"}
                    </p>
                  </div>
                </div>
              )}

              {trip && (
                <>
                  {/* Trip header */}
                  <div className="flex items-center justify-between rounded-xl border border-border/60 bg-card px-5 py-4">
                    <div>
                      <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-muted-foreground/70 mb-1">
                        Active Trip
                      </p>
                      <p className="text-[14px] font-bold text-foreground">
                        {routeDetail?.name ?? vehicle?.routeName ?? "Loading route…"}
                      </p>
                      {direction && (
                        <p className="text-[11px] text-muted-foreground mt-0.5">
                          {directionLabel(direction)}
                        </p>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2.5 py-1">
                        <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
                        <span className="text-[10px] font-bold text-[#2E6B1A]">Active</span>
                      </span>
                      {!isLiveData && (
                        <span className="text-[9px] text-muted-foreground italic">snapshot</span>
                      )}
                      <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        {tripDuration(trip.startedAt)}
                      </span>
                    </div>
                  </div>

                  {/* Occupancy bar */}
                  {occupancy != null && capacity != null && (
                    <div className="rounded-xl border border-border/60 bg-card px-5 py-4">
                      <div className="flex items-center justify-between mb-2">
                        <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-muted-foreground/70">
                          Occupancy
                        </p>
                        <span className={cn(
                          "text-[12px] font-bold",
                          occupancy >= 90 ? "text-[#C0392B]" : occupancy >= 70 ? "text-yellow-600" : "text-[#2E6B1A]"
                        )}>
                          {occupancy}%
                        </span>
                      </div>
                      <div className="h-2 rounded-full bg-muted overflow-hidden mb-2">
                        <div
                          className={cn(
                            "h-full rounded-full transition-all duration-500",
                            occupancy >= 90 ? "bg-[#C0392B]" : occupancy >= 70 ? "bg-yellow-500" : "bg-[#91D06C]"
                          )}
                          style={{ width: `${Math.min(100, occupancy)}%` }}
                        />
                      </div>
                      <p className="text-[11px] text-muted-foreground text-center">
                        <span className="font-bold text-foreground">{onBoard}</span> on board
                        {trip.availableSeats != null && (
                          <> · <span className="font-bold text-foreground">{trip.availableSeats}</span> available</>
                        )}
                        {capacity && (
                          <> · <span className="font-bold text-foreground">{capacity}</span> capacity</>
                        )}
                      </p>
                    </div>
                  )}

                  {/* Passenger stats — only when we have live delta counts */}
                  {isLiveData && (
                    <div className="grid grid-cols-3 gap-3">
                      {([
                        { icon: Users,       value: onBoard,          label: "On Board",  sublabel: "Currently",  color: "text-primary",         bg: "bg-primary/8" },
                        { icon: TrendingUp,  value: trip.passengersIn,  label: "Boarded",  sublabel: "This trip",  color: "text-[#2E6B1A]",       bg: "bg-[#2E6B1A]/8" },
                        { icon: TrendingDown,value: trip.passengersOut, label: "Alighted", sublabel: "This trip",  color: "text-muted-foreground", bg: "bg-muted" },
                      ] as const).map(({ icon: Icon, value, label, sublabel, color, bg }) => (
                        <div key={label}
                          className="rounded-xl border border-border/60 bg-card px-4 py-4 flex flex-col items-center gap-1">
                          <div className={cn("flex h-8 w-8 items-center justify-center rounded-lg mb-1", bg)}>
                            <Icon className={cn("h-4 w-4", color)} />
                          </div>
                          <p className="text-[28px] font-bold text-foreground leading-none">{value}</p>
                          <p className="text-[11px] font-semibold text-foreground">{label}</p>
                          <p className="text-[9px] text-muted-foreground">{sublabel}</p>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* On-board count when only REST data is available */}
                  {!isLiveData && (
                    <div className="rounded-xl border border-border/60 bg-card px-5 py-4 flex items-center gap-4">
                      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 shrink-0">
                        <Users className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <p className="text-[24px] font-bold text-foreground leading-none">{onBoard}</p>
                        <p className="text-[11px] text-muted-foreground mt-0.5">passengers on board</p>
                      </div>
                      {trip.availableSeats != null && (
                        <div className="ml-auto text-right">
                          <p className="text-[18px] font-bold text-[#2E6B1A] leading-none">{trip.availableSeats}</p>
                          <p className="text-[11px] text-muted-foreground mt-0.5">available</p>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Bus info */}
                  {vehicle && (
                    <div className="rounded-xl border border-border/60 bg-card px-5 py-4">
                      <p className="text-[9.5px] font-bold uppercase tracking-[0.1em] text-muted-foreground/70 mb-3">Vehicle</p>
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
                          <Bus className="h-5 w-5 text-primary" />
                        </div>
                        <div>
                          <p className="text-[14px] font-bold text-foreground">{vehicle.plateNumber}</p>
                          <p className="text-[11px] text-muted-foreground">
                            {vehicle.model ?? "Unknown model"}{capacity ? ` · ${capacity} seats` : ""}
                          </p>
                        </div>
                        {effectiveEvent?.speedKmh != null && gpsValid && (
                          <div className="ml-auto rounded-xl bg-surface-container/60 px-3 py-2 text-center">
                            <p className="text-[18px] font-bold text-foreground leading-none">
                              {Math.round(effectiveEvent.speedKmh)}
                            </p>
                            <p className="text-[9px] text-muted-foreground mt-0.5">km/h</p>
                          </div>
                        )}
                        {effectiveEvent?.headingDeg != null && gpsValid && (
                          <div className="rounded-xl bg-muted/60 px-3 py-2 text-center">
                            <Navigation
                              className="h-4 w-4 text-muted-foreground mx-auto"
                              style={{ transform: `rotate(${effectiveEvent.headingDeg}deg)` }}
                            />
                            <p className="text-[9px] text-muted-foreground mt-0.5">heading</p>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </TabsContent>

          {/* ── Satellite tab ───────────────────────────────────────────────── */}
          <TabsContent value="satellite" className="m-0 p-0 flex-1">
            <TrackingMapTab
              mode="satellite"
              routeDetail={routeDetail}
              liveEvent={effectiveEvent}
              plateNumber={vehicle?.plateNumber ?? ""}
              height={MAP_H}
            />
          </TabsContent>

          {/* ── Map tab ─────────────────────────────────────────────────────── */}
          <TabsContent value="map" className="m-0 p-0 flex-1">
            <TrackingMapTab
              mode="plain"
              routeDetail={routeDetail}
              liveEvent={effectiveEvent}
              plateNumber={vehicle?.plateNumber ?? ""}
              height={MAP_H}
            />
          </TabsContent>
        </Tabs>
      </div>

      {/* Loading overlay */}
      {isLoading && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-background/60 backdrop-blur-sm">
          <div className="flex flex-col items-center gap-3 bg-card rounded-xl px-8 py-6 shadow-ambient-md border border-border/60">
            <div className="h-7 w-7 rounded-full border-2 border-primary/20 border-t-primary animate-spin" />
            <p className="text-[13px] font-semibold text-foreground">Loading vehicle…</p>
          </div>
        </div>
      )}
    </div>
  );
}
