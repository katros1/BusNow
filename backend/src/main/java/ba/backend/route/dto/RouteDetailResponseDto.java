package ba.backend.route.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteDetailResponseDto(
        UUID id,
        String name,
        List<List<Double>> routePath,
        RouteBusParkShapeDto startBusPark,
        RouteBusParkShapeDto endBusPark,
        List<RouteStopShapeDto> stops,
        Instant createdAt,
        Instant updatedAt
) {
}
