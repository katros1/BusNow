package ba.backend.plan.dto;

import java.util.List;
import java.util.UUID;

public record JourneyNearestStopResponseDto(
        UUID stopId,
        String stopName,
        List<Double> coordinates,
        double distanceKm
) {
}
