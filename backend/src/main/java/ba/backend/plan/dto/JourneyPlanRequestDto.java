package ba.backend.plan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JourneyPlanRequestDto(
        @Size(min = 2, max = 2) List<@NotNull Double> currentLocation,   // null = GPS unavailable
        @NotNull @Size(min = 2, max = 2) List<@NotNull Double> destinationLocation,
        @Positive Integer maxSuggestions
) {
}
