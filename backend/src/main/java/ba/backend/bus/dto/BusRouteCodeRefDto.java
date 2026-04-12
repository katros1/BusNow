package ba.backend.bus.dto;

import java.util.UUID;

public record BusRouteCodeRefDto(
        UUID id,
        String code
) {
}
