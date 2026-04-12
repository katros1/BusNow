package ba.backend.routecode.dto;

import java.util.UUID;

public record RouteSummaryDto(
        UUID id,
        String name
) {
}
