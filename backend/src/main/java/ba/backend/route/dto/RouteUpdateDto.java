package ba.backend.route.dto;

import java.util.List;
import java.util.UUID;

public record RouteUpdateDto(
        String name,
        List<List<Double>> coordinates,
        UUID startBusParkId,
        UUID endBusParkId
) {
}
