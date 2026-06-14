package ba.backend.route.dto;

import java.util.List;
import java.util.UUID;

public record RouteStopShapeDto(
        UUID id,
        String name,
        int sequenceIndex,
        List<List<Double>> coordinates
) {
}
