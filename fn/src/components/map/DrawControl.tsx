import { useEffect, useRef } from "react";
import { useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet-draw/dist/leaflet.draw.css";
import "leaflet-draw";

export type ShapeType = "polygon" | "polyline";

export interface DrawnShape {
  id: string;
  type: ShapeType;
  latlngs: L.LatLng[] | L.LatLng[][];
  vertexCount: number;
}

interface DrawControlProps {
  onChange?: (shapes: DrawnShape[]) => void;
  initialCoordinates?: [number, number][];
  initialShapeType?: "polygon" | "polyline";
}

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

function DrawControl({ onChange, initialCoordinates, initialShapeType = "polygon" }: DrawControlProps) {
  const map = useMap();
  const drawnItemsRef = useRef<L.FeatureGroup>(new L.FeatureGroup());
  const shapeMapRef = useRef<Map<L.Layer, DrawnShape>>(new Map());
  const onChangeRef = useRef(onChange);
  useEffect(() => { onChangeRef.current = onChange; });

  useEffect(() => {
    const drawnItems = drawnItemsRef.current;
    const shapeMap = shapeMapRef.current;

    drawnItems.clearLayers();
    shapeMap.clear();

    if (initialCoordinates && initialCoordinates.length > 0) {
      const shape =
        initialShapeType === "polygon"
          ? L.polygon(initialCoordinates, {
              color: "#06b6d4",
              weight: 3,
              opacity: 1,
              fillColor: "#06b6d4",
              fillOpacity: 0.22,
              dashArray: "8 5",
            })
          : L.polyline(initialCoordinates, {
              color: "#818cf8",
              weight: 5,
              opacity: 0.9,
              dashArray: "14 7",
            });
      drawnItems.addLayer(shape);
      shapeMap.set(shape, layerToShape(uid(), shape)!);
    }

    map.addLayer(drawnItems);

    // Notify parent about the pre-loaded shape so it's in state from the start
    const emitCurrent = () => {
      if (!onChangeRef.current) return;
      const shapes: DrawnShape[] = [];
      shapeMap.forEach((shape: DrawnShape) => shapes.push(shape));
      onChangeRef.current(shapes);
    };
    emitCurrent();

    // After the flex layout has settled, invalidate the map size and zoom to
    // the initial shape so it's clearly visible in the viewport.
    let fitTimer: ReturnType<typeof setTimeout> | undefined;
    if (initialCoordinates && initialCoordinates.length > 0) {
      fitTimer = setTimeout(() => {
        try {
          const bounds = L.polyline(initialCoordinates).getBounds();
          if (bounds.isValid()) {
            map.invalidateSize();
            map.fitBounds(bounds, { padding: [50, 50], maxZoom: 17, animate: false });
          }
        } catch {
          // fall back to default view
        }
      }, 250);
    }

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
            color: "#06b6d4",
            weight: 3,
            opacity: 1,
            fillColor: "#06b6d4",
            fillOpacity: 0.25,
          },
          icon: new L.DivIcon({
            iconSize: new L.Point(8, 8),
            className: "leaflet-div-icon",
          }),
        },
        // ── Polyline ─────────────────────────────────────────────
        polyline: {
          shapeOptions: {
            color: "#818cf8",
            weight: 5,
            opacity: 1,
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
      if (!onChangeRef.current) return;
      const shapes: DrawnShape[] = [];
      shapeMap.forEach((shape: DrawnShape) => shapes.push(shape));
      onChangeRef.current(shapes);
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
      clearTimeout(fitTimer);
      map.removeControl(drawControl);
      map.removeLayer(drawnItems);
      map.off(L.Draw.Event.CREATED);
      map.off(L.Draw.Event.EDITED);
      map.off(L.Draw.Event.DELETED);
    };
  }, [map, initialCoordinates, initialShapeType]);

  return null;
}

export default DrawControl;
