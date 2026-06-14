package ba.backend.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MLPredictRequest(
        String destination,

        @JsonProperty("user_latitude")
        Double userLatitude,

        @JsonProperty("user_longitude")
        Double userLongitude,

        Integer hour,

        @JsonProperty("day_of_week")
        Integer dayOfWeek
) {
    // Factory method
    public static MLPredictRequest from(RecommendationRequest request, int hour, int dayOfWeek) {
        return new MLPredictRequest(
                request.destination(),
                request.userLatitude(),
                request.userLongitude(),
                hour,
                dayOfWeek
        );
    }
}