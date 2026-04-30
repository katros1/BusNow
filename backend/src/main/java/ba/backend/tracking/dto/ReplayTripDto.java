package ba.backend.tracking.dto;

import ba.backend.trip.entity.TripStatus;
import java.time.Instant;
import java.util.UUID;

public record ReplayTripDto(
        UUID tripId,
        UUID busId,
        String plateNumber,
        String routeName,
        Long frameCount,
        Instant firstFrame,
        Instant lastFrame,
        TripStatus status
) {}
