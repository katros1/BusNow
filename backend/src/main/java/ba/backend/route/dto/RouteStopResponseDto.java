package ba.backend.route.dto;

import java.util.UUID;

public record RouteStopResponseDto(
        UUID stopId,
        int sequence
) {
}
