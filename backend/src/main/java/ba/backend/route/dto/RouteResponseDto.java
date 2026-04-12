package ba.backend.route.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteResponseDto(
        UUID id,
        String name,
        RouteBusParkRefDto startBusPark,
        RouteBusParkRefDto endBusPark,
        List<RouteStopResponseDto> stops,
        Instant createdAt,
        Instant updatedAt
) {
}
