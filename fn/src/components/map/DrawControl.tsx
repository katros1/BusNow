import { useEffect, useRef } from "react";
import { useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet-draw/dist/leaflet.draw.css";
import "leaflet-draw";

// ── Public types ─────────────────────────────────────────────────────────────

export type ShapeType = "polygon" | "polyline";

export interface DrawnShape {
  /** Stable unique ID for this shape — survives re-renders. */
  id: string;
  type: ShapeType;
  /** Flat ring for polylines; nested rings for polygons (outer + holes). */
  latlngs: L.LatLng[] | L.LatLng[][];
  /** Vertex count (total points in the outer ring). */
  vertexCount: number;
}

interface DrawControlProps {
  /** Fired after every draw / edit / delete with the full current shape list. */
  onChange?: (shapes: DrawnShape[]) => void;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function uid(): string {
  return crypto.randomUUID();
}

function vertexCount(layer: L.Layer): number {
  if (layer instanceof L.Polygon) {
    const rings = layer.getLatLngs() as L.LatLng[][];
    return (rings[0] ?? []).length;
  }
  if (layer instanceof L.Polyline) {
    return (layer.getLatLngs() as L.LatLng[]).length;
  }
  return 0;
}

function layerToShape(id: string, layer: L.Layer): DrawnShape | null {
  // L.Polygon extends L.Polyline, so check Polygon first
  if (layer instanceof L.Polygon) {
    return {
      id,
      type: "polygon",
      latlngs: (layer.getLatLngs() as unknown) as L.LatLng[][],
      vertexCount: vertexCount(layer),
    };
  }
  if (layer instanceof L.Polyline) {
    return {
      id,
      type: "polyline",
      latlngs: layer.getLatLngs() as L.LatLng[],
      vertexCount: vertexCount(layer),
    };
  }
  return null;
}

// ── Component ────────────────────────────────────────────────────────────────

/**
 * Mounts Leaflet.draw inside a <MapContainer>.
 *
 * Supports:  polygon, polyline
 * Disabled:  rectangle, circle, circlemarker, marker
 * Tracks every shape with a stable UUID so callers can correlate
 * drawn geometries with backend records.
 */
function DrawControl({ onChange }: DrawControlProps) {
  const map = useMap();
  const drawnItemsRef = useRef<L.FeatureGroup>(new L.FeatureGroup());
  /** layer → DrawnShape (keeps IDs stable across edits) */
  const shapeMapRef = useRef<Map<L.Layer, DrawnShape>>(new Map());

  useEffect(() => {
    const drawnItems = drawnItemsRef.current;
    const shapeMap = shapeMapRef.current;

    map.addLayer(drawnItems);

    const drawControl = new L.Control.Draw({
      position: "topright",
      edit: {
        featureGroup: drawnItems,
        remove: true,
      },
      draw: {
        // ── Polygon ──────────────────────────────────────────────
        polygon: {
          allowIntersection: false,
          showArea: true,
          shapeOptions: {
            color: "#005BBF",
            weight: 2,
            opacity: 1,
            fillColor: "#1a73e8",
            fillOpacity: 0.12,
          },
          icon: new L.DivIcon({
            iconSize: new L.Point(8, 8),
            className: "leaflet-div-icon",
          }),
        },
        // ── Polyline ─────────────────────────────────────────────
        polyline: {
          shapeOptions: {
            color: "#795900",   // Safety Amber — secondary
            weight: 3,
            opacity: 0.85,
            dashArray: "6 4",
          },
          icon: new L.DivIcon({
            iconSize: new L.Point(7, 7),
            className: "leaflet-div-icon",
          }),
        },
        // ── Disabled ─────────────────────────────────────────────
        rectangle:    false,
        circle:       false,
        circlemarker: false,
        marker:       false,
      },
    });

    map.addControl(drawControl);

    /** Re-collect all shapes from the internal map and call onChange. */
    const emit = () => {
      if (!onChange) return;
      const shapes: DrawnShape[] = [];
      shapeMap.forEach((shape) => shapes.push(shape));
      onChange(shapes);
    };

    // ── CREATED ──────────────────────────────────────────────────
    map.on(L.Draw.Event.CREATED, (e) => {
      const layer = (e as L.DrawEvents.Created).layer;
      const id = uid();
      const shape = layerToShape(id, layer);
      if (shape) {
        shapeMap.set(layer, shape);
        drawnItems.addLayer(layer);
        emit();
      }
    });

    // ── EDITED ───────────────────────────────────────────────────
    map.on(L.Draw.Event.EDITED, (e) => {
      (e as L.DrawEvents.Edited).layers.eachLayer((layer) => {
        const existing = shapeMap.get(layer);
        if (!existing) return;
        const updated = layerToShape(existing.id, layer);
        if (updated) shapeMap.set(layer, updated);
      });
      emit();
    });

    // ── DELETED ──────────────────────────────────────────────────
    map.on(L.Draw.Event.DELETED, (e) => {
      (e as L.DrawEvents.Deleted).layers.eachLayer((layer) => {
        shapeMap.delete(layer);
      });
      emit();
    });

    return () => {
      map.removeControl(drawControl);
      map.removeLayer(drawnItems);
      map.off(L.Draw.Event.CREATED);
      map.off(L.Draw.Event.EDITED);
      map.off(L.Draw.Event.DELETED);
    };
  }, [map, onChange]);

  return null;
}

export default DrawControl;
