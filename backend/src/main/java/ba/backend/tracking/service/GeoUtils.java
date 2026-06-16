package ba.backend.tracking.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * Pure geographic math utilities for the tracking pipeline.
 * All coordinates are WGS-84 (latitude, longitude in decimal degrees).
 * JTS Coordinate convention: x = longitude, y = latitude.
 */
public final class GeoUtils {

    private static final double EARTH_R = 6_371_000.0; // metres

    private GeoUtils() {}

    /** Haversine distance in metres between two WGS-84 points. */
    public static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Distance in metres from the route start to the projection of (lat, lon) onto the
     * nearest vertex of the route LineString.
     *
     * <p>This is the primary primitive for stop-advancement: project both the bus and each
     * stop onto the route, then compare scalar distances from the start.  Works for
     * off-route buses — the nearest vertex acts as a reasonable projection even when the
     * GPS position is not on the drawn path.
     */
    public static double distanceFromStartM(LineString route, double lat, double lon) {
        Coordinate[] pts = route.getCoordinates();
        if (pts.length == 0) return 0.0;
        int nearest = nearestVertex(pts, lat, lon);
        double d = 0.0;
        for (int i = 0; i < nearest; i++) {
            d += haversineM(pts[i].y, pts[i].x, pts[i + 1].y, pts[i + 1].x);
        }
        d += haversineM(pts[nearest].y, pts[nearest].x, lat, lon);
        return d;
    }

    /**
     * Metres remaining along a route LineString from the bus to a target point.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Find the nearest route vertex to the bus (busIdx).</li>
     *   <li>Find the nearest route vertex to the target (tgtIdx).</li>
     *   <li>Walk forward: bus → pts[busIdx+1] → … → pts[tgtIdx] → target, summing haversine per segment.</li>
     *   <li>If target is behind the bus on the route, fall back to straight-line haversine.</li>
     * </ol>
     */
    public static double distanceAlongLineM(LineString route,
                                            double busLat, double busLon,
                                            double targetLat, double targetLon) {
        Coordinate[] pts = route.getCoordinates();
        if (pts.length < 2) {
            return haversineM(busLat, busLon, targetLat, targetLon);
        }

        int busIdx = nearestVertex(pts, busLat, busLon);
        int tgtIdx = nearestVertex(pts, targetLat, targetLon);

        if (tgtIdx <= busIdx) {
            // Target vertex is at or behind bus — return straight-line
            return haversineM(busLat, busLon, targetLat, targetLon);
        }

        // bus → first vertex past busIdx
        int next = Math.min(busIdx + 1, pts.length - 1);
        double total = haversineM(busLat, busLon, pts[next].y, pts[next].x);

        // walk intermediate vertices
        for (int i = next; i < tgtIdx; i++) {
            total += haversineM(pts[i].y, pts[i].x, pts[i + 1].y, pts[i + 1].x);
        }

        // last vertex → target (tgtIdx >= next after the guard above)
        if (tgtIdx >= next) {
            total += haversineM(pts[tgtIdx].y, pts[tgtIdx].x, targetLat, targetLon);
        }

        return total;
    }

    /**
     * Progress (0.0 – 100.0) of the bus along the route LineString from start to end.
     * Uses the nearest vertex to the bus as the projection point.
     */
    public static double progressPercent(LineString route, double busLat, double busLon) {
        Coordinate[] pts = route.getCoordinates();
        if (pts.length < 2) return 0.0;

        double totalLen = 0.0;
        for (int i = 0; i < pts.length - 1; i++) {
            totalLen += haversineM(pts[i].y, pts[i].x, pts[i + 1].y, pts[i + 1].x);
        }
        if (totalLen == 0.0) return 0.0;

        int busIdx = nearestVertex(pts, busLat, busLon);
        double traveled = 0.0;
        for (int i = 0; i < busIdx; i++) {
            traveled += haversineM(pts[i].y, pts[i].x, pts[i + 1].y, pts[i + 1].x);
        }
        traveled += haversineM(pts[busIdx].y, pts[busIdx].x, busLat, busLon);

        return Math.min(100.0, (traveled / totalLen) * 100.0);
    }

    /**
     * Centroid of a JTS Polygon as {@code [lat, lon]}.
     * Returns {@code null} if polygon is null.
     */
    public static double[] centroidOf(Polygon polygon) {
        if (polygon == null) return null;
        var c = polygon.getCentroid();
        return new double[]{c.getY(), c.getX()};
    }

    /**
     * Centroid of a Polygon as {@code [lat, lon]}, correcting for swapped lon/lat storage.
     *
     * <p>Heuristic: if {@code |y| > 10} and {@code |x| < 5} the polygon was stored with
     * longitude in the Y field and latitude in X (common mis-import for Rwanda where
     * lat ≈ −2°, lon ≈ 30°).  In that case the values are swapped before returning.
     */
    public static double[] centroidSafe(Polygon polygon) {
        if (polygon == null) return null;
        var c = polygon.getCentroid();
        double rawY = c.getY(), rawX = c.getX();
        if (Math.abs(rawY) > 10 && Math.abs(rawX) < 5) {
            return new double[]{rawX, rawY}; // lat was stored in X, lon in Y
        }
        return new double[]{rawY, rawX}; // normal JTS: y = lat, x = lon
    }

    /**
     * Direction-aware route progress (0.0 – 100.0).
     *
     * <p>Delegates to {@link #progressPercent} and then checks whether the LineString
     * starts near the start bus-park or near the end.  If the <em>last</em> vertex is
     * closer to {@code startCenter} than the first vertex (i.e. the path is stored
     * end → start), the raw value is inverted: {@code 100 − raw}.
     *
     * @param route       route LineString (JTS convention: x = lon, y = lat)
     * @param busLat      bus latitude
     * @param busLon      bus longitude
     * @param startCenter {@code [lat, lon]} centroid of the start bus-park,
     *                    or {@code null} to skip direction correction
     */
    public static double progressPercentNormalized(
            LineString route, double busLat, double busLon, double[] startCenter) {
        double raw = progressPercent(route, busLat, busLon);
        if (startCenter == null) return raw;
        Coordinate[] pts = route.getCoordinates();
        if (pts.length < 2) return raw;
        double distFirst = haversineM(pts[0].y,              pts[0].x,
                                      startCenter[0],         startCenter[1]);
        double distLast  = haversineM(pts[pts.length - 1].y, pts[pts.length - 1].x,
                                      startCenter[0],         startCenter[1]);
        // Last vertex closer to start park → path is stored end→start → invert
        return (distLast < distFirst) ? Math.max(0.0, Math.min(100.0, 100.0 - raw)) : raw;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    static int nearestVertex(Coordinate[] pts, double lat, double lon) {
        int idx = 0;
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < pts.length; i++) {
            double d = haversineM(lat, lon, pts[i].y, pts[i].x);
            if (d < minD) { minD = d; idx = i; }
        }
        return idx;
    }
}
