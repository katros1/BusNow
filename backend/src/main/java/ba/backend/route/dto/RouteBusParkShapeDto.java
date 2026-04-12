package ba.backend.route.dto;

import java.util.List;
import java.util.UUID;

public record RouteBusParkShapeDto(
        UUID id,
        String name,
        List<List<Double>> coordinates
) {
}
