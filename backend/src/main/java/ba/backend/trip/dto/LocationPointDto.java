package ba.backend.trip.dto;

import ba.backend.tracking.entity.VehicleLocationEntity;
import java.time.Instant;

public record LocationPointDto(
        double latitude,
        double longitude,
        Double speedKmh,
        Double headingDeg,
        Integer passengersOnBoard,
        Instant recordedAt
) {
    public static LocationPointDto from(VehicleLocationEntity e) {
        return new LocationPointDto(
                e.getLatitude(), e.getLongitude(),
                e.getSpeedKmh(), e.getHeadingDeg(),
                e.getPassengersOnBoard(), e.getRecordedAt());
    }
}
