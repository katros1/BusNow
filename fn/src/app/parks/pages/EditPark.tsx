import { useState } from "react";
import { useParams, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { MapPin, ChevronLeft, Map as MapIcon } from "lucide-react";
import { parksApi } from "../api/parks.api";
import { parkKeys } from "../api/parks.keys";
import { useUpdatePark } from "../api/parks.mutations";
import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";

import type { Park } from "../api/parks.types";

type CoordinateObj = { lat: number; lng: number }; 

export default function EditPark() {
  const { parkId } = useParams({ strict: false }) as { parkId: string };
  const { data: park, isLoading } = useQuery({
    queryKey: parkKeys.detail(parkId),
    queryFn: () => parksApi.getById(parkId),
    enabled: !!parkId,
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-20">
        <div className="w-8 h-8 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
        <span className="mt-4 text-sm text-muted-foreground">Loading park data...</span>
      </div>
    );
  }

  if (!park) {
    return (
      <div className="flex flex-col items-center justify-center p-20 text-red-500">
        Error: Park not found.
      </div>
    );
  }

  return <EditParkForm park={park} parkId={parkId} />;
}

function EditParkForm({ park, parkId }: { park: Park, parkId: string }) {
  const navigate = useNavigate();
  const [editName, setEditName] = useState(park.name);
  const [editShapes, setEditShapes] = useState<DrawnShape[]>([]);

  const updateParkMut = useUpdatePark(parkId);

  const handleSave = () => {
    let coords = park.coordinates;
    if (editShapes.length > 0 && editShapes[editShapes.length - 1].type === "polygon") {
      const mapLatLngs = editShapes[editShapes.length - 1].latlngs as unknown as CoordinateObj[][];
      coords = mapLatLngs[0].map((pt: CoordinateObj) => [pt.lat, pt.lng]);
    }

    updateParkMut.mutate(
      {
        name: editName,
        coordinates: coords,
      },
      {
        onSuccess: () => navigate({ to: "/parks" }),
      }
    );
  };

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] max-w-[1400px] mx-auto w-full">
      {/* Header Area */}
      <div className="px-6 py-6 flex items-center justify-between border-b border-border bg-white sticky top-0 z-10 shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate({ to: "/parks" })}
            className="w-10 h-10 flex items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary transition-all"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-[24px] font-bold tracking-tight text-foreground flex items-center gap-2">
              <MapIcon className="h-6 w-6 text-primary" />
              Edit Service Zone
            </h1>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Modify the physical boundaries and formal identity of this park.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: "/parks" })}
            className="px-5 h-11 rounded-lg font-bold text-muted-foreground hover:bg-surface-container-low transition-colors"
          >
            Discard
          </button>
          <button
            onClick={handleSave}
            disabled={updateParkMut.isPending}
            className="px-6 h-11 bg-primary text-primary-foreground font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {updateParkMut.isPending ? "Saving..." : "Save Route Configuration"}
          </button>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row flex-1 overflow-hidden">
        {/* Left Pane - Inputs */}
        <div className="w-full lg:w-[380px] shrink-0 border-r border-border p-6 bg-white flex flex-col gap-8 overflow-y-auto z-10">
          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Facility Identity
            </label>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full h-11 px-4 rounded-lg border border-border bg-white outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
              placeholder="e.g. Nyabugogo Main Terminal"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Boundary Data
            </label>
            <div className="p-5 rounded-lg bg-primary/5 border border-primary/20">
              <div className="flex items-center gap-3 mb-3">
                <MapPin className="h-5 w-5 text-primary" />
                <span className="font-bold text-[14px] text-primary">
                  {editShapes.length > 0
                    ? editShapes.reduce((acc, curr) => acc + curr.vertexCount, 0)
                    : park.coordinates?.length || 0}{" "}
                  Vertices plotted
                </span>
              </div>
              <p className="text-[13px] text-muted-foreground font-medium leading-relaxed">
                Draw a new zone onto the map, or hover your cursor over the existing polygon and click the 'Edit Layers' icon to reposition vertices manually.
              </p>
            </div>
          </div>
        </div>

        {/* Right Pane - Map */}
        <div className="flex-1 relative z-0 bg-[#e5e3df] min-h-[400px]">
          <MapView
            onShapesChange={setEditShapes}
            initialCoordinates={park.coordinates as [number, number][]}
          />
        </div>
      </div>
    </div>
  );
}
