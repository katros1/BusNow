import {
  ArrowLeft,
  Bus,
  Clock,
  Navigation,
  TrendingDown,
  TrendingUp,
  Users,
  WifiOff,
  AlertTriangle,
} from "lucide-react";
import { useNavigate } from "@tanstack/react-router";
import { cn } from "@/lib/utils";
import type { TrackingVehicleDto, VehiclePositionEvent } from "../api/tracking.types";

interface Props {
  vehicle: TrackingVehicleDto;
  liveEvent: VehiclePositionEvent | undefined;
  connected: boolean;
}

function tripDuration(startedAt: string): string {
  const secs = Math.max(
    0,
    Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000)
  );
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export function VehicleDetailPanel({ vehicle, liveEvent, connected }: Props) {
  const navigate = useNavigate();

  const trip      = liveEvent?.trip;
  const gpsValid  = liveEvent?.gpsValid ?? false;
  const speed     = liveEvent?.speedKmh != null ? Math.round(liveEvent.speedKmh) : null;
  const heading   = liveEvent?.headingDeg != null ? Math.round(liveEvent.headingDeg) : null;
  const routeName = liveEvent?.route?.name ?? vehicle.routeName;
  const routeCode = liveEvent?.route?.code ?? vehicle.routeCode;
  const direction = liveEvent?.route?.direction ?? vehicle.direction;
  const onBoard   = trip?.onBoard ?? vehicle.passengersOnBoard ?? 0;
  const capacity  = vehicle.capacity;
  const available = trip?.availableSeats ?? vehicle.availableSeats;
  const occupancy = capacity ? Math.round((onBoard / capacity) * 100) : null;

  const statusMeta = !liveEvent
    ? { text: "Awaiting signal…", cls: "text-muted-foreground" }
    : !gpsValid
    ? { text: "GPS signal lost", cls: "text-yellow-600" }
    : trip
    ? { text: "Active trip", cls: "text-[#2E6B1A]" }
    : { text: "Online — no active trip", cls: "text-primary" };

  return (
    <div
      className={cn(
        "absolute top-3 right-3 z-[400]",
        "w-[272px] rounded-xl overflow-hidden flex flex-col",
        "bg-card/95 backdrop-blur-md border border-border/60 shadow-ambient-md"
      )}
    >
      {/* ── Header ── */}
      <div className="flex items-center gap-2 px-3 py-3 bg-surface-container/40 border-b border-border/50 shrink-0">
        <button
          onClick={() => navigate({ to: "/tracking" })}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          aria-label="Back to overview"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>

        <div className="flex-1 min-w-0">
          <p className="text-[13px] font-bold text-foreground truncate">
            {vehicle.plateNumber}
          </p>
          <p className={cn("text-[10px] font-medium truncate", statusMeta.cls)}>
            {statusMeta.text}
          </p>
        </div>

        {connected ? (
          <span className="flex items-center gap-1 rounded-full bg-[#2E6B1A]/10 px-2 py-0.5 shrink-0">
            <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
            <span className="text-[9px] font-bold uppercase tracking-widest text-[#2E6B1A]">
              Live
            </span>
          </span>
        ) : (
          <span className="flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 shrink-0">
            <WifiOff className="h-2.5 w-2.5 text-muted-foreground" />
            <span className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground">
              Offline
            </span>
          </span>
        )}
      </div>

      {/* GPS warning banner */}
      {liveEvent && !gpsValid && (
        <div className="flex items-center gap-2 px-4 py-2 bg-yellow-50 border-b border-yellow-100 shrink-0">
          <AlertTriangle className="h-3.5 w-3.5 text-yellow-600 shrink-0" />
          <p className="text-[11px] text-yellow-700 font-medium">
            Last known position shown
          </p>
        </div>
      )}

      {/* ── Scrollable body ── */}
      <div
        className="flex flex-col divide-y divide-border/30 overflow-y-auto"
        style={{ maxHeight: "calc(100vh - 180px)" }}
      >
        {/* Vehicle */}
        <div className="px-4 py-3">
          <p className="text-[9px] font-bold uppercase tracking-[0.12em] text-muted-foreground/70 mb-2">
            Vehicle
          </p>
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Bus className="h-4 w-4 text-primary" />
            </div>
            <div>
              <p className="text-[13px] font-bold text-foreground">
                {vehicle.plateNumber}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {vehicle.model ?? "Unknown model"} · Cap.{" "}
                {capacity != null ? capacity : "—"}
              </p>
            </div>
          </div>
        </div>

        {/* Route */}
        {(routeName || routeCode) && (
          <div className="px-4 py-3">
            <p className="text-[9px] font-bold uppercase tracking-[0.12em] text-muted-foreground/70 mb-2">
              Route
            </p>
            <div className="flex items-start gap-2">
              <div className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded bg-primary/10">
                <span className="text-[8px] font-bold text-primary">
                  {routeCode ?? "?"}
                </span>
              </div>
              <div>
                <p className="text-[13px] font-semibold text-foreground leading-tight">
                  {routeName ?? "Unknown route"}
                </p>
                {direction && (
                  <p className="text-[11px] text-muted-foreground mt-0.5">
                    {direction === "FORWARD" ? "→ Forward" : "← Backward"}
                  </p>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Motion */}
        {liveEvent && gpsValid && (
          <div className="px-4 py-3">
            <p className="text-[9px] font-bold uppercase tracking-[0.12em] text-muted-foreground/70 mb-2">
              Motion
            </p>
            <div className="grid grid-cols-2 gap-2">
              <div className="rounded-lg bg-surface-container/60 px-3 py-2.5">
                <p className="text-[9px] text-muted-foreground mb-1">Speed</p>
                <p className="text-[22px] font-bold text-foreground leading-none">
                  {speed ?? "—"}
                </p>
                <p className="text-[9px] text-muted-foreground mt-0.5">km/h</p>
              </div>
              <div className="rounded-lg bg-surface-container/60 px-3 py-2.5">
                <div className="flex items-center gap-1 mb-1">
                  <Navigation
                    className="h-2.5 w-2.5 text-muted-foreground shrink-0"
                    style={{ transform: `rotate(${heading ?? 0}deg)` }}
                  />
                  <p className="text-[9px] text-muted-foreground">Heading</p>
                </div>
                <p className="text-[22px] font-bold text-foreground leading-none">
                  {heading ?? "—"}
                </p>
                <p className="text-[9px] text-muted-foreground mt-0.5">°</p>
              </div>
            </div>
          </div>
        )}

        {/* Active trip */}
        {trip ? (
          <div className="px-4 py-3">
            <div className="flex items-center justify-between mb-2">
              <p className="text-[9px] font-bold uppercase tracking-[0.12em] text-muted-foreground/70">
                Active Trip
              </p>
              <span className="flex items-center gap-1 text-[9px] text-muted-foreground">
                <Clock className="h-2.5 w-2.5" />
                {tripDuration(trip.startedAt)}
              </span>
            </div>

            {/* Occupancy bar */}
            {occupancy != null && (
              <div className="mb-3">
                <div className="flex justify-between mb-1">
                  <span className="text-[10px] text-muted-foreground">
                    Occupancy
                  </span>
                  <span className="text-[10px] font-bold text-foreground">
                    {occupancy}%
                  </span>
                </div>
                <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                  <div
                    className={cn(
                      "h-full rounded-full transition-all duration-500",
                      occupancy >= 90
                        ? "bg-[#C0392B]"
                        : occupancy >= 70
                        ? "bg-yellow-500"
                        : "bg-[#91D06C]"
                    )}
                    style={{ width: `${Math.min(100, occupancy)}%` }}
                  />
                </div>
              </div>
            )}

            {/* Passenger stats */}
            <div className="grid grid-cols-3 gap-1.5">
              {[
                { icon: Users, value: onBoard, label: "On board", color: "text-primary" },
                { icon: TrendingUp, value: trip.passengersIn, label: "Boarded", color: "text-[#2E6B1A]" },
                { icon: TrendingDown, value: trip.passengersOut, label: "Alighted", color: "text-muted-foreground" },
              ].map(({ icon: Icon, value, label, color }) => (
                <div
                  key={label}
                  className="rounded-lg bg-surface-container/60 px-2 py-2 text-center"
                >
                  <Icon className={cn("h-3 w-3 mx-auto mb-1", color)} />
                  <p className="text-[17px] font-bold text-foreground leading-none">
                    {value}
                  </p>
                  <p className="text-[8.5px] text-muted-foreground mt-0.5">{label}</p>
                </div>
              ))}
            </div>

            {available != null && (
              <p className="mt-2.5 text-[10px] text-muted-foreground text-center">
                <span className="font-bold text-foreground">{available}</span>{" "}
                seat{available !== 1 ? "s" : ""} available
              </p>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center gap-1.5 py-5 text-muted-foreground">
            <Clock className="h-5 w-5 opacity-25" />
            <p className="text-[11px]">No active trip</p>
          </div>
        )}

        {/* Last update */}
        {liveEvent && (
          <div className="px-4 py-2.5">
            <p className="text-[10px] text-muted-foreground text-center">
              Updated{" "}
              {new Date(liveEvent.timestamp).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
              })}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
