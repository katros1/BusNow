package ba.backend.tracking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Single GPS push for simulator testing.
 * Maps to a VehiclePayload and is fed directly into the tracking pipeline.
 */
public record SimulatorGpsRequest(
        @NotBlank String imei,
        double  lat,
        double  lon,
        Double  speedKmh,
        Double  headingDeg,
        Integer passengersIn,
        Integer passengersOut
) {}
