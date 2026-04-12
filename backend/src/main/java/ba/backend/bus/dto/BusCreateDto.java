package ba.backend.bus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record BusCreateDto(
        @NotBlank String plateNumber,
        @NotBlank String gpsImei,
        String model,
        @Min(1) Integer capacity,
        Double currentLatitude,
        Double currentLongitude,
        UUID currentDriverId,
        UUID routeCodeId
) {
}
