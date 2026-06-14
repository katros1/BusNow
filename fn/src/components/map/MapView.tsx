import "leaflet/dist/leaflet.css";
import "./map.css";

import L from "leaflet";
import { MapContainer, TileLayer, useMap } from "react-leaflet";
import { useEffect } from "react";

import type { RouteBusPark, RouteStop } from "../../app/routes/api/routes.types";
import SearchControl from "./SearchControl";
import DrawControl, { type DrawnShape } from "./DrawControl";

// ── Rwanda geographic constants ──────────────────────────────────────────────
const RWANDA_CENTER: L.LatLngExpression = [-1.9441, 29.8739];

const RWANDA_BOUNDS = L.latLngBounds(
  L.latLng(-2.8389, 28.8617),
  L.latLng(-1.0474, 30.8990)
);

// ── Restrict panning to Rwanda ───────────────────────────────────────────────
function RwandaBoundsEnforcer() {
  const map = useMap();
  useEffect(() => {
    map.setMaxBounds(RWANDA_BOUNDS);
    map.on("drag", () => map.panInsideBounds(RWANDA_BOUNDS, { animate: false }));
  }, [map]);
  return null;
}

// ── Imperative overlay polygon for park / stop shapes on the route-edit map ──
function OverlayPolygon({
  coordinates,
  color,
  label,
}: {
  coordinates: [number, number][];
  color: string;
  label: string;
}) {
  const map = useMap();
  useEffect(() => {
    if (!coordinates || coordinates.length === 0) return;
    const layer = L.polygon(coordinates, {
      color,
      weight: 3,
      fillColor: color,
      fillOpacity: 0.3,
    }).bindTooltip(label, { direction: "top", sticky: true });
    map.addLayer(layer);
    return () => { map.removeLayer(layer); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

// ── Public API ───────────────────────────────────────────────────────────────
export interface MapViewProps {
  onShapesChange?: (shapes: DrawnShape[]) => void;
  initialCoordinates?: [number, number][];
  initialShapeType?: "polygon" | "polyline";
  parks?: RouteBusPark[];
  stops?: RouteStop[];
}

function MapView({
  onShapesChange,
  initialCoordinates,
  initialShapeType = "polygon",
  parks,
  stops,
}: MapViewProps) {
  return (
    <MapContainer
      center={RWANDA_CENTER}
      zoom={9}
      minZoom={8}
      maxZoom={18}
      maxBounds={RWANDA_BOUNDS}
      maxBoundsViscosity={1.0}
      style={{ width: "100%", height: "100%" }}
    >
      <TileLayer
        url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        attribution="Tiles &copy; Esri"
        maxZoom={19}
      />

      <RwandaBoundsEnforcer />
      <SearchControl />

      {/* Route-edit overlays: terminal polygons (green) */}
      {parks?.map((park) =>
        park?.coordinates && park.coordinates.length > 0 ? (
          <OverlayPolygon
            key={park.id}
            coordinates={park.coordinates as [number, number][]}
            color="#22c55e"
            label={`Terminal: ${park.name}`}
          />
        ) : null
      )}

      {/* Route-edit overlays: stop polygons (amber) */}
      {stops?.map((stop) =>
        stop?.coordinates && stop.coordinates.length > 0 ? (
          <OverlayPolygon
            key={stop.id}
            coordinates={stop.coordinates as [number, number][]}
            color="#f59e0b"
            label={`Stop ${stop.sequenceIndex}: ${stop.name || "Unnamed"}`}
          />
        ) : null
      )}

      {/* Draw toolbar — renders the initial shape as a visible editable layer */}
      <DrawControl
        onChange={onShapesChange}
        initialCoordinates={initialCoordinates}
        initialShapeType={initialShapeType}
      />
    </MapContainer>
  );
}

export default MapView;
