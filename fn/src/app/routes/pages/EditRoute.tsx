import { useState } from "react";
import { useParams, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { MapPin, ChevronLeft, Route as RouteIcon } from "lucide-react";

import { routesApi } from "../api/routes.api";
import { routeKeys } from "../api/routes.keys";
import { useUpdateRoute } from "../api/routes.mutations";

import { parksApi } from "../../parks/api/parks.api";
import { parkKeys } from "../../parks/api/parks.keys";

import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";
import { TerminalSelect } from "@/components/ui/TerminalSelect";
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

  // Fetch terminals for selection
  const { data: parks = [], isLoading: isLoadingParks } = useQuery({
    queryKey: parkKeys.lists(),
    queryFn: () => parksApi.getAll(),
  });

  const [editName, setEditName] = useState(route.name);
  const [editStartBusParkId, setEditStartBusParkId] = useState(route.startBusParkId);
  const [editEndBusParkId, setEditEndBusParkId] = useState(route.endBusParkId);
  const [editShapes, setEditShapes] = useState<DrawnShape[]>([]);

  const updateRouteMut = useUpdateRoute(routeId);

  const handleSave = () => {
    let coords = route.coordinates;
    if (editShapes.length > 0 && editShapes[editShapes.length - 1].type === "polyline") {
      // Leaflet polylines return a flat array of LatLng objects, unlike polygons which return nested rings.
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

  const isComplete = editName && editStartBusParkId && editEndBusParkId;

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] max-w-[1400px] mx-auto w-full">
      {/* Header Area */}
      <div className="px-6 py-6 flex items-center justify-between border-b border-border bg-white sticky top-0 z-10 shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate({ to: "/routes" })}
            className="w-10 h-10 flex items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary transition-all"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-[24px] font-bold tracking-tight text-foreground flex items-center gap-2">
              <RouteIcon className="h-6 w-6 text-primary" />
              Edit Transit Route
            </h1>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Modify the origin, destination, and the exact polyline trajectory for this route.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: "/routes" })}
            className="px-5 h-11 rounded-lg font-bold text-muted-foreground hover:bg-surface-container-low transition-colors"
          >
            Discard
          </button>
          <button
            onClick={handleSave}
            disabled={updateRouteMut.isPending || !isComplete}
            className="px-6 h-11 bg-primary text-primary-foreground font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {updateRouteMut.isPending ? "Saving..." : "Save Route Configuration"}
          </button>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row flex-1 overflow-hidden">
        {/* Left Pane - Inputs */}
        <div className="w-full lg:w-[380px] shrink-0 border-r border-border p-6 bg-white flex flex-col gap-8 overflow-y-auto z-10">
          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Route Designator
            </label>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full h-11 px-4 rounded-lg border border-border bg-white outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
              placeholder="e.g. Nyabugogo - Kimironko via Downtown"
            />
          </div>

          <div className="space-y-4">
            <TerminalSelect 
              label="Origin Terminal (Start)"
              iconColor="#22c55e" // green-500
              options={parks}
              value={editStartBusParkId}
              onChange={setEditStartBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search origin..."
            />

            <TerminalSelect 
              label="Destination Terminal (End)"
              iconColor="#ef4444" // red-500
              options={parks}
              value={editEndBusParkId}
              onChange={setEditEndBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search destination..."
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Trajectory Mapping
            </label>
            <div className="p-5 rounded-lg bg-primary/5 border border-primary/20">
              <div className="flex items-center gap-3 mb-3">
                <MapPin className="h-5 w-5 text-primary" />
                <span className="font-bold text-[14px] text-primary">
                  {editShapes.length > 0
                    ? editShapes.reduce((acc, curr) => acc + curr.vertexCount, 0)
                    : route.coordinates?.length || 0}{" "}
                  Waypoints plotted
                </span>
              </div>
              <p className="text-[13px] text-muted-foreground font-medium leading-relaxed">
                Connect the origin and destination by drawing a sequence of paths on the right. Select the Polyline tool to edit the sequence natively.
              </p>
            </div>
          </div>
        </div>

        {/* Right Pane - Map */}
        <div className="flex-1 relative z-0 bg-[#e5e3df] min-h-[400px]">
          <MapView
            onShapesChange={setEditShapes}
            initialCoordinates={route.coordinates as [number, number][]}
          />
        </div>
      </div>
    </div>
  );
}
