package ba.backend.tracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BusSimulatorRequest(
        @NotBlank String imei,
        @JsonProperty("origin_lat") double originLat,
        @JsonProperty("origin_lon") double originLon,
        @JsonProperty("dest_lat")   double destLat,
        @JsonProperty("dest_lon")   double destLon,
        @Min(5) @Max(120) @JsonProperty("speed_kmh") double speedKmh,
        @Min(1) @Max(60)  @JsonProperty("interval_s") int    intervalS,
        // When true the simulator reverses direction at each terminal and keeps running,
        // simulating a full day of back-and-forth trips. Cumulative paxIn/paxOut never
        // reset (matching real beam-breaker hardware); the trip snapshot mechanism in
        // GpsIngestService resets the per-trip onBoard count automatically.
        @JsonProperty("loop") boolean loop
) {}
