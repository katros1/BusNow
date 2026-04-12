package ba.backend.shared.dto;

import java.util.List;

public record PolygonUpdateDto(
        String name,
        List<List<Double>> coordinates
) {
}
