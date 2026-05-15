package ba.backend.tracking.service;

import ba.backend.route.entity.RouteStopEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves the current and next bus stop for a given GPS coordinate along a route.
 * Current stop = the stop whose geofence polygon contains the GPS point.
 * Next stop    = lowest-sequence stop after the last passed stop.
 */
@Service
public class StopResolverService {

    private final GeofenceService geofenceService;

    public StopResolverService(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    public record StopResolution(
            UUID   currentStopId,
            String currentStopName,
            int    updatedLastPassedSequence,
            String nextStopName
    ) {
        static StopResolution none(int lastPassedSeq) {
            return new StopResolution(null, null, lastPassedSeq, null);
        }
    }

    public StopResolution resolve(List<RouteStopEntity> stops, double lat, double lon, int lastPassedSeq) {
        if (stops == null || stops.isEmpty()) return StopResolution.none(lastPassedSeq);

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

        int newLastPassed = (current != null && current.getSequence() > lastPassedSeq)
                ? current.getSequence() : lastPassedSeq;

        int effectiveSeq = newLastPassed;
        String nextStopName = stops.stream()
                .filter(rs -> rs.getSequence() > effectiveSeq)
                .min(Comparator.comparingInt(RouteStopEntity::getSequence))
                .map(rs -> rs.getStop().getName())
                .orElse(null);

        if (current != null) {
            return new StopResolution(
                    current.getStop().getId(), current.getStop().getName(),
                    newLastPassed, nextStopName);
        }
        return new StopResolution(null, null, newLastPassed, nextStopName);
    }
}
