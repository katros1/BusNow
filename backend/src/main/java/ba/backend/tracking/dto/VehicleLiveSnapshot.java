package ba.backend.tracking.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a vehicle's live state, published to Redis and forwarded to WebSocket clients.
 * Produced by GpsIngestService on every GPS update.
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
        int     passengersOnBoard,
        Integer availableSeats,
        UUID    tripId,
        Instant timestamp
) {}
