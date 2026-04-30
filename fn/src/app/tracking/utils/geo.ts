import type { StopShapeDto } from "../api/tracking.types";

export function haversineM(
  lat1: number, lon1: number,
  lat2: number, lon2: number
): number {
  const R = 6_371_000;
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(Δφ / 2) ** 2 +
    Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function centroid(coords: [number, number][]): [number, number] {
  const lat = coords.reduce((s, c) => s + c[0], 0) / coords.length;
  const lon = coords.reduce((s, c) => s + c[1], 0) / coords.length;
  return [lat, lon];
}

function clamp01(t: number): number {
  return Math.max(0, Math.min(1, t));
}

function segmentT(
  px: number, py: number,
  ax: number, ay: number,
  bx: number, by: number
): number {
  const dx = bx - ax, dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) return 0;
  return clamp01(((px - ax) * dx + (py - ay) * dy) / lenSq);
}

/** Returns 0–1 progress of a point projected onto a multi-segment path. */
export function pathProgress(
  lat: number, lon: number,
  path: [number, number][]
): number {
  if (path.length < 2) return 0;

  const segLens: number[] = [];
  let total = 0;
  for (let i = 0; i < path.length - 1; i++) {
    const d = haversineM(path[i][0], path[i][1], path[i + 1][0], path[i + 1][1]);
    segLens.push(d);
    total += d;
  }
  if (total === 0) return 0;

  let minPerp = Infinity, best = 0, cum = 0;
  for (let i = 0; i < path.length - 1; i++) {
    const [la1, lo1] = path[i];
    const [la2, lo2] = path[i + 1];
    const t = segmentT(lat, lon, la1, lo1, la2, lo2);
    const projLat = la1 + t * (la2 - la1);
    const projLon = lo1 + t * (lo2 - lo1);
    const perp = haversineM(lat, lon, projLat, projLon);
    if (perp < minPerp) {
      minPerp = perp;
      best = (cum + t * segLens[i]) / total;
    }
    cum += segLens[i];
  }
  return clamp01(best);
}

/** Project each stop centroid onto the route path, returning 0–1 progress per stop. */
export function stopProgresses(
  stops: StopShapeDto[],
  path: [number, number][]
): number[] {
  return stops.map((stop) => {
    const [sLat, sLon] = centroid(stop.coordinates);
    return pathProgress(sLat, sLon, path);
  });
}

/** Nearest stop to a bus position. */
export function nearestStop(
  lat: number, lon: number,
  stops: StopShapeDto[]
): { stop: StopShapeDto; distanceM: number } | null {
  if (!stops.length) return null;
  let best: StopShapeDto | null = null;
  let bestDist = Infinity;
  for (const stop of stops) {
    const [sLat, sLon] = centroid(stop.coordinates);
    const d = haversineM(lat, lon, sLat, sLon);
    if (d < bestDist) { bestDist = d; best = stop; }
  }
  return best ? { stop: best, distanceM: bestDist } : null;
}

export function formatDistance(m: number): string {
  if (m < 1000) return `${Math.round(m)} m`;
  return `${(m / 1000).toFixed(1)} km`;
}
