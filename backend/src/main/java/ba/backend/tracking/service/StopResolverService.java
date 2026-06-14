package ba.backend.tracking.service;

import ba.backend.route.entity.RouteStopEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/**
 * Resolves the bus's current and next stop along a route.
 *
 * <h3>Stop-advancement algorithm</h3>
 * <p>Two independent mechanisms can advance {@code lastPassedStopSeq}; either is sufficient:
 *
 * <ol>
 *   <li><b>Route-projection</b> (accurate when bus is on route): project bus and stop onto the
 *       nearest route vertex, compare accumulated distances from the route start.  If
 *       {@code busD > stopD + OVERSHOOT_M} the bus is past the stop.</li>
 *   <li><b>Straight-line min-dist</b> (works even when bus is off-route): track the minimum
 *       haversine distance the bus has ever achieved to the current next stop.  Once the distance
 *       increases by more than {@code PASSED_INCREASE_M} beyond that minimum, the bus has passed
 *       the stop's closest-approach point — regardless of the road it took.</li>
 * </ol>
 *
 * <p>Mechanism 2 is the key fix for buses that detour via a different road: the distance
 * decreases as the bus approaches the stop on the parallel road, reaches a minimum (closest
 * approach), then increases — triggering the switch.
 */
@Service
public class StopResolverService {

    /** Route-projection: how far (m) past a stop's route position before it's "passed". */
    private static final double OVERSHOOT_M = 30.0;

    /** Min-dist: bus must have come within this radius (m) of the stop to start tracking. */
    private static final double MIN_DIST_ARM_M = 400.0;

    /**
     * Min-dist: once the bus has come within {@link #MIN_DIST_ARM_M}, it's counted as passed
     * when the distance has increased by this many metres beyond the minimum.
     */
    private static final double PASSED_INCREASE_M = 80.0;

    /** Proximity (m) to show the stop as "current stop" in the UI. */
    private static final double STOP_AT_M = 150.0;

    /** Legacy fallback radius for passed-zone detection when route geometry is missing. */
    private static final double STOP_PASSED_M = 250.0;

    private final GeofenceService geofenceService;

