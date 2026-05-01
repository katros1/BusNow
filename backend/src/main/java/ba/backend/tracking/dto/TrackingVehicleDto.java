package ba.backend.tracking.dto;

import java.time.Instant;
import java.util.UUID;

public record TrackingVehicleDto(
        UUID busId,
        String plateNumber,
        String model,
        Integer capacity,
        Double latitude,
        Double longitude,
        UUID routeId,
        String routeName,
        String routeCode,
        String direction,
        UUID activeTripId,
        Instant tripStartedAt,
        Integer passengersOnBoard,
        Integer availableSeats
) {}
