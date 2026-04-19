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
            double walkToBoardingKm,
            double walkToDestinationKm,
            double totalWalkingKm,
            String tier
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
