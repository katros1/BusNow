import "leaflet/dist/leaflet.css";
import "./TrackingMapTab.css";
import L from "leaflet";
import {
  MapContainer, TileLayer, Marker, Polyline,
  Polygon, Tooltip, useMap, useMapEvents,
} from "react-leaflet";
import { useEffect, useRef, useState } from "react";
import { Locate, Navigation2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { RouteDetailDto, VehicleLiveSnapshot } from "../api/tracking.types";
import { centroid, lonLatToLatLon } from "../utils/geo";

// ── Tile sources ──────────────────────────────────────────────────────────────

const ESRI_SATELLITE =
  "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
const OSM_STANDARD =
  "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";

const RWANDA_CENTER: L.LatLngExpression = [-1.9441, 29.8739];

// ── Vehicle icon ──────────────────────────────────────────────────────────────

function makeBusIcon(active: boolean, heading?: number | null): L.DivIcon {
  const color = active ? "#22c55e" : "#3b82f6";
  const glow  = active ? "rgba(34,197,94,.35)" : "rgba(59,130,246,.35)";

  const ring = active
    ? `<span class="busnow-ring" style="border-color:${color};"></span>`
    : "";

  const arrow = heading != null
    ? `<span class="busnow-arrow" style="
           border-bottom-color:${color};
           transform: translateX(-50%) rotate(${heading}deg);
         "></span>`
    : "";

  return new L.DivIcon({
    className: "busnow-marker",
    html: `
      <div class="busnow-wrap">
        ${ring}
        <div class="busnow-circle"
          style="background:${color};
                 box-shadow:0 0 0 3px ${glow},0 2px 8px rgba(0,0,0,.25);">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
            stroke="white" stroke-width="2.5"
            stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="7" width="20" height="12" rx="2"/>
            <path d="M7 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2"/>
            <circle cx="7" cy="19" r="2"/>
            <circle cx="17" cy="19" r="2"/>
          </svg>
        </div>
        ${arrow}
      </div>`,
    iconSize:      [40, 40],
    iconAnchor:    [20, 20],
    tooltipAnchor: [0, -26],
  });
}

function makeStopIcon(seq: number): L.DivIcon {
  return new L.DivIcon({
    className: "",
    html: `<div style="width:20px;height:20px;border-radius:50%;
      background:#FFF799;border:2px solid #856B00;
      display:flex;align-items:center;justify-content:center;
      font-size:8px;font-weight:700;color:#3D3000;
      box-shadow:0 1px 4px rgba(0,0,0,.2);font-family:Inter,sans-serif;">${seq}</div>`,
    iconSize:      [20, 20],
    iconAnchor:    [10, 10],
    tooltipAnchor: [0, -13],
  });
}

// ── Map size fixer ────────────────────────────────────────────────────────────
//
// Radix UI (shadcn Tabs) keeps inactive TabsContent in the DOM with display:none.
// Leaflet initialises inside a zero-size container → blank tiles + no polylines.
// A ResizeObserver fires whenever the container transitions from 0px to its real
// size (i.e. when the tab becomes active), and we call invalidateSize() at that
// point so Leaflet redraws everything correctly.

function MapSizeFixer() {
  const map = useMap();
  useEffect(() => {
    const container = map.getContainer();
    const flush = () => map.invalidateSize({ pan: false });

    // Immediate fix for first mount
    flush();

    const ro = new ResizeObserver(flush);
    ro.observe(container);
    return () => ro.disconnect();
  }, [map]);
  return null;
}

// ── Route fitter — fires once when route data first arrives ───────────────────

function RouteFitter({ path }: { path: [number, number][] }) {
  const map    = useMap();
  const fitted = useRef(false);
  useEffect(() => {
    if (fitted.current || path.length < 2) return;
    fitted.current = true;
    map.fitBounds(L.latLngBounds(path), { padding: [60, 60], maxZoom: 15 });
  }, [path, map]);
  return null;
}

// ── Vehicle tracker — auto-pans when following=true ───────────────────────────

function VehicleTracker({
  pos,
  following,
  onUserMove,
}: {
  pos: [number, number] | null;
  following: boolean;
  onUserMove: () => void;
}) {
  const map     = useMap();
  const firstRef = useRef(true);

  // Detect manual pan/zoom — disable following
  useMapEvents({
    dragstart: onUserMove,
    zoomstart: onUserMove,
  });

  useEffect(() => {
    if (!pos || !following) return;
    if (firstRef.current) {
      firstRef.current = false;
      map.setView(pos, Math.max(map.getZoom(), 14), { animate: true });
    } else {
      map.panTo(pos, { animate: true, duration: 0.8 });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pos?.[0], pos?.[1], following]);

  return null;
}

// ── Component ─────────────────────────────────────────────────────────────────

interface TrackingMapTabProps {
  mode:        "satellite" | "plain";
  routeDetail: RouteDetailDto | undefined;
  liveEvent:   VehicleLiveSnapshot | undefined;
  plateNumber: string;
}

export function TrackingMapTab({
  mode, routeDetail, liveEvent, plateNumber,
}: TrackingMapTabProps) {
  const [following, setFollowing] = useState(true);

  const tileUrl  = mode === "satellite" ? ESRI_SATELLITE : OSM_STANDARD;
  const tileAttr = mode === "satellite"
    ? "Tiles &copy; Esri — Source: Esri, USGS, NOAA"
    : "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>";

  const lat = liveEvent?.latitude;
  const lng = liveEvent?.longitude;
  const markerPos: [number, number] | null =
    lat != null && lng != null ? [lat, lng] : null;

  const active  = liveEvent?.tripId != null;
  const heading = liveEvent?.headingDeg;
  const busIcon = makeBusIcon(active, heading);

  return (
    // Wrapper takes 100% of the flex-1 TabsContent — no fixed height needed
    <div className="relative w-full h-full" style={{ minHeight: 280 }}>
      <MapContainer
        center={RWANDA_CENTER}
        zoom={12}
        minZoom={8}
        maxZoom={19}
        style={{ width: "100%", height: "100%" }}
        zoomControl={true}
      >
        {/* Fix blank map when tab transitions from display:none → visible */}
        <MapSizeFixer />

        <TileLayer url={tileUrl} attribution={tileAttr} maxZoom={19} />

        {/* Fit to full route bounds — swap [lon,lat]→[lat,lon] for Leaflet */}
        {routeDetail && routeDetail.routePath.length > 1 && (
          <RouteFitter path={lonLatToLatLon(routeDetail.routePath)} />
        )}

        {/* Auto-pan + user-interaction detector */}
        <VehicleTracker
          pos={markerPos}
          following={following}
          onUserMove={() => setFollowing(false)}
        />

        {/* ── Route polyline from busnow_route.geo ───────────────────────── */}
        {routeDetail && routeDetail.routePath.length >= 2 && (
          <Polyline
            positions={lonLatToLatLon(routeDetail.routePath)}
            pathOptions={{
              color: "#3b82f6", weight: 5, opacity: 0.85,
              lineCap: "round", lineJoin: "round",
            }}
          />
        )}

        {/* ── Start bus-park polygon — green ──────────────────────────── */}
        {routeDetail?.startBusPark?.coordinates?.length >= 3 && (
          <Polygon
            positions={lonLatToLatLon(routeDetail.startBusPark.coordinates)}
            pathOptions={{ color: "#166534", weight: 2, fillColor: "#22c55e", fillOpacity: 0.3 }}
          >
            <Tooltip sticky opacity={1}>
              <b>🚏 {routeDetail.startBusPark.name}</b><br />
              <span style={{ fontSize: 10, opacity: 0.7 }}>Start terminal</span>
            </Tooltip>
          </Polygon>
        )}

        {/* ── End bus-park polygon — red ───────────────────────────────── */}
        {routeDetail?.endBusPark?.coordinates?.length >= 3 && (
          <Polygon
            positions={lonLatToLatLon(routeDetail.endBusPark.coordinates)}
            pathOptions={{ color: "#991b1b", weight: 2, fillColor: "#ef4444", fillOpacity: 0.25 }}
          >
            <Tooltip sticky opacity={1}>
              <b>🏁 {routeDetail.endBusPark.name}</b><br />
              <span style={{ fontSize: 10, opacity: 0.7 }}>End terminal</span>
            </Tooltip>
          </Polygon>
        )}

        {/* ── Stop markers + polygon outline ───────────────────────────── */}
        {routeDetail?.stops.map((stop) => {
          const swapped = lonLatToLatLon(stop.coordinates);
          const [cLat, cLon] = centroid(swapped);
          return (
            <Marker key={stop.id} position={[cLat, cLon]} icon={makeStopIcon(stop.sequence)}>
              <Tooltip direction="top" offset={[0, -13]} opacity={1}>
                <b>Stop {stop.sequence}: {stop.name}</b>
              </Tooltip>
            </Marker>
          );
        })}

        {/* Live vehicle marker */}
        {markerPos && (
          <Marker
            key={`bus-${plateNumber}`}
            position={markerPos}
            icon={busIcon}
            zIndexOffset={1000}
          >
            <Tooltip direction="top" offset={[0, -26]} opacity={1}>
              <div style={{ fontFamily: "Inter,sans-serif", minWidth: 80 }}>
                <b style={{ fontSize: 12 }}>{plateNumber}</b>
                {liveEvent?.speedKmh != null && (
                  <div style={{ fontSize: 10, opacity: 0.75 }}>
                    {Math.round(liveEvent.speedKmh)} km/h
                  </div>
                )}
                {active && liveEvent?.passengersOnBoard != null && (
                  <div style={{ fontSize: 10, color: "#166534", fontWeight: 600 }}>
                    {liveEvent.passengersOnBoard} pax on board
                  </div>
                )}
                {liveEvent?.gpsStale && (
                  <div style={{ fontSize: 9, color: "#b45309" }}>Last known position</div>
                )}
              </div>
            </Tooltip>
          </Marker>
        )}
      </MapContainer>

      {/* ── Follow button — overlaid on map, bottom-right ───────────── */}
      {markerPos && (
        <button
          onClick={() => setFollowing(true)}
          className={cn(
            "absolute bottom-10 right-3 z-[1000] flex items-center gap-1.5",
            "rounded-full px-3 py-1.5 text-[11px] font-semibold shadow-md",
            "border transition-all duration-200",
            following
              ? "bg-[#22c55e] border-[#16a34a] text-white cursor-default"
              : "bg-white border-border text-foreground hover:bg-muted active:scale-95",
          )}
          disabled={following}
          title={following ? "Following bus" : "Click to follow bus"}
        >
          {following
            ? <Navigation2 className="h-3 w-3" />
            : <Locate className="h-3 w-3" />}
          {following ? "Following" : "Follow Bus"}
        </button>
      )}

      {/* No GPS placeholder */}
      {!markerPos && !routeDetail && (
        <div className="absolute inset-0 z-[500] flex items-center justify-center pointer-events-none">
          <div className="bg-card/80 backdrop-blur-sm rounded-xl px-5 py-3 border border-border/60 flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground animate-pulse" />
            <span className="text-[12px] text-muted-foreground">Waiting for GPS signal…</span>
          </div>
        </div>
      )}
    </div>
  );
}
