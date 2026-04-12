package ba.backend.bus.dto;

import java.util.UUID;

public record BusDriverRefDto(
        UUID id,
        String fullName
) {
}
