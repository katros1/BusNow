package ba.backend.route.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RouteStopsAssignmentDto(
        @NotNull List<@NotNull UUID> stopIds
) {
}
