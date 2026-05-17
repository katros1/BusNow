package ba.backend.tracking.service;

import ba.backend.route.entity.RouteStopEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/**
 * Resolves the current and next bus stop for a given GPS coordinate along a route.
 *
 * <p>Current stop = the stop whose geofence polygon contains the GPS point.
 * <br>Next stop   = lowest-sequence stop after the last passed stop.
 *
 * <p>Also exposes the next stop's centroid coordinates so callers can compute
 * distance-to-next-stop along the route geometry without re-querying the DB.
 */
@Service
public class StopResolverService {

    /** Fallback radius when polygon geofence fails (swapped lat/lon in DB). */
    private static final double STOP_PROXIMITY_M = 80.0;

    private final GeofenceService geofenceService;

    public StopResolverService(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    public record StopResolution(
            UUID   currentStopId,
            String currentStopName,
            int    updatedLastPassedSequence,
            String nextStopName,
            Double nextStopLat,   // centroid of next stop polygon, null if no next stop
            Double nextStopLon
    ) {
        static StopResolution none(int lastPassedSeq) {
            return new StopResolution(null, null, lastPassedSeq, null, null, null);
        }
    }

    public StopResolution resolve(List<RouteStopEntity> stops, double lat, double lon, int lastPassedSeq) {
        if (stops == null || stops.isEmpty()) return StopResolution.none(lastPassedSeq);

        // Detect current stop via geofence, then proximity fallback for swapped-coordinate DBs
        RouteStopEntity current = null;
        for (RouteStopEntity rs : stops) {
            try {
                if (rs.getStop() != null && rs.getStop().getGeo() != null
                        && geofenceService.contains(rs.getStop().getGeo(), lat, lon)) {
                    current = rs;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (current == null) {
            for (RouteStopEntity rs : stops) {
                if (rs.getStop() == null || rs.getStop().getGeo() == null) continue;
                try {
                    Point centroid = rs.getStop().getGeo().getCentroid();
                    double rawY = centroid.getY(), rawX = centroid.getX();
                    double cLat, cLon;
                    if (Math.abs(rawY) > 10 && Math.abs(rawX) < 5) {
                        cLat = rawX; cLon = rawY;
                    } else {
                        cLat = rawY; cLon = rawX;
                    }
                    if (GeoUtils.haversineM(lat, lon, cLat, cLon) <= STOP_PROXIMITY_M) {
                        current = rs;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        int newLastPassed = (current != null && current.getSequence() > lastPassedSeq)
                ? current.getSequence() : lastPassedSeq;

        // Find next stop (lowest sequence above the last passed)
        int effectiveSeq = newLastPassed;
        RouteStopEntity nextStopEntity = stops.stream()
                .filter(rs -> rs.getSequence() > effectiveSeq)
                .min(Comparator.comparingInt(RouteStopEntity::getSequence))
                .orElse(null);

        String nextStopName = nextStopEntity != null ? nextStopEntity.getStop().getName() : null;
        Double nextStopLat  = null;
        Double nextStopLon  = null;
        if (nextStopEntity != null && nextStopEntity.getStop().getGeo() != null) {
            Point centroid = nextStopEntity.getStop().getGeo().getCentroid();
            double rawY = centroid.getY();  // expected lat in JTS convention
            double rawX = centroid.getX();  // expected lon in JTS convention
            // If coordinates were stored with (lat, lon) instead of (lon, lat) in PostGIS,
            // getY() returns the stored lon and getX() returns the stored lat.
            // Detect by checking if rawY looks like a longitude (large absolute value
            // typical of African longitudes ~28-36°) and rawX like a latitude (small, ~-3 to 0).
            if (Math.abs(rawY) > 10 && Math.abs(rawX) < 5) {
                nextStopLat = rawX;
                nextStopLon = rawY;
            } else {
                nextStopLat = rawY;
                nextStopLon = rawX;
            }
        }

        if (current != null) {
            return new StopResolution(
                    current.getStop().getId(), current.getStop().getName(),
                    newLastPassed, nextStopName, nextStopLat, nextStopLon);
        }
        return new StopResolution(null, null, newLastPassed, nextStopName, nextStopLat, nextStopLon);
    }
}