    public StopResolverService(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public record StopResolution(
            UUID   currentStopId,
            String currentStopName,
            int    updatedLastPassedSequence,
            /** Updated min-distance to the (possibly new) next stop.  Caller must persist this. */
            double updatedMinDistToNextStopM,
            String nextStopName,
            Double nextStopLat,
            Double nextStopLon
    ) {
        static StopResolution none(int lastPassedSeq, double minDist) {
            return new StopResolution(null, null, lastPassedSeq, minDist, null, null, null);
        }
    }

    /**
     * @param stops                 route stop list (any order)
     * @param routeGeo              route LineString (may be null — triggers proximity fallback)
     * @param lat                   current bus latitude
     * @param lon                   current bus longitude
     * @param lastPassedSeq         highest sequence already confirmed passed
     * @param minDistToNextStopM    minimum haversine distance the bus has achieved to the
     *                              current next stop so far; {@link Double#MAX_VALUE} on trip start
     *                              or immediately after a stop advance
     */
    public StopResolution resolve(List<RouteStopEntity> stops, LineString routeGeo,
                                  double lat, double lon,
                                  int lastPassedSeq, double minDistToNextStopM) {
        if (stops == null || stops.isEmpty()) return StopResolution.none(lastPassedSeq, minDistToNextStopM);

        List<RouteStopEntity> sorted = stops.stream()
                .filter(rs -> rs.getStop() != null && rs.getStop().getGeo() != null)
                .sorted(Comparator.comparingInt(RouteStopEntity::getSequence))
                .collect(Collectors.toList());

        if (sorted.isEmpty()) return StopResolution.none(lastPassedSeq, minDistToNextStopM);

        return routeGeo != null
                ? resolveWithRoute(sorted, routeGeo, lat, lon, lastPassedSeq, minDistToNextStopM)
                : resolveProximityFallback(sorted, lat, lon, lastPassedSeq, minDistToNextStopM);
    }

    // ── Primary algorithm (route geometry available) ──────────────────────────

    private StopResolution resolveWithRoute(List<RouteStopEntity> sorted, LineString routeGeo,
                                            double lat, double lon,
                                            int lastPassedSeq, double minDistToNextStopM) {
        double busD = GeoUtils.distanceFromStartM(routeGeo, lat, lon);

        int newLastPassed = lastPassedSeq;
        double newMinDist = minDistToNextStopM;

        for (RouteStopEntity rs : sorted) {
            if (rs.getSequence() <= newLastPassed) continue;

            double[] c = centroidLatLon(rs);
            if (c == null) continue;

            boolean passed = false;

            // ── Mechanism 1: route projection ─────────────────────────────────
            double stopD = GeoUtils.distanceFromStartM(routeGeo, c[0], c[1]);
            if (busD > stopD + OVERSHOOT_M) {
                passed = true;
            }

            // ── Mechanism 2: straight-line min-dist (handles off-route buses) ─
            if (!passed) {
                double straightDist = GeoUtils.haversineM(lat, lon, c[0], c[1]);
                if (straightDist < newMinDist) {
                    newMinDist = straightDist; // bus is approaching → update minimum
                }
                if (newMinDist < MIN_DIST_ARM_M && straightDist > newMinDist + PASSED_INCREASE_M) {
                    passed = true; // bus was close, has now moved away → passed
                }
            }

            if (passed) {
                newLastPassed = rs.getSequence();
                newMinDist = Double.MAX_VALUE; // reset for the next stop
            } else {
                break; // sorted → once we hit a non-passed stop, no further stops are passed
            }
        }

        RouteStopEntity current = nearbyStop(sorted, lat, lon, newLastPassed);

        final int effectiveSeq = newLastPassed;
        RouteStopEntity nextStop = sorted.stream()
                .filter(rs -> rs.getSequence() > effectiveSeq)
                .min(Comparator.comparingInt(RouteStopEntity::getSequence))
                .orElse(null);

        // If we didn't advance this tick, update minDist for the current next stop
        if (nextStop != null && newMinDist == minDistToNextStopM) {
            double[] nc = centroidLatLon(nextStop);
            if (nc != null) {
                double d = GeoUtils.haversineM(lat, lon, nc[0], nc[1]);
                if (d < newMinDist) newMinDist = d;
            }
        }

        return buildResolution(current, nextStop, newLastPassed, newMinDist);
    }

    // ── Proximity fallback (no route geometry) ────────────────────────────────

    private StopResolution resolveProximityFallback(List<RouteStopEntity> sorted,
                                                    double lat, double lon,
                                                    int lastPassedSeq, double minDistToNextStopM) {
        RouteStopEntity current = null;
        for (RouteStopEntity rs : sorted) {
            if (rs.getSequence() <= lastPassedSeq) continue;
            try {
                if (geofenceService.contains(rs.getStop().getGeo(), lat, lon)) { current = rs; break; }
            } catch (Exception ignored) {}
        }
        if (current == null) {
            for (RouteStopEntity rs : sorted) {
                if (rs.getSequence() <= lastPassedSeq) continue;
                double[] c = centroidLatLon(rs);
                if (c != null && GeoUtils.haversineM(lat, lon, c[0], c[1]) <= STOP_AT_M) { current = rs; break; }
            }
        }

        int newLastPassed = lastPassedSeq;
        double newMinDist = minDistToNextStopM;

        if (current != null && current.getSequence() > newLastPassed) {
            newLastPassed = current.getSequence();
            newMinDist = Double.MAX_VALUE;
        } else {
            // Passed-zone + min-dist
            for (int i = 0; i < sorted.size(); i++) {
                RouteStopEntity rs = sorted.get(i);
                if (rs.getSequence() <= newLastPassed) continue;
                double[] cThis = centroidLatLon(rs);
                if (cThis == null) continue;
                double distToThis = GeoUtils.haversineM(lat, lon, cThis[0], cThis[1]);

                boolean passed = false;

                // proximity passed-zone
                if (distToThis <= STOP_PASSED_M && i + 1 < sorted.size()) {
                    double[] cNext = centroidLatLon(sorted.get(i + 1));
                    if (cNext != null && GeoUtils.haversineM(lat, lon, cNext[0], cNext[1]) < distToThis) {
                        passed = true;
                    }
                }

                // min-dist
                if (!passed) {
                    if (distToThis < newMinDist) newMinDist = distToThis;
                    if (newMinDist < MIN_DIST_ARM_M && distToThis > newMinDist + PASSED_INCREASE_M) {
                        passed = true;
                    }
                }

                if (passed) {
                    newLastPassed = rs.getSequence();
                    newMinDist = Double.MAX_VALUE;
                } else {
                    break;
                }
            }
        }

        final int effectiveSeq = newLastPassed;
        RouteStopEntity nextStop = sorted.stream()
                .filter(rs -> rs.getSequence() > effectiveSeq)
                .min(Comparator.comparingInt(RouteStopEntity::getSequence))
                .orElse(null);

        if (nextStop != null && newMinDist == minDistToNextStopM) {
            double[] nc = centroidLatLon(nextStop);
            if (nc != null) { double d = GeoUtils.haversineM(lat, lon, nc[0], nc[1]); if (d < newMinDist) newMinDist = d; }
        }

        return buildResolution(current, nextStop, newLastPassed, newMinDist);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RouteStopEntity nearbyStop(List<RouteStopEntity> sorted, double lat, double lon,
                                       int newLastPassed) {
        RouteStopEntity best = null;
        double bestDist = STOP_AT_M;
        for (RouteStopEntity rs : sorted) {
            if (rs.getSequence() > newLastPassed + 1) break;
            double[] c = centroidLatLon(rs);
            if (c == null) continue;
            double d = GeoUtils.haversineM(lat, lon, c[0], c[1]);
            if (d < bestDist) { bestDist = d; best = rs; }
        }
        return best;
    }

    private StopResolution buildResolution(RouteStopEntity current, RouteStopEntity nextStop,
                                           int newLastPassed, double newMinDist) {
        String nextName = null;
        Double nextLat  = null;
        Double nextLon  = null;
        if (nextStop != null) {
            nextName = nextStop.getStop().getName();
            double[] c = centroidLatLon(nextStop);
            if (c != null) { nextLat = c[0]; nextLon = c[1]; }
        }
        if (current != null) {
            return new StopResolution(current.getStop().getId(), current.getStop().getName(),
                    newLastPassed, newMinDist, nextName, nextLat, nextLon);
        }
        return new StopResolution(null, null, newLastPassed, newMinDist, nextName, nextLat, nextLon);
    }

    private double[] centroidLatLon(RouteStopEntity rs) {
        if (rs.getStop() == null || rs.getStop().getGeo() == null) return null;
        try {
            Point centroid = rs.getStop().getGeo().getCentroid();
            double rawY = centroid.getY();
            double rawX = centroid.getX();
            if (Math.abs(rawY) > 10 && Math.abs(rawX) < 5) {
                return new double[]{rawX, rawY};
            }
            return new double[]{rawY, rawX};
        } catch (Exception ignored) {
            return null;
        }
    }
}
