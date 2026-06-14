package ba.backend.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RecommendationResponse(
        Boolean success,

        String destination,

        String timestamp,

        @JsonProperty("time_context")
        String timeContext,

        List<StopRecommendation> recommendations,

        @JsonProperty("best_stop")
        StopRecommendation bestStop,

        String error
) {
    // Factory method for success response
    public static RecommendationResponse success(
            String destination,
            String timestamp,
            String timeContext,
            List<StopRecommendation> recommendations,
            StopRecommendation bestStop) {
        return new RecommendationResponse(
                true, destination, timestamp, timeContext, recommendations, bestStop, null
        );
    }

    // Factory method for error response
    public static RecommendationResponse error(String destination, String timestamp, String error) {
        return new RecommendationResponse(
                false, destination, timestamp, null, null, null, error
        );
    }
}