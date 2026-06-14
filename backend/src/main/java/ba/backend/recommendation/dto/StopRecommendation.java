package ba.backend.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StopRecommendation(
        @JsonProperty("stop_name")
        String stopName,

        Double latitude,

        Double longitude,

        Double confidence,

        Boolean recommended,

        @JsonProperty("wait_time")
        Integer waitTime,

        @JsonProperty("bus_frequency")
        Integer busFrequency,

        Integer fare,

        @JsonProperty("distance_km")
        Double distanceKm,

        @JsonProperty("walking_time")
        Integer walkingTime
) {}