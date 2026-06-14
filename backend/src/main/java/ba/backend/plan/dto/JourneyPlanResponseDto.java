package ba.backend.plan.dto;

import java.util.List;
import java.util.UUID;

public record JourneyPlanResponseDto(
        List<JourneyRouteSuggestionDto> suggestions
) {
    public record JourneyRouteSuggestionDto(
            UUID routeId,
            String routeName,
            List<List<Double>> routeCoordinates,
            JourneyRoutePointDto boardingPoint,
            JourneyRoutePointDto destinationPoint,
            Double walkToBoardingKm,           // null when user GPS position is unavailable
            double distanceToDestinationKm,    // walk from alighting stop → destination (user rides bus for the main leg)
            double totalWalkingKm,
            int walkToBoardingMinutes,
            int distanceToDestinationMinutes,
            int totalWalkingMinutes,
            String tier,
            double rideDistanceKm,
            long fareAmount,
            long requiredCardBalance
    ) {
    }

    public record JourneyRoutePointDto(
            UUID pointId,
            String pointName,
            String pointType,
            int sequence,
            List<Double> coordinates
    ) {
    }
}
