package ba.backend.tracking.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a vehicle's live state, published to Redis and forwarded to WebSocket clients.
 *
 * <p>All route/stop/distance fields are computed by the backend from the route geometry.
 * The ESP32 only supplies: lat, lon, speed, heading, passengers-in, passengers-out.
 */
public record VehicleLiveSnapshot(
        UUID    busId,
        String  plateNumber,
        UUID    routeId,
        String  routeCode,
        String  routeName,
        Double  latitude,
        Double  longitude,
        Double  speedKmh,
        Double  headingDeg,
        boolean gpsValid,
        boolean gpsStale,
        String  currentStopName,
        String  nextStopName,
        Double  distanceToNextStopM,   // metres along route to next stop centroid
        Double  distanceToTerminalM,   // metres along route to end terminal centroid
        Double  progressPercent,       // 0–100, how far along the route the bus is
        int     passengersOnBoard,
        Integer availableSeats,
        UUID    tripId,
        Instant tripStartedAt,
        Instant timestamp
) {}
