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
        TripInfo trip
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
}
