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

        // last vertex → target
        if (tgtIdx > next) {
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

    // ── Private ───────────────────────────────────────────────────────────────

    private static int nearestVertex(Coordinate[] pts, double lat, double lon) {
        int idx = 0;
        double minD = Double.MAX_VALUE;
        for (int i = 0; i < pts.length; i++) {
            double d = haversineM(lat, lon, pts[i].y, pts[i].x);
            if (d < minD) { minD = d; idx = i; }
        }
        return idx;
    }
}
