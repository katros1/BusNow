package ba.backend.tracking.dto;

import ba.backend.trip.entity.TripStatus;
import java.time.Instant;
import java.util.UUID;

public record VehiclePositionEvent(
        UUID busId,
        String plateNumber,
        String deviceId,
        boolean gpsValid,
        Double latitude,
        Double longitude,
        Double speedKmh,
        Double headingDeg,
        String timestamp,
        RouteInfo route,
        TripInfo trip,
        StopInfo currentStop
) {
    public record RouteInfo(
            UUID id,
            String name,
            String code,
            String direction
    ) {}

    public record TripInfo(
            UUID id,
            TripStatus status,
            Instant startedAt,
            int passengersIn,
            int passengersOut,
            int onBoard,
            Integer availableSeats
    ) {}

    /** Non-null when the bus is physically inside a stop's geofence polygon. */
    public record StopInfo(
            UUID id,
            String name,
            int sequence
    ) {}
}
