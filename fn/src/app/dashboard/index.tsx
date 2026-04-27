import { useQuery } from "@tanstack/react-query";
import {
  Bus,
  Route,
  UserRound,
  MapPin,
  ParkingCircle,
  Activity,
  Zap,
  ArrowUpRight,
  TrendingUp,
} from "lucide-react";

import { routesApi } from "../routes/api/routes.api";
import { routeKeys } from "../routes/api/routes.keys";
import { driversApi } from "../drivers/api/drivers.api";
import { driverKeys } from "../drivers/api/drivers.keys";
import { busesApi } from "../buses/api/buses.api";
import { busKeys } from "../buses/api/buses.keys";
import { stopsApi } from "../stops/api/stops.api";
import { stopKeys } from "../stops/api/stops.keys";
import { parksApi } from "../parks/api/parks.api";
import { parkKeys } from "../parks/api/parks.keys";

// ── Stat card ─────────────────────────────────────────────────

interface StatCardProps {
  title: string;
  value: number | string;
  icon: React.ElementType;
  isLoading: boolean;
  trend?: string;
  iconBg: string;
  iconColor: string;
  accentBar: string;
}

function StatCard({ title, value, icon: Icon, isLoading, trend, iconBg, iconColor, accentBar }: StatCardProps) {
  return (
    <div className="relative bg-card rounded-2xl border border-border/60 shadow-ambient p-5 overflow-hidden group hover:shadow-ambient-md transition-shadow duration-200">
      {/* Top accent bar */}
      <div className={`absolute top-0 left-0 right-0 h-[3px] ${accentBar} rounded-t-2xl`} />

      <div className="flex items-start justify-between mb-4">
        {/* Icon */}
        <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${iconBg}`}>
          <Icon className={`h-5 w-5 ${iconColor}`} />
        </div>

        {/* Trend badge */}
        {trend && (
          <div className="flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded-lg bg-[#91D06C]/10 text-[#2E6B1A] border border-[#91D06C]/20">
            <ArrowUpRight className="h-3 w-3" />
            {trend}
          </div>
        )}
      </div>

      <p className="text-[10.5px] font-bold uppercase tracking-[0.1em] text-muted-foreground mb-1">
        {title}
      </p>
      {isLoading ? (
        <div className="h-8 w-16 bg-muted animate-pulse rounded-lg mt-1" />
      ) : (
        <p className="text-[32px] font-bold text-foreground leading-none tracking-tight">{value}</p>
      )}
    </div>
  );
}

// ── Dashboard ─────────────────────────────────────────────────

export default function Dashboard() {
  const { data: routes,  isLoading: loadingRoutes  } = useQuery(routeKeys.lists(),  { queryFn: () => routesApi.getAll() });
  const { data: drivers, isLoading: loadingDrivers } = useQuery(driverKeys.lists(), { queryFn: () => driversApi.getAll() });
  const { data: buses,   isLoading: loadingBuses   } = useQuery(busKeys.lists(),    { queryFn: () => busesApi.getAll() });
  const { data: stops,   isLoading: loadingStops   } = useQuery(stopKeys.lists(),   { queryFn: () => stopsApi.getAll() });
  const { data: parks,   isLoading: loadingParks   } = useQuery(parkKeys.lists(),   { queryFn: () => parksApi.getAll() });

  const routeItems  = routes?.content  ?? [];
  const driverItems = drivers?.content ?? [];
  const busItems    = buses?.content   ?? [];
  const stopItems   = stops?.content   ?? [];
  const parkItems   = parks?.content   ?? [];

  return (
    <div className="space-y-7 max-w-7xl mx-auto w-full">

      {/* ── Page header ─────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <span className="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.18em] text-primary mb-2">
            <Activity className="h-3.5 w-3.5" />
            System Control Center
          </span>
          <h1 className="text-[30px] font-bold tracking-tight text-foreground leading-none">
            Operational Overview
          </h1>
        </div>

        {/* System status badge */}
        <div className="inline-flex items-center gap-2.5 px-4 py-2 bg-[#91D06C]/10 border border-[#91D06C]/25 text-[#2E6B1A] rounded-xl">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#91D06C] opacity-60" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-[#91D06C]" />
          </span>
          <span className="text-[12px] font-bold tracking-wide">SYSTEM ONLINE</span>
        </div>
      </div>

      {/* ── KPI grid ────────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        <StatCard
          title="Active Buses"
          value={busItems.length}
          icon={Bus}
          isLoading={loadingBuses}
          trend="Live"
          iconBg="bg-[#406093]/10"
          iconColor="text-[#406093]"
          accentBar="bg-[#406093]"
        />
        <StatCard
          title="Drivers"
          value={driverItems.length}
          icon={UserRound}
          isLoading={loadingDrivers}
          iconBg="bg-[#4C8CE4]/10"
          iconColor="text-[#4C8CE4]"
          accentBar="bg-[#4C8CE4]"
        />
        <StatCard
          title="Routes"
          value={routeItems.length}
          icon={Route}
          isLoading={loadingRoutes}
          iconBg="bg-[#91D06C]/15"
          iconColor="text-[#2E6B1A]"
          accentBar="bg-[#91D06C]"
        />
        <StatCard
          title="Stops"
          value={stopItems.length}
          icon={MapPin}
          isLoading={loadingStops}
          iconBg="bg-[#4C8CE4]/10"
          iconColor="text-[#4C8CE4]"
          accentBar="bg-[#4C8CE4]"
        />
        <StatCard
          title="Parks"
          value={parkItems.length}
          icon={ParkingCircle}
          isLoading={loadingParks}
          iconBg="bg-[#FFF799]/50"
          iconColor="text-[#856B00]"
          accentBar="bg-[#FFF799]"
        />
      </div>

      {/* ── Lower grid ──────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

        {/* Fleet Live Status */}
        <div className="bg-card rounded-2xl border border-border/60 shadow-ambient overflow-hidden lg:col-span-2">
          <div className="px-6 py-4 border-b border-border/60 bg-surface-container/30 flex items-center justify-between">
            <div>
              <h3 className="text-[15px] font-bold text-foreground">Fleet Live Status</h3>
              <p className="text-[12px] text-muted-foreground mt-0.5">Real-time GPS sync & driver activity</p>
            </div>
            <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/10 text-primary text-[12px] font-bold hover:bg-primary/20 transition-colors border border-primary/15">
              <Zap className="h-3 w-3" />
              Sync Fleet
            </button>
          </div>

          <div className="p-5 min-h-[260px] flex items-center justify-center">
            {loadingBuses ? (
              <div className="text-center text-muted-foreground flex flex-col items-center gap-3">
                <div className="w-6 h-6 border-2 border-primary/20 border-t-primary rounded-full animate-spin" />
                <span className="text-[13px]">Syncing fleet positions…</span>
              </div>
            ) : busItems.length ? (
              <div className="w-full flex flex-col gap-2.5">
                {busItems.slice(0, 4).map((bus) => (
                  <div
                    key={bus.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between p-3.5 bg-surface-container/30 border border-border/50 rounded-xl hover:bg-surface-container/60 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-9 w-9 shrink-0 bg-primary/10 border border-primary/15 text-primary rounded-full flex items-center justify-center">
                        <Bus className="h-4 w-4" />
                      </div>
                      <div>
                        <p className="text-[13px] font-bold text-foreground">{bus.plateNumber}</p>
                        <p className="text-[11px] text-muted-foreground mt-0.5">
                          {bus.model} · {bus.currentDriver?.fullName ?? "No driver"}
                        </p>
                      </div>
                    </div>
                    <div className="mt-2.5 sm:mt-0">
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-bold bg-[#91D06C]/10 text-[#2E6B1A] border border-[#91D06C]/20">
                        <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
                        Tracking Active
                      </span>
                    </div>
                  </div>
                ))}
                {busItems.length > 4 && (
                  <p className="text-center text-[12px] font-semibold text-primary hover:underline cursor-pointer mt-1">
                    View all {busItems.length} buses →
                  </p>
                )}
              </div>
            ) : (
              <div className="text-center">
                <Bus className="h-8 w-8 text-muted-foreground/30 mx-auto mb-2" />
                <p className="text-[13px] text-muted-foreground">No buses transmitting GPS.</p>
              </div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="bg-card rounded-2xl border border-border/60 shadow-ambient overflow-hidden">
          <div className="px-6 py-4 border-b border-border/60 bg-surface-container/30">
            <h3 className="text-[15px] font-bold text-foreground">Quick Actions</h3>
            <p className="text-[12px] text-muted-foreground mt-0.5">Common workflow shortcuts</p>
          </div>
          <div className="p-3 flex flex-col gap-1">
            {[
              { icon: Bus,       label: "Register New Bus",  desc: "Add a vehicle to fleet tracking" },
              { icon: UserRound, label: "Add Driver",        desc: "Create a new driver profile" },
              { icon: Route,     label: "Map Route",         desc: "Draw a trajectory on the map" },
              { icon: TrendingUp,label: "System Logs",       desc: "View server connectivity logs" },
            ].map((action, i) => (
              <button
                key={i}
                className="flex items-start gap-3.5 p-3 rounded-xl hover:bg-surface-container/60 transition-colors text-left group"
              >
                <div className="h-9 w-9 shrink-0 bg-primary/8 text-primary rounded-lg flex items-center justify-center group-hover:bg-primary group-hover:text-white transition-colors">
                  <action.icon className="h-4 w-4" />
                </div>
                <div>
                  <p className="text-[13px] font-bold text-foreground">{action.label}</p>
                  <p className="text-[11px] text-muted-foreground font-medium">{action.desc}</p>
                </div>
              </button>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
}
