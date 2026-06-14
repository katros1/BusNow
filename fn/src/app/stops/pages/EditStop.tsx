import { useState } from "react";
import { useParams, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { MapPin, ChevronLeft, Waypoints } from "lucide-react";
import { stopsApi } from "../api/stops.api";
import { stopKeys } from "../api/stops.keys";
import { useUpdateStop } from "../api/stops.mutations";
import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";

import type { Stop } from "../api/stops.types";

type CoordinateObj = { lat: number; lng: number };

export default function EditStop() {
  const { stopId } = useParams({ strict: false }) as { stopId: string };
  const { data: stop, isLoading } = useQuery({
    queryKey: stopKeys.detail(stopId),
    queryFn: () => stopsApi.getById(stopId),
    enabled: !!stopId,
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-20">
        <div className="w-8 h-8 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
        <span className="mt-4 text-sm text-muted-foreground">Loading stop data...</span>
      </div>
    );
  }

  if (!stop) {
    return (
      <div className="flex flex-col items-center justify-center p-20 text-red-500">
        Error: Stop not found.
      </div>
    );
  }

  return <EditStopForm stop={stop} stopId={stopId} />;
}

function EditStopForm({ stop, stopId }: { stop: Stop, stopId: string }) {
  const navigate = useNavigate();
  const [editName, setEditName] = useState(stop.name);
  const [editShapes, setEditShapes] = useState<DrawnShape[]>([]);

  const updateStopMut = useUpdateStop(stopId);

  const handleSave = () => {
    let coords = stop.coordinates;
    if (editShapes.length > 0 && editShapes[editShapes.length - 1].type === "polygon") {
      const mapLatLngs = editShapes[editShapes.length - 1].latlngs as unknown as CoordinateObj[][];
      coords = mapLatLngs[0].map((pt: CoordinateObj) => [pt.lat, pt.lng]);
    }

    updateStopMut.mutate(
      {
        name: editName,
        coordinates: coords,
      },
      {
        onSuccess: () => navigate({ to: "/stops" }),
      }
    );
  };

  const vertexCount = editShapes.length > 0
    ? editShapes.reduce((acc, curr) => acc + curr.vertexCount, 0)
    : stop.coordinates?.length ?? 0;

  const isComplete = !!editName.trim();

  return (
    <div className="flex flex-col h-[calc(100vh-80px)] max-w-[1400px] mx-auto w-full">
      {/* Header */}
      <div className="px-6 py-4 flex items-center justify-between border-b border-border bg-white sticky top-0 z-10 shrink-0">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: "/stops" })}
            className="w-9 h-9 flex items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary transition-all"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div>
            <h1 className="text-[20px] font-bold tracking-tight text-foreground flex items-center gap-2">
              <Waypoints className="h-5 w-5 text-amber-500" />
              Edit Bus Stop
            </h1>
            <p className="text-[12px] text-muted-foreground mt-0.5">
              Update the name and boundary zone for this passenger stop.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => navigate({ to: "/stops" })}
            className="px-4 h-9 rounded-lg text-sm font-semibold text-muted-foreground hover:bg-surface-container-low transition-colors"
          >
            Discard
          </button>
          <button
            onClick={handleSave}
            disabled={updateStopMut.isPending || !isComplete}
            className="px-5 h-9 bg-primary text-primary-foreground text-sm font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {updateStopMut.isPending ? "Saving..." : "Save Stop"}
          </button>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row flex-1 overflow-hidden">
        {/* Left Panel */}
        <div className="w-full lg:w-[340px] shrink-0 border-r border-border bg-white flex flex-col overflow-y-auto z-10">
          {/* Stop identity section */}
          <div className="p-5 border-b border-border">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">
              Stop Name
            </label>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border bg-white outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 transition-all font-semibold text-[14px]"
              placeholder="e.g. Kimironko Market"
            />
          </div>

          {/* Zone info section */}
          <div className="p-5 border-b border-border">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-3">
              Stop Zone
            </label>

            <div className="rounded-xl bg-gradient-to-br from-amber-500/5 to-amber-500/10 border border-amber-500/15 p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-8 h-8 rounded-full bg-amber-500/15 flex items-center justify-center shrink-0">
                  <MapPin className="h-4 w-4 text-amber-500" />
                </div>
                <div>
                  <p className="text-[13px] font-bold text-amber-600">{vertexCount} vertices</p>
                  <p className="text-[11px] text-muted-foreground">
                    {editShapes.length > 0 ? "New zone drawn" : "Current boundary"}
                  </p>
                </div>
              </div>
              <p className="text-[12px] text-muted-foreground leading-relaxed">
                The existing stop zone is shown on the map. To change it, use the polygon tool to draw a new zone, or click the edit icon to reposition vertices.
              </p>
            </div>
          </div>

          {/* Instructions */}
          <div className="p-5 flex-1">
            <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-3">
              How to Edit
            </label>
            <ol className="space-y-3">
              {[
                { step: "1", text: "The existing stop polygon is displayed on the map." },
                { step: "2", text: "Hover over the polygon and click the Edit Layers icon to reposition vertices." },
                { step: "3", text: "Or draw a completely new zone using the polygon tool." },
                { step: "4", text: "Click Save Stop when you're done." },
              ].map(({ step, text }) => (
                <li key={step} className="flex gap-3 items-start">
                  <span className="w-5 h-5 rounded-full bg-amber-500/10 text-amber-600 text-[11px] font-bold flex items-center justify-center shrink-0 mt-0.5">
                    {step}
                  </span>
                  <p className="text-[12px] text-muted-foreground leading-relaxed">{text}</p>
                </li>
              ))}
            </ol>
          </div>

          {updateStopMut.isError && (
            <div className="mx-5 mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-[12px] text-red-700 font-medium">
              Failed to save. Please try again.
            </div>
          )}
        </div>

        {/* Map */}
        <div className="flex-1 relative z-0 bg-[#e5e3df] min-h-[400px]">
          <MapView
            onShapesChange={setEditShapes}
            initialCoordinates={stop.coordinates as [number, number][]}
          />
        </div>
      </div>
    </div>
  );
}
