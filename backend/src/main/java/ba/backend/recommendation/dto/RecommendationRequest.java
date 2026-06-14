package ba.backend.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecommendationRequest(
        @NotBlank(message = "Destination is required")
        String destination,

        @NotNull(message = "User latitude is required")
        Double userLatitude,

        @NotNull(message = "User longitude is required")
        Double userLongitude,

        Integer hour,

        Integer dayOfWeek
) {
    // Builder-like static factory method
    public static RecommendationRequest of(String destination, Double lat, Double lng) {
        return new RecommendationRequest(destination, lat, lng, null, null);
    }
}