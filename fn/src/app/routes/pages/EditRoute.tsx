import { useState } from "react";
import { useParams, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { MapPin, ChevronLeft, Route as RouteIcon, MapPinOff, Bus, Waypoints } from "lucide-react";

import { routesApi } from "../api/routes.api";
import { routeKeys } from "../api/routes.keys";
import { useUpdateRoute } from "../api/routes.mutations";

import { parksApi } from "../../parks/api/parks.api";
import { parkKeys } from "../../parks/api/parks.keys";

import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";
import { TerminalSelect } from "@/components/ui/TerminalSelect";
import { ManageRouteStopsModal } from "../components/ManageRouteStopsModal";
import type { Route } from "../api/routes.types";

type CoordinateObj = { lat: number; lng: number };

export default function EditRoute() {
  const { routeId } = useParams({ strict: false }) as { routeId: string };
  const { data: route, isLoading } = useQuery({
    queryKey: routeKeys.detail(routeId),
    queryFn: () => routesApi.getById(routeId),
    enabled: !!routeId,
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-20">
        <div className="w-8 h-8 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
        <span className="mt-4 text-sm text-muted-foreground">Loading route data...</span>
      </div>
    );
  }

  if (!route) {
    return (
      <div className="flex flex-col items-center justify-center p-20 text-red-500">
        Error: Route not found.
      </div>
    );
  }

  return <EditRouteForm route={route} routeId={routeId} />;
}

function EditRouteForm({ route, routeId }: { route: Route, routeId: string }) {
  const navigate = useNavigate();

  const { data: parksResponse, isLoading: isLoadingParks } = useQuery({
    queryKey: parkKeys.lists(),
    queryFn: () => parksApi.getAll(),
  });
  const parks = parksResponse?.content ?? [];

  const [editName, setEditName] = useState(route.name);
  const [editStartBusParkId, setEditStartBusParkId] = useState(route.startBusPark?.id);
  const [editEndBusParkId, setEditEndBusParkId] = useState(route.endBusPark?.id);
  const [editShapes, setEditShapes] = useState<DrawnShape[]>([]);
  const [isStopsModalOpen, setIsStopsModalOpen] = useState(false);

  const updateRouteMut = useUpdateRoute(routeId);

  const handleSave = () => {
    let coords = route.routePath;
    if (editShapes.length > 0 && editShapes[editShapes.length - 1].type === "polyline") {
      const mapLatLngs = editShapes[editShapes.length - 1].latlngs as unknown as CoordinateObj[];
      coords = mapLatLngs.map((pt: CoordinateObj) => [pt.lat, pt.lng]);
    }

    updateRouteMut.mutate(
      {
        name: editName,
        startBusParkId: editStartBusParkId,
        endBusParkId: editEndBusParkId,
        coordinates: coords,
      },
      {
        onSuccess: () => navigate({ to: "/routes" }),
      }
    );
  };

  const waypointCount = editShapes.length > 0
    ? editShapes.reduce((acc, curr) => acc + curr.vertexCount, 0)
    : route.routePath?.length ?? 0;

  const isComplete = editName && editStartBusParkId && editEndBusParkId;

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] max-w-[1400px] mx-auto w-full">
      {/* Header */}
      <div className="px-6 py-4 flex items-center justify-between border-b border-border bg-white sticky top-0 z-10 shrink-0">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: "/routes" })}
            className="w-9 h-9 flex items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary transition-all"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div>
            <h1 className="text-[20px] font-bold tracking-tight text-foreground flex items-center gap-2">
              <RouteIcon className="h-5 w-5 text-primary" />
              Edit Transit Route
            </h1>
            <p className="text-[12px] text-muted-foreground mt-0.5">
              Modify the terminals, stops, and polyline trajectory for this route.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => navigate({ to: "/routes" })}
            className="px-4 h-9 rounded-lg text-sm font-semibold text-muted-foreground hover:bg-surface-container-low transition-colors"
          >
            Discard
          </button>
          <button
            onClick={handleSave}
            disabled={updateRouteMut.isPending || !isComplete}
            className="px-5 h-9 bg-primary text-primary-foreground text-sm font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {updateRouteMut.isPending ? "Saving..." : "Save Route"}
          </button>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row flex-1 overflow-hidden">
        {/* Left Panel */}
        <div className="w-full lg:w-[360px] shrink-0 border-r border-border bg-white flex flex-col overflow-y-auto z-10">

          {/* Route name */}
          <div className="p-5 border-b border-border">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Route Name
            </label>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border bg-white outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
              placeholder="e.g. Nyabugogo - Kimironko via Downtown"
            />
          </div>

          {/* Terminals */}
          <div className="p-5 border-b border-border space-y-3">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block">
              Terminals
            </label>
            <TerminalSelect
              label="Origin Terminal"
              iconColor="#22c55e"
              options={parks}
              value={editStartBusParkId}
              onChange={setEditStartBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search origin..."
            />
            <TerminalSelect
              label="Destination Terminal"
              iconColor="#ef4444"
              options={parks}
              value={editEndBusParkId}
              onChange={setEditEndBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search destination..."
            />
          </div>

          {/* Stops */}
          <div className="p-5 border-b border-border">
            <div className="flex items-center justify-between mb-3">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">
                Passenger Stops
              </label>
              {route.stops && route.stops.length > 0 && (
                <span className="text-[11px] font-semibold text-amber-600 bg-amber-500/10 px-2 py-0.5 rounded-full">
                  {route.stops.length} stops
                </span>
              )}
            </div>

            {route.stops && route.stops.length > 0 ? (
              <div className="space-y-1.5 mb-3 max-h-[160px] overflow-y-auto pr-1">
                {route.stops.map((stop) => (
                  <div
                    key={stop.id}
                    className="flex items-center gap-2 px-3 py-2 rounded-lg bg-amber-500/5 border border-amber-500/15"
                  >
                    <Waypoints className="h-3.5 w-3.5 text-amber-500 shrink-0" />
                    <span className="text-[12px] font-semibold text-amber-700">{stop.sequenceIndex}.</span>
                    <span className="text-[12px] text-foreground truncate">{stop.name || 'Unnamed'}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex items-center gap-2 text-[12px] text-muted-foreground bg-surface-container-lowest p-2.5 rounded-lg border border-border mb-3">
                <MapPinOff className="h-3.5 w-3.5 shrink-0" />
                No stops assigned to this route.
              </div>
            )}

            <button
              onClick={() => setIsStopsModalOpen(true)}
              className="w-full h-9 bg-white border border-border rounded-lg text-[12px] font-semibold text-foreground hover:bg-surface-container-low transition-colors shadow-sm"
            >
              Manage Route Stops
            </button>
          </div>

          {/* Trajectory info */}
          <div className="p-5 flex-1">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-3">
              Route Polyline
            </label>
            <div className="rounded-xl bg-gradient-to-br from-primary/5 to-primary/10 border border-primary/15 p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-8 h-8 rounded-full bg-primary/15 flex items-center justify-center shrink-0">
                  <MapPin className="h-4 w-4 text-primary" />
                </div>
                <div>
                  <p className="text-[13px] font-bold text-primary">{waypointCount} waypoints</p>
                  <p className="text-[11px] text-muted-foreground">
                    {editShapes.length > 0 ? "New path drawn" : "Current trajectory"}
                  </p>
                </div>
              </div>

              {/* Map legend */}
              <div className="space-y-1.5 mt-3 pt-3 border-t border-primary/10">
                <div className="flex items-center gap-2">
                  <div className="w-4 h-0.5 bg-blue-600 rounded"></div>
                  <span className="text-[11px] text-muted-foreground">Route polyline</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-sm bg-blue-500/30 border border-blue-500"></div>
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <Bus className="h-3 w-3" /> Terminal zones
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-sm bg-amber-500/30 border border-amber-500"></div>
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <Waypoints className="h-3 w-3" /> Passenger stops
                  </span>
                </div>
              </div>

              <p className="text-[12px] text-muted-foreground leading-relaxed mt-3">
                Select the Polyline tool on the map to redraw the trajectory.
              </p>
            </div>
          </div>

          {updateRouteMut.isError && (
            <div className="mx-5 mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-[12px] text-red-700 font-medium">
              Failed to save. Please try again.
            </div>
          )}
        </div>

        {/* Map — shows route polyline + terminal polygons + stop polygons */}
        <div className="flex-1 relative z-0 bg-[#e5e3df] min-h-[400px]">
          <MapView
            onShapesChange={setEditShapes}
            initialCoordinates={route.routePath as [number, number][]}
            initialShapeType="polyline"
            parks={[route.startBusPark, route.endBusPark].filter(Boolean)}
            stops={route.stops}
          />
        </div>
      </div>

      {isStopsModalOpen && (
        <ManageRouteStopsModal
          route={route}
          onClose={() => setIsStopsModalOpen(false)}
        />
      )}
    </div>
  );
}
