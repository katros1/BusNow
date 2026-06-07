package ba.backend.tracking.service;

import ba.backend.tracking.dto.VehicleLiveSnapshot;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically checks all tracked buses and re-publishes their last snapshot
 * with {@code gpsStale=true} if no GPS fix was received within the stale threshold.
 * This ensures frontend clients are notified when a bus stops reporting.
 */
@Component
public class StalenessChecker {

    private static final Logger log = LoggerFactory.getLogger(StalenessChecker.class);

    private final GpsIngestService   ingestService;
    private final TrackingPublisher  publisher;
    private final long               staleSeconds;

    public StalenessChecker(GpsIngestService ingestService, TrackingPublisher publisher,
            @Value("${tracking.gps.stale-seconds:30}") long staleSeconds) {
        this.ingestService  = ingestService;
        this.publisher      = publisher;
        this.staleSeconds   = staleSeconds;
    }

    @Scheduled(fixedRateString = "${tracking.gps.stale-seconds:30}000")
    public void checkStaleness() {
        Instant threshold = Instant.now().minus(Duration.ofSeconds(staleSeconds));

        ingestService.getBusStates().forEach((busId, state) -> {
            if (state.lastSnapshot() == null || state.lastSeenAt() == null) return;
            if (state.lastSeenAt().isBefore(threshold) && !state.lastSnapshot().gpsStale()) {
                VehicleLiveSnapshot stale = staleVersion(state.lastSnapshot());
                // Update in-memory state so reconnecting clients also receive the stale flag
                ingestService.markStale(busId, stale);
                publisher.publish(stale);
                log.debug("GPS stale for bus {}, broadcasting stale snapshot", state.lastSnapshot().plateNumber());
            }
        });
    }

    private VehicleLiveSnapshot staleVersion(VehicleLiveSnapshot s) {
        return new VehicleLiveSnapshot(
                s.busId(), s.plateNumber(), s.routeId(), s.routeCode(), s.routeName(),
                s.latitude(), s.longitude(), s.speedKmh(), s.headingDeg(),
                s.gpsValid(), true,
                s.currentStopName(), s.nextStopName(),
                s.distanceToNextStopM(), s.distanceToTerminalM(), s.progressPercent(),
                s.passengersOnBoard(), s.availableSeats(),
                s.tripId(), s.tripStartedAt(), Instant.now());
    }
}
