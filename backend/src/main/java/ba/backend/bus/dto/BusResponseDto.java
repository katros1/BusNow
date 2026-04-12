package ba.backend.bus.dto;

import java.time.Instant;
import java.util.UUID;

public record BusResponseDto(
        UUID id,
        String plateNumber,
        String gpsImei,
        String model,
        Integer capacity,
        Double currentLatitude,
        Double currentLongitude,
        BusDriverRefDto currentDriver,
        BusRouteCodeRefDto routeCode,
        Instant createdAt,
        Instant updatedAt
) {
}
