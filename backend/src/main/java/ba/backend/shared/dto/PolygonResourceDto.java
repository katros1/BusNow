package ba.backend.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolygonResourceDto(
        UUID id,
        String name,
        List<List<Double>> coordinates,
        Instant createdAt,
        Instant updatedAt
) {
}
