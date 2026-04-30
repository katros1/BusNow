import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { MapContainer, TileLayer, Marker, Polyline, Polygon, Tooltip, useMap } from "react-leaflet";
import { useEffect } from "react";
import type { RouteDetailDto, VehiclePositionEvent } from "../api/tracking.types";
import { centroid } from "../utils/geo";

const ESRI_TILE =
  "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
const OSM_TILE = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
const RWANDA_CENTER: L.LatLngExpression = [-1.9441, 29.8739];

function makeVehicleIcon(active: boolean, heading?: number | null): L.DivIcon {
  const color = active ? "#91D06C" : "#4C8CE4";
  const arrow =
    heading != null
      ? `<div style="position:absolute;top:-8px;left:50%;
          transform:translateX(-50%) rotate(${heading}deg);
          width:0;height:0;
          border-left:4px solid transparent;border-right:4px solid transparent;
          border-bottom:8px solid ${color};"></div>`
      : "";
  const ring = active
    ? `<span style="position:absolute;inset:-5px;border-radius:50%;
        border:2px solid ${color};opacity:.25;
        animation:pulse-live 2s cubic-bezier(.4,0,.6,1) infinite;"></span>`
    : "";

  return L.divIcon({
    className: "",
    html: `<div style="position:relative;width:34px;height:34px;
              display:flex;align-items:center;justify-content:center;">
            ${ring}
            <div style="width:28px;height:28px;background:${color};
              border:2.5px solid white;border-radius:50%;
              box-shadow:0 2px 10px ${color}88;
              display:flex;align-items:center;justify-content:center;position:relative;z-index:1;">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="7" width="20" height="12" rx="2"/>
                <path d="M7 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2"/>
                <circle cx="7" cy="19" r="2"/><circle cx="17" cy="19" r="2"/>
              </svg>
            </div>
            ${arrow}
          </div>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
    tooltipAnchor: [0, -20],
  });
}

function makeStopIcon(seq: number): L.DivIcon {
  return L.divIcon({
    className: "",
    html: `<div style="width:20px;height:20px;border-radius:50%;
      background:#FFF799;border:2px solid #856B00;
      display:flex;align-items:center;justify-content:center;
      font-size:8px;font-weight:700;color:#3D3000;
      box-shadow:0 1px 4px rgba(0,0,0,.2);font-family:Inter,sans-serif;">${seq}</div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
    tooltipAnchor: [0, -13],
  });
}

function RouteFitter({ path }: { path: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (path.length < 2) return;
    map.fitBounds(L.latLngBounds(path), { padding: [50, 50], maxZoom: 15 });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

interface TrackingMapTabProps {
  mode: "satellite" | "plain";
  routeDetail: RouteDetailDto | undefined;
  liveEvent: VehiclePositionEvent | undefined;
  plateNumber: string;
  height: string;
}

export function TrackingMapTab({
  mode,
  routeDetail,
  liveEvent,
  plateNumber,
  height,
}: TrackingMapTabProps) {
  const tileUrl   = mode === "satellite" ? ESRI_TILE : OSM_TILE;
  const tileAttr  = mode === "satellite" ? "Tiles © Esri" : "© OpenStreetMap contributors";

  const markerPos: [number, number] | null =
    liveEvent?.latitude && liveEvent?.longitude
      ? [liveEvent.latitude, liveEvent.longitude]
      : null;

  return (
    <MapContainer
      center={RWANDA_CENTER}
      zoom={12}
      minZoom={8}
      maxZoom={18}
      style={{ width: "100%", height }}
      zoomControl={false}
    >
      <TileLayer url={tileUrl} attribution={tileAttr} maxZoom={19} />

      {routeDetail && routeDetail.routePath.length > 1 && (
        <RouteFitter path={routeDetail.routePath} />
      )}

      {/* Route path */}
      {routeDetail && (
        <Polyline
          positions={routeDetail.routePath}
          pathOptions={{ color: "#4C8CE4", weight: 4, opacity: 0.85 }}
        />
      )}

      {/* Start park — green */}
      {routeDetail?.startBusPark && (
        <Polygon
          positions={routeDetail.startBusPark.coordinates}
          pathOptions={{ color: "#2E6B1A", weight: 2, fillColor: "#91D06C", fillOpacity: 0.25 }}
        >
          <Tooltip sticky opacity={1}>
            <b>🚏 {routeDetail.startBusPark.name}</b><br />
            <span style={{ fontSize: 10, opacity: 0.7 }}>Start terminal</span>
          </Tooltip>
        </Polygon>
      )}

      {/* End park — red */}
      {routeDetail?.endBusPark && (
        <Polygon
          positions={routeDetail.endBusPark.coordinates}
          pathOptions={{ color: "#C0392B", weight: 2, fillColor: "#C0392B", fillOpacity: 0.2 }}
        >
          <Tooltip sticky opacity={1}>
            <b>🏁 {routeDetail.endBusPark.name}</b><br />
            <span style={{ fontSize: 10, opacity: 0.7 }}>End terminal</span>
          </Tooltip>
        </Polygon>
      )}

      {/* Stops */}
      {routeDetail?.stops.map((stop) => {
        const [cLat, cLon] = centroid(stop.coordinates);
        return (
          <Marker key={stop.id} position={[cLat, cLon]} icon={makeStopIcon(stop.sequence)}>
            <Tooltip direction="top" offset={[0, -13]} opacity={1}>
              <b>Stop {stop.sequence}: {stop.name}</b>
            </Tooltip>
          </Marker>
        );
      })}

      {/* Live vehicle */}
      {markerPos && (
        <Marker
          position={markerPos}
          icon={makeVehicleIcon(!!(liveEvent?.trip), liveEvent?.headingDeg)}
        >
          <Tooltip direction="top" offset={[0, -20]} opacity={1}>
            <div style={{ fontFamily: "Inter,sans-serif" }}>
              <b style={{ fontSize: 12 }}>{plateNumber}</b>
              {liveEvent?.speedKmh != null && (
                <div style={{ fontSize: 10, opacity: 0.75 }}>
                  {Math.round(liveEvent.speedKmh)} km/h
                </div>
              )}
              {liveEvent?.trip && (
                <div style={{ fontSize: 10, color: "#2E6B1A", fontWeight: 600 }}>
                  {liveEvent.trip.onBoard} pax on board
                </div>
              )}
            </div>
          </Tooltip>
        </Marker>
      )}
    </MapContainer>
  );
}
