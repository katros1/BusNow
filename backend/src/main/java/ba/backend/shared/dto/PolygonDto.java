package ba.backend.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PolygonDto(
        @NotBlank String name,
        @NotEmpty List<List<Double>> coordinates
) {
}
