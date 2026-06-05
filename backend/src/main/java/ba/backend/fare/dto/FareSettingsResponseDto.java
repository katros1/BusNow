package ba.backend.fare.dto;

import java.time.Instant;
import java.util.List;

public record FareSettingsResponseDto(
        double basePriceFrw,
        Instant updatedAt,
        List<FareTierDto> tiers
) {}
