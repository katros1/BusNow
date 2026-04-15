import "leaflet/dist/leaflet.css";
import "./map.css";

import L from "leaflet";
import { MapContainer, TileLayer, useMap, Polygon, Tooltip } from "react-leaflet";
import { useEffect } from "react";
import type { RouteBusPark, RouteStop } from "../../app/routes/api/routes.types";
import SearchControl from "./SearchControl";
import DrawControl, { type DrawnShape } from "./DrawControl";

// ── Rwanda geographic constants ──────────────────────────────────────────────
const RWANDA_CENTER: L.LatLngExpression = [-1.9441, 29.8739];

const RWANDA_BOUNDS = L.latLngBounds(
  L.latLng(-2.8389, 28.8617), // SW corner
  L.latLng(-1.0474, 30.8990)  // NE corner
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

// ── Public API ───────────────────────────────────────────────────────────────
export interface MapViewProps {
  /** Fires whenever shapes are drawn, edited, or deleted. */
  onShapesChange?: (shapes: DrawnShape[]) => void;
  initialCoordinates?: [number, number][];
  initialShapeType?: "polygon" | "polyline";
  parks?: RouteBusPark[];
  stops?: RouteStop[];
}

function MapView({ onShapesChange, initialCoordinates, initialShapeType, parks, stops }: MapViewProps) {
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
      {/*
        CartoDB Voyager — bright, detailed light basemap. No API key required.
        Pairs perfectly with the project's white/surface design system.
      */}
      <TileLayer
        url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
        subdomains="abcd"
        maxZoom={19}
      />

      {/* Lock the map inside Rwanda */}
      <RwandaBoundsEnforcer />

      {/* Rwanda-only place search */}
      <SearchControl />

      {/* Map Stops and Parks */}
      {parks?.map((park) => (
        <Polygon 
          key={park.id} 
          positions={park.coordinates as [number, number][]} 
          pathOptions={{ color: "#3b82f6", weight: 2, fillColor: "#3b82f6", fillOpacity: 0.2 }}
        >
          <Tooltip direction="top" offset={[0, -10]} opacity={1}>
            Terminal: {park.name}
          </Tooltip>
        </Polygon>
      ))}

      {stops?.map((stop) => (
        <Polygon 
          key={stop.id} 
          positions={stop.coordinates as [number, number][]} 
          pathOptions={{ color: "#eab308", weight: 2, fillColor: "#eab308", fillOpacity: 0.4 }}
        >
          <Tooltip direction="top" offset={[0, -10]} opacity={1}>
            Stop {stop.sequence}: {stop.name || 'Unnamed'}
          </Tooltip>
        </Polygon>
      ))}

      {/* Polygon + polyline draw toolbar */}
      <DrawControl onChange={onShapesChange} initialCoordinates={initialCoordinates} initialShapeType={initialShapeType} />
    </MapContainer>
  );
}

export default MapView;
