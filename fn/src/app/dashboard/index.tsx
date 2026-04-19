import { useQuery } from "@tanstack/react-query";
import { 
  Bus, 
  Route, 
  UserRound, 
  MapPin, 
  ParkingCircle, 
  Activity, 
  Zap, 
  ArrowUpRight 
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

function StatCard({ 
  title, 
  value, 
  icon: Icon, 
  isLoading, 
  trend,
  colorClass 
}: { 
  title: string; 
  value: number | string; 
  icon: import("react").ElementType; 
  isLoading: boolean;
  trend?: string;
  colorClass: string;
}) {
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-border p-6 relative overflow-hidden group hover:shadow-md transition-shadow">
      <div className={`absolute top-0 right-0 w-32 h-32 transform translate-x-8 -translate-y-8 rounded-full opacity-10 bg-current ${colorClass}`}></div>
      <div className="flex justify-between items-start mb-4 relative z-10">
        <div className={`p-3 rounded-xl ${colorClass.replace('text-', 'bg-').replace('600', '100')} text-${colorClass.split("-")[1]}-600`}>
          <Icon className={`h-6 w-6 ${colorClass}`} />
        </div>
        {trend && (
          <div className="flex items-center gap-1 text-emerald-600 bg-emerald-50 px-2 py-1 rounded-md text-[12px] font-semibold">
            <ArrowUpRight className="h-3 w-3" />
            {trend}
          </div>
        )}
      </div>
      <div className="relative z-10">
        <p className="text-[14px] font-semibold text-muted-foreground uppercase tracking-wider mb-1">{title}</p>
        <div className="flex items-end gap-3">
          {isLoading ? (
            <div className="h-10 w-20 bg-muted animate-pulse rounded-md"></div>
          ) : (
            <h3 className="text-[36px] font-bold text-foreground leading-none tracking-tight">{value}</h3>
          )}
        </div>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const { data: routes, isLoading: loadingRoutes } = useQuery({ queryKey: routeKeys.lists(), queryFn: routesApi.getAll });
  const { data: drivers, isLoading: loadingDrivers } = useQuery({ queryKey: driverKeys.lists(), queryFn: driversApi.getAll });
  const { data: buses, isLoading: loadingBuses } = useQuery({ queryKey: busKeys.lists(), queryFn: busesApi.getAll });
  const { data: stops, isLoading: loadingStops } = useQuery({ queryKey: stopKeys.lists(), queryFn: stopsApi.getAll });
  const { data: parks, isLoading: loadingParks } = useQuery({ queryKey: parkKeys.lists(), queryFn: parksApi.getAll });

  return (
    <div className="flex-1 space-y-8 pt-6 max-w-7xl mx-auto w-full px-6 md:px-0 pb-12">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <span className="text-[12px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block flex items-center gap-2">
            <Activity className="h-4 w-4" /> System Control Center
          </span>
          <h1 className="text-[36px] font-bold tracking-tight text-foreground leading-none">Operational Overview</h1>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-emerald-50 to-emerald-100/50 border border-emerald-200 text-emerald-700 rounded-xl shadow-sm">
          <span className="relative flex h-2 w-2 mr-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
          </span>
          <span className="text-[13px] font-bold tracking-wide">SYSTEM ONLINE</span>
        </div>
      </div>

      {/* Primary KPI Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-5">
        <StatCard 
          title="Active Buses" 
          value={buses?.content.length ?? 0}
          icon={Bus} 
          isLoading={loadingBuses} 
          trend="Live" 
          colorClass="text-amber-600" 
        />
        <StatCard 
          title="Registered Drivers" 
          value={drivers?.content.length ?? 0}
          icon={UserRound} 
          isLoading={loadingDrivers} 
          colorClass="text-blue-600" 
        />
        <StatCard 
          title="Mapped Routes" 
          value={routes?.content.length ?? 0}
          icon={Route} 
          isLoading={loadingRoutes} 
          colorClass="text-emerald-600" 
        />
        <StatCard 
          title="Transit Stops" 
          value={stops?.content.length ?? 0}
          icon={MapPin} 
          isLoading={loadingStops} 
          colorClass="text-purple-600" 
        />
        <StatCard 
          title="Terminals (Parks)" 
          value={parks?.length ?? 0} 
          icon={ParkingCircle} 
          isLoading={loadingParks} 
          colorClass="text-rose-600" 
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white rounded-2xl shadow-sm border border-border overflow-hidden lg:col-span-2">
          <div className="px-6 py-5 border-b border-border bg-surface-container-lowest flex items-center justify-between">
            <div>
              <h3 className="text-lg font-bold text-foreground">Fleet Live Status</h3>
              <p className="text-[13px] text-muted-foreground mt-0.5">Real-time GPS synchronization and driver activity.</p>
            </div>
            <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/10 text-primary text-[12px] font-bold hover:bg-primary/20 transition-colors">
              <Zap className="h-3 w-3" />
              Sync Fleet
            </button>
          </div>
          
          <div className="p-6 h-[300px] flex items-center justify-center bg-[#faf9f8]">
            {loadingBuses ? (
              <div className="text-center text-muted-foreground text-[14px] flex flex-col items-center gap-3">
                 <div className="w-6 h-6 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
                 Mapping geospatial footprint...
              </div>
            ) : buses?.length ? (
              <div className="w-full h-full flex flex-col gap-4">
                 {buses.slice(0, 4).map(bus => (
                    <div key={bus.id} className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-4 bg-white border border-border rounded-xl shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)]">
                       <div className="flex items-center gap-4">
                           <div className="h-10 w-10 shrink-0 bg-primary/10 text-primary rounded-full flex items-center justify-center">
                               <Bus className="h-5 w-5" />
                           </div>
                           <div>
                              <p className="text-[14px] font-bold text-foreground">{bus.plateNumber}</p>
                              <p className="text-[12px] text-muted-foreground font-medium">Model: {bus.model} • Driver: {bus.currentDriver?.fullName || 'Assigned'}</p>
                           </div>
                       </div>
                       <div className="mt-3 sm:mt-0 flex items-center gap-2">
                           <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/50">
                               <div className="h-1.5 w-1.5 rounded-full bg-emerald-500"></div> Tracking Active
                           </span>
                       </div>
                    </div>
                 ))}
                 {buses.length > 4 && (
                    <div className="text-center mt-2 text-[13px] font-semibold text-primary cursor-pointer hover:underline">
                        View all {buses.length} active buses →
                    </div>
                 )}
              </div>
            ) : (
                <div className="text-center text-muted-foreground text-[14px]">
                    No active buses transmitting GPS.
                </div>
            )}
          </div>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-border overflow-hidden">
          <div className="px-6 py-5 border-b border-border bg-surface-container-lowest">
            <h3 className="text-lg font-bold text-foreground">Quick Actions</h3>
            <p className="text-[13px] text-muted-foreground mt-0.5">Common workflow shortcuts.</p>
          </div>
          <div className="p-4 flex flex-col gap-2">
            {[
              { icon: Bus, label: "Register New Bus", desc: "Add a vehicle to fleet tracking" },
              { icon: UserRound, label: "Add Driver", desc: "Create a new driver profile" },
              { icon: Route, label: "Map Route", desc: "Draw a trajectory on the map" },
              { icon: Activity, label: "System Logs", desc: "View server connectivity logs" }
            ].map((action, i) => (
              <button key={i} className="flex items-start gap-4 p-3 rounded-xl hover:bg-surface-container-low transition-colors text-left group">
                 <div className="h-10 w-10 shrink-0 bg-secondary-container/50 text-on-secondary-container rounded-lg flex items-center justify-center group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                    <action.icon className="h-5 w-5" />
                 </div>
                 <div>
                    <p className="text-[14px] font-bold text-foreground">{action.label}</p>
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