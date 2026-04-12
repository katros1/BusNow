package ba.backend.bus.dto;

import jakarta.validation.constraints.Min;
import java.util.UUID;

public record BusUpdateDto(
        String plateNumber,
        String gpsImei,
        String model,
        @Min(1) Integer capacity,
        Double currentLatitude,
        Double currentLongitude,
        UUID currentDriverId,
        UUID routeCodeId
) {
}
