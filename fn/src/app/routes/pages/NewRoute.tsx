import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { MapPin, ChevronLeft, Route as RouteIcon } from "lucide-react";
import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";

import { useCreateRoute } from "../api/routes.mutations";
import { parksApi } from "../../parks/api/parks.api";
import { parkKeys } from "../../parks/api/parks.keys";

import { TerminalSelect } from "@/components/ui/TerminalSelect";

type CoordinateObj = { lat: number; lng: number }; 

export default function NewRoute() {
  const navigate = useNavigate();

  // Fetch terminals for selection
  const { data: parks = [], isLoading: isLoadingParks } = useQuery({
    queryKey: parkKeys.lists(),
    queryFn: () => parksApi.getAll(),
  });

  const [name, setName] = useState("");
  const [startBusParkId, setStartBusParkId] = useState("");
  const [endBusParkId, setEndBusParkId] = useState("");
  const [shapes, setShapes] = useState<DrawnShape[]>([]);

  const createRouteMut = useCreateRoute();

  const handleSave = () => {
    if (!name || !startBusParkId || !endBusParkId) return;

    let coords: [number, number][] = [];
    if (shapes.length > 0 && shapes[shapes.length - 1].type === "polyline") {
      // Leaflet polylines return a flat array of LatLng objects, unlike polygons which return nested rings.
      const mapLatLngs = shapes[shapes.length - 1].latlngs as unknown as CoordinateObj[];
      coords = mapLatLngs.map((pt: CoordinateObj) => [pt.lat, pt.lng]);
    }

    createRouteMut.mutate(
      {
        name,
        startBusParkId,
        endBusParkId,
        coordinates: coords,
      },
      {
        onSuccess: () => navigate({ to: "/routes" }),
      }
    );
  };

  const isComplete = name && startBusParkId && endBusParkId && shapes.length > 0;

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
              Draw Transit Route
            </h1>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Specify origin, destination, and plot the exact polyline trajectory for this route.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: "/routes" })}
            className="px-5 h-11 rounded-lg font-bold text-muted-foreground hover:bg-surface-container-low transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={createRouteMut.isPending || !isComplete}
            className="px-6 h-11 bg-primary text-primary-foreground font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {createRouteMut.isPending ? "Configuring..." : "Publish Route Configuration"}
          </button>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row flex-1 overflow-hidden">
        {/* Left Pane - Inputs */}
        <div className="w-full lg:w-[380px] shrink-0 border-r border-border p-6 bg-white flex flex-col gap-8 overflow-y-auto z-10">
          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Route Parameters
            </label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full h-11 px-4 rounded-lg border border-border bg-white outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
              placeholder="e.g. Nyabugogo - Kimironko via Downtown"
            />
          </div>

          <div className="space-y-4">
            <TerminalSelect 
              label="Origin Terminal (Start)"
              iconColor="#22c55e" // green-500
              options={parks}
              value={startBusParkId}
              onChange={setStartBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search origin..."
            />

            <TerminalSelect 
              label="Destination Terminal (End)"
              iconColor="#ef4444" // red-500
              options={parks}
              value={endBusParkId}
              onChange={setEndBusParkId}
              isLoading={isLoadingParks}
              placeholder="Search destination..."
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Trajectory Mapping
            </label>
            <div className={`p-5 rounded-lg border ${shapes.length > 0 ? 'bg-primary/5 border-primary/20' : 'bg-surface-container-lowest border-border'} transition-colors`}>
              <div className="flex items-center gap-3 mb-3">
                <MapPin className={`h-5 w-5 ${shapes.length > 0 ? 'text-primary' : 'text-muted-foreground'}`} />
                <span className={`font-bold text-[14px] ${shapes.length > 0 ? 'text-primary' : 'text-muted-foreground'}`}>
                  {shapes.length > 0
                    ? shapes.reduce((acc, curr) => acc + curr.vertexCount, 0)
                    : 0}{" "}
                  Waypoints plotted
                </span>
              </div>
              <p className="text-[13px] text-muted-foreground font-medium leading-relaxed">
                Connect the origin and destination by drawing a sequence of paths on the right. Select the Polyline tool to begin drafting the sequence.
              </p>
            </div>
          </div>
        </div>

        {/* Right Pane - Map */}
        <div className="flex-1 relative z-0 bg-[#e5e3df] min-h-[400px]">
          <MapView
            onShapesChange={setShapes}
          />
        </div>
      </div>
    </div>
  );
}
