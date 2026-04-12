package ba.backend.route.dto;

import java.util.List;
import java.util.UUID;

public record RouteStopShapeDto(
        UUID id,
        String name,
        int sequence,
        List<List<Double>> coordinates
) {
}
