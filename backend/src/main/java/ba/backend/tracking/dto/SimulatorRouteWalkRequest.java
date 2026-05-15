package ba.backend.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Walks a bus through all stops of a route for end-to-end simulation testing.
 * Triggers stop detection, trip completion at end park, and direction toggle for next trip.
 */
public record SimulatorRouteWalkRequest(
        @NotBlank String imei,
        UUID     routeId,
        Double   speedKmh,
        Integer  passengersIn,
        Integer  passengersOut
) {}
